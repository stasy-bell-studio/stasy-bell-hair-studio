"""Private, offline, fail-closed XLSX -> JSONL preparation for review before DB import.

This command cannot connect to PostgreSQL and never writes to a server.
Keep outputs outside Git and on encrypted private storage.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
from pathlib import Path

from openpyxl import load_workbook

from preflight import (
    KIND_VALUES, MAX_ROWS, clean, find_columns, inspect_xlsx,
    norm, parse_amount, parse_date,
)

FINANCE_CATEGORY_ALIASES = {
    "other": "other", "прочее": "other", "другое": "other",
    "materials": "materials", "материалы": "materials",
    "rent": "rent", "аренда": "rent",
    "taxes": "taxes", "налоги": "taxes",
    "advertising": "advertising", "реклама": "advertising",
}
ALLOWED_CLIENT_KEYS = {"name", "phone"}
ALLOWED_FINANCE_KEYS = {"occurred_at", "amount", "kind", "category", "note"}


def _sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_output(path: Path):
    if not path.is_absolute() or path.is_symlink():
        raise ValueError("Output must be absolute and not a symlink")
    repo = Path(__file__).resolve().parents[2]
    if repo in path.resolve().parents or path.resolve() == repo:
        raise ValueError("Private output must stay outside the Git repository")
    if path.exists() or Path(str(path) + ".manifest.json").exists():
        raise ValueError("Refusing to overwrite existing private export")
    if not path.parent.is_dir():
        raise ValueError("Private output directory does not exist")
    if not os.access(path.parent, os.W_OK):
        raise ValueError("Private output directory is not writable")


def _mapped_row(cells, indices, kind):
    values = []
    for cell in cells:
        values.append(cell.value)
    for index in indices.values():
        if index < len(cells) and cells[index].data_type == "f":
            raise ValueError("Formula found in a mapped data column; manual review required")

    def get(field):
        position = indices.get(field)
        return values[position] if position is not None and position < len(values) else None

    if kind == "clients":
        name = clean(get("name"))
        phone = clean(get("phone"))
        if not name or len(name) > 200 or (phone and len(phone) > 40):
            raise ValueError("Invalid client record")
        if phone and len(re.sub(r"\D", "", phone)) < 8:
            raise ValueError("Invalid phone format")
        return {"kind": "client", "name": name, "phone": phone or None}

    when = parse_date(get("occurred_at"))
    rubles = parse_amount(get("amount"))
    money_kind = KIND_VALUES.get(norm(get("kind")))
    if money_kind not in ("income", "expense"):
        raise ValueError("Unknown cash operation kind")
    category = FINANCE_CATEGORY_ALIASES.get(norm(get("category")) or "other")
    if category is None:
        raise ValueError("Unrecognised cash category; explicit mapping required")
    if money_kind == "income" and category != "other":
        # Procedure income requires an already completed linked visit;
        # generic XLSX cannot establish that association.
        raise ValueError("Income category needs a reviewed visit link")
    if money_kind == "expense" and category not in {
        "other", "materials", "rent", "taxes", "advertising",
    }:
        raise ValueError("Invalid expense category")
    note = clean(get("note"))
    if len(note) > 1000:
        raise ValueError("Overlong transaction note")
    return {
        "kind": "cash", "cash_kind": money_kind,
        "category": category, "amount_kopecks": rubles,
        "date_moscow": when.isoformat(), "note": note or None,
    }


def prepare(workbook_path, kind, output_path, sheet=None, mapping=None):
    src = Path(workbook_path)
    dst = Path(output_path)
    summary = inspect_xlsx(src, kind, sheet=sheet, mapping=mapping)
    if not summary["ready_for_mapping_review"]:
        raise ValueError("Validation has errors or duplicates; review original workbook first")
    _safe_output(dst)
    wb = load_workbook(src, read_only=True, data_only=False, keep_links=False)
    tmp = None
    manifest_tmp = None
    count = 0
    income = expense = 0
    try:
        ws = wb[summary["sheet"]]
        header = tuple(next(ws.iter_rows(
            min_row=summary["header_row"], max_row=summary["header_row"]
        )))
        indices = find_columns([cell.value for cell in header], kind, mapping or {})
        if indices is None:
            raise ValueError("Header mapping changed during preparation")
        with tempfile.NamedTemporaryFile(
            dir=dst.parent, prefix=".stasy-private-", mode="w",
            encoding="utf-8", delete=False,
        ) as output:
            tmp = Path(output.name)
            os.chmod(tmp, 0o600)
            for row in ws.iter_rows(min_row=summary["header_row"] + 1):
                if count >= MAX_ROWS:
                    raise ValueError("Exceeded permitted row count")
                if not any(clean(cell.value) for cell in row):
                    continue
                item = _mapped_row(row, indices, kind)
                output.write(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n")
                count += 1
                if item["kind"] == "cash":
                    if item["cash_kind"] == "income":
                        income += item["amount_kopecks"]
                    else:
                        expense += item["amount_kopecks"]
        if count != summary["valid_rows"] or count != summary["rows_seen"]:
            raise ValueError("Row counts diverged from initial preflight; refuse export")
        metadata = {
            "kind": kind, "rows": count, "income_kopecks": income,
            "expense_kopecks": expense, "source_sha256": _sha256(src),
            "output_sha256": _sha256(tmp), "database_modified": False,
            "transfer_ready": True,
        }
        with tempfile.NamedTemporaryFile(
            dir=dst.parent, prefix=".stasy-manifest-", mode="w",
            encoding="utf-8", delete=False,
        ) as handle:
            manifest_tmp = Path(handle.name)
            os.chmod(manifest_tmp, 0o600)
            json.dump(metadata, handle, ensure_ascii=False, indent=2)
        os.rename(tmp, dst)
        os.rename(manifest_tmp, Path(str(dst) + ".manifest.json"))
        return metadata
    finally:
        wb.close()
        if tmp is not None and tmp.exists():
            tmp.unlink()
        if manifest_tmp is not None and manifest_tmp.exists():
            manifest_tmp.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workbook")
    parser.add_argument("--kind", choices=["clients", "finances"], required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--sheet")
    parser.add_argument("--map", help="Private JSON mapping of logical columns to headers")
    args = parser.parse_args()
    mapping = None
    if args.map:
        mapping = json.loads(Path(args.map).read_text(encoding="utf-8"))
        if not isinstance(mapping, dict) or any(
            not isinstance(v, str) for v in mapping.values()
        ):
            parser.error("Mapping must be a JSON object of header names")
    try:
        result = prepare(args.workbook, args.kind, args.output, args.sheet, mapping)
    except (ValueError, OSError, KeyError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

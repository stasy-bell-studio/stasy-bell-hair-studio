"""Offline inspection of private XLSX before explicit, separately approved import.

No database access. No PII in output. Untrusted formulas are never evaluated.
Use only on a trusted local workstation, never in CI with real files.
"""
import argparse
import json
import os
import re
from collections import Counter
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

from openpyxl import load_workbook

HEADER_LIMIT = 30
MAX_ROWS = 200_000
MAX_BYTES = 60 * 1024 * 1024
FIELDS = {
    "clients": {"required": ["name"], "optional": ["phone"]},
    "finances": {"required": ["occurred_at", "amount", "kind"], "optional": ["category", "note"]},
}
DEFAULT_ALIASES = {
    "name": ["фио", "клиент", "имя клиента", "имя", "name"],
    "phone": ["телефон", "номер телефона", "тел", "phone", "мобильный"],
    "occurred_at": ["дата", "дата операции", "дата платежа", "date"],
    "amount": ["сумма", "сумма р", "сумма ₽", "сумма руб", "amount"],
    "kind": ["тип", "доход расход", "вид операции", "тип операции", "kind"],
    "category": ["категория", "category", "статья"],
    "note": ["примечание", "комментарий", "описание", "note"],
}
KIND_VALUES = {
    "доход": "income", "поступление": "income", "income": "income",
    "приход": "income", "расход": "expense", "списание": "expense", "expense": "expense",
    "трата": "expense",
}


def clean(value):
    return re.sub(r"\s+", " ", str(value or "")).strip()


def norm(value):
    return clean(value).lower().replace("ё", "е").replace(".", "").replace(":", "")


def parse_amount(value):
    if isinstance(value, (int, float, Decimal)):
        number = Decimal(str(value))
    else:
        raw = clean(value).replace(" ", "").replace("\u00a0", "").replace("₽", "")
        raw = raw.replace("руб.", "").replace("руб", "").replace(",", ".")
        if not re.fullmatch(r"[+-]?\d+(?:\.\d{1,2})?", raw):
            raise ValueError("Amount must be explicit decimal RUB amount")
        try:
            number = Decimal(raw)
        except InvalidOperation as exc:
            raise ValueError("Invalid amount") from exc
    if not number.is_finite() or number <= 0:
        raise ValueError("Amount must be positive")
    kopecks = number * 100
    if kopecks != kopecks.to_integral_value() or kopecks > 2_147_483_647:
        raise ValueError("Amount exceeds precision or supported range")
    return int(kopecks)


def parse_date(value):
    if isinstance(value, (date, datetime)):
        return value.date() if isinstance(value, datetime) else value
    raw = clean(value)
    for fmt in ("%d.%m.%Y", "%Y-%m-%d", "%d/%m/%Y"):
        try:
            return datetime.strptime(raw, fmt).date()
        except ValueError:
            pass
    raise ValueError("Date requires DD.MM.YYYY, YYYY-MM-DD or a date cell")


def find_columns(values, kind, mapping):
    cells = [norm(x) for x in values]
    matches = {}
    for name in FIELDS[kind]["required"] + FIELDS[kind]["optional"]:
        preferred = mapping.get(name)
        if preferred:
            candidates = [i for i, value in enumerate(cells) if value == norm(preferred)]
        else:
            candidates = [i for i, value in enumerate(cells)
                          if value in {norm(x) for x in DEFAULT_ALIASES[name]}]
        if len(candidates) == 1:
            matches[name] = candidates[0]
        elif len(candidates) > 1:
            return None  # Ambiguous duplicate header cannot be guessed.
    if not set(FIELDS[kind]["required"]).issubset(matches):
        return None
    if len(set(matches.values())) != len(matches):
        return None
    return matches


def inspected_row(row, columns, kind):
    def get(field):
        pos = columns.get(field)
        return row[pos] if pos is not None and pos < len(row) else None

    if kind == "clients":
        name = clean(get("name"))
        if not 1 <= len(name) <= 200:
            raise ValueError("Missing or overlong client name")
        phone = clean(get("phone"))
        if phone and (len(phone) > 40 or len(re.sub(r"\D", "", phone)) < 8):
            raise ValueError("Invalid phone format")
        return ("name_and_phone", norm(name) + "|" + re.sub(r"\D", "", phone))

    when = parse_date(get("occurred_at"))
    amount = parse_amount(get("amount"))
    transaction_kind = KIND_VALUES.get(norm(get("kind")))
    if not transaction_kind:
        raise ValueError("Unknown finance transaction kind; mapping required")
    category = clean(get("category"))
    if category and len(category) > 40:
        raise ValueError("Overlong category")
    return ("finance_row", f"{when}|{amount}|{transaction_kind}|{norm(category)}")


def inspect_xlsx(path, kind, sheet=None, mapping=None):
    source = Path(path)
    if not source.is_file() or source.suffix.lower() != ".xlsx":
        raise ValueError("Use an existing XLSX workbook")
    if source.stat().st_size > MAX_BYTES:
        raise ValueError("Workbook too large for safe preflight")
    if kind not in FIELDS:
        raise ValueError("Unsupported import kind")
    mapping = mapping or {}
    if set(mapping) - set(FIELDS[kind]["required"] + FIELDS[kind]["optional"]):
        raise ValueError("Unknown mapping keys")
    workbook = load_workbook(source, read_only=True, data_only=True, keep_links=False)
    try:
        worksheets = [workbook[sheet]] if sheet else workbook.worksheets
        choices = []
        for ws in worksheets:
            for number, row in enumerate(ws.iter_rows(values_only=True), start=1):
                if number > HEADER_LIMIT:
                    break
                columns = find_columns(row, kind, mapping)
                if columns:
                    choices.append((ws, number, columns))
        if len(choices) != 1:
            raise ValueError("Header mapping ambiguous or missing; specify --sheet and --map")
        ws, header_number, columns = choices[0]
        status = Counter()
        first_errors = Counter()
        duplicates = set()
        for number, row in enumerate(ws.iter_rows(values_only=True), start=1):
            if number <= header_number:
                continue
            if number - header_number > MAX_ROWS:
                raise ValueError("Too many data rows")
            if not any(clean(value) for value in row):
                continue
            status["rows_seen"] += 1
            try:
                keytype, key = inspected_row(row, columns, kind)
                if key in duplicates:
                    status["possible_duplicates"] += 1
                else:
                    duplicates.add(key)
                    status["valid_rows"] += 1
            except ValueError as exc:
                status["invalid_rows"] += 1
                first_errors[str(exc)] += 1
        return {
            "kind": kind, "sheet": ws.title, "header_row": header_number,
            "matched_columns": list(columns.keys()), "rows_seen": status["rows_seen"],
            "valid_rows": status["valid_rows"],
            "possible_duplicates": status["possible_duplicates"],
            "invalid_rows": status["invalid_rows"], "error_types": dict(first_errors),
            "ready_for_mapping_review": status["invalid_rows"] == 0 and
                                        status["possible_duplicates"] == 0,
            "database_modified": False,
        }
    finally:
        workbook.close()


def main():
    parser = argparse.ArgumentParser(description="Local-only XLSX validation; no DB writes")
    parser.add_argument("workbook")
    parser.add_argument("--kind", choices=sorted(FIELDS), required=True)
    parser.add_argument("--sheet", help="Explicit workbook sheet (if several are similar)")
    parser.add_argument("--map", help="Private JSON mapping of logical columns to header names")
    args = parser.parse_args()
    mapping = {}
    if args.map:
        with open(args.map, encoding="utf-8") as handle:
            mapping = json.load(handle)
        if not isinstance(mapping, dict) or not all(isinstance(x, str) for x in mapping.values()):
            parser.error("Mapping must be a JSON object of header names")
    try:
        result = inspect_xlsx(args.workbook, args.kind, args.sheet, mapping)
    except (ValueError, KeyError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not result["ready_for_mapping_review"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

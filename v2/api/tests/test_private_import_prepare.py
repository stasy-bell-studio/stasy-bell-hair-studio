"""Synthetic-only tests of private normalized XLSX export."""
import importlib.util
import json
from pathlib import Path
import sys

import pytest
from openpyxl import Workbook

MIGRATION = Path(__file__).resolve().parents[2] / "migration"
sys.path.insert(0, str(MIGRATION))
spec = importlib.util.spec_from_file_location("private_export", MIGRATION / "prepare_private_import.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def workbook(path, headings, rows):
    wb = Workbook()
    ws = wb.active
    ws.append(headings)
    for row in rows:
        ws.append(row)
    wb.save(path)
    wb.close()


def test_client_export_private_output_and_manifest(tmp_path):
    source = tmp_path / "source.xlsx"
    dest = tmp_path / "private.jsonl"
    workbook(source, ["Имя клиента", "Телефон"], [
        ["Demo Alpha", "+79990000001"], ["Demo Bravo", "+79990000002"],
    ])
    report = module.prepare(source, "clients", dest)
    assert report["rows"] == 2
    assert report["database_modified"] is False
    assert report["source_sha256"]
    assert dest.stat().st_mode & 0o077 == 0
    lines = [json.loads(line) for line in dest.read_text().splitlines()]
    assert lines[0]["name"] == "Demo Alpha"
    assert json.loads(Path(str(dest) + ".manifest.json").read_text())["rows"] == 2
    with pytest.raises(ValueError, match="overwrite"):
        module.prepare(source, "clients", dest)


def test_finance_report_never_recognizes_booking_as_received_cash(tmp_path):
    src = tmp_path / "cash.xlsx"
    dst = tmp_path / "cash.jsonl"
    workbook(src, ["Дата", "Сумма", "Тип", "Категория"],
             [["05.11.2026", "1200,50", "доход", "other"],
              ["06.11.2026", "300,00", "расход", "materials"]])
    report = module.prepare(src, "finances", dst)
    assert report["income_kopecks"] == 120050
    assert report["expense_kopecks"] == 30000
    assert report["database_modified"] is False


def test_formulas_in_mapped_cells_fail_closed(tmp_path):
    src = tmp_path / "formula.xlsx"
    dst = tmp_path / "blocked.jsonl"
    workbook(src, ["Имя клиента", "Телефон"], [
        ["=CONCATENATE(\"A\",\"B\")", "+79990000001"],
    ])
    with pytest.raises(ValueError):
        module.prepare(src, "clients", dst)
    assert not dst.exists()


def test_duplicate_clients_block_export(tmp_path):
    src = tmp_path / "dup.xlsx"
    dst = tmp_path / "dup.jsonl"
    workbook(src, ["Имя клиента", "Телефон"], [
        ["Demo A", "+79990000001"], ["Demo A", "+79990000001"],
    ])
    with pytest.raises(ValueError, match="duplicates"):
        module.prepare(src, "clients", dst)
    assert not dst.exists()

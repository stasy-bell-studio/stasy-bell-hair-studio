import importlib.util
from pathlib import Path

from openpyxl import Workbook

module_path = Path(__file__).resolve().parents[2] / "migration" / "preflight.py"
spec = importlib.util.spec_from_file_location("stasy_preflight", module_path)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


def save_xlsx(path, headers, records, offset=2):
    wb = Workbook()
    ws = wb.active
    ws.title = "Synthetic only"
    for index in range(offset - 1):
        ws.append([f"Demo header line {index + 1}"])
    ws.append(headers)
    for row in records:
        ws.append(row)
    wb.save(path)


def test_clients_preflight_without_personal_data_leak(tmp_path):
    path = tmp_path / "synthetic_clients.xlsx"
    save_xlsx(path, ["ФИО", "Телефон"],
              [["Test Ada", "+7 999 111 22 33"], ["Test Ada", "+7 999 111 22 33"],
               ["Test Ben", ""]], offset=5)
    summary = preflight.inspect_xlsx(path, "clients")
    assert summary["header_row"] == 5
    assert summary["valid_rows"] == 2
    assert summary["possible_duplicates"] == 1
    assert not summary["database_modified"]
    assert "Test Ada" not in str(summary)


def test_financial_preflight_validates_amounts_and_types(tmp_path):
    path = tmp_path / "synthetic_finances.xlsx"
    save_xlsx(path, ["Дата", "Сумма", "Тип", "Категория"],
              [["01.11.2026", "1 200,50", "доход", "other"],
               ["02.11.2026", "-15", "расход", "materials"],
               ["03.11.2026", "100.123", "приход", "other"]])
    summary = preflight.inspect_xlsx(path, "finances")
    assert summary["valid_rows"] == 1
    assert summary["invalid_rows"] == 2
    assert summary["error_types"]["Amount must be positive"] == 1
    assert summary["error_types"]["Amount must be explicit decimal RUB amount"] == 1
    assert preflight.parse_amount("1 200,50") == 120050
    assert not summary["ready_for_mapping_review"]


def test_ambiguous_workbooks_rejected(tmp_path):
    path = tmp_path / "sheets.xlsx"
    wb = Workbook()
    wb.active.append(["Имя клиента", "Телефон"])
    wb.create_sheet("Another").append(["Имя клиента", "Телефон"])
    wb.save(path)
    import pytest
    with pytest.raises(ValueError, match="ambiguous"):
        preflight.inspect_xlsx(path, "clients")
    assert preflight.inspect_xlsx(path, "clients", sheet="Sheet")["valid_rows"] == 0

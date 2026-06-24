"""
Utilidad de parseo para importaciones masivas CSV/Excel.
Soporta:
  - CSV UTF-8 (con o sin BOM)
  - XLSX (openpyxl)
"""
from __future__ import annotations
import csv
import io
from datetime import date, datetime


def parse_file(content: bytes, filename: str) -> tuple[list[str], list[list[str]]]:
    """Retorna (headers_lowercase, filas_de_strings) desde CSV o XLSX."""
    ext = (filename or "").lower()
    if ext.endswith(".xlsx") or ext.endswith(".xls"):
        return _parse_excel(content)
    return _parse_csv(content)


def _parse_csv(content: bytes) -> tuple[list[str], list[list[str]]]:
    """Parsea contenido CSV (UTF-8 con/sin BOM o latin-1) y normaliza encabezados.

    Args:
        content: Bytes crudos del archivo CSV.

    Returns:
        Tupla (headers_lowercase_snake, filas_de_strings).
    """
    try:
        text = content.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = content.decode("latin-1")
    reader = csv.reader(io.StringIO(text))
    rows = [r for r in reader if any(c.strip() for c in r)]
    if not rows:
        return [], []
    headers = [h.strip().lower().replace(" ", "_") for h in rows[0]]
    data    = [[c.strip() for c in r] for r in rows[1:]]
    return headers, data


def _parse_excel(content: bytes) -> tuple[list[str], list[list[str]]]:
    """Parsea contenido XLSX/XLS con openpyxl en modo solo lectura.

    Args:
        content: Bytes crudos del archivo Excel.

    Returns:
        Tupla (headers_lowercase_snake, filas_de_strings).

    Raises:
        RuntimeError: Si openpyxl no está instalado en el entorno.
    """
    try:
        import openpyxl
    except ImportError:
        raise RuntimeError("openpyxl no está instalado — ejecuta: pip install openpyxl")
    wb = openpyxl.load_workbook(io.BytesIO(content), read_only=True, data_only=True)
    ws = wb.active
    raw = []
    for row in ws.iter_rows():
        raw.append([str(c.value if c.value is not None else "").strip() for c in row])
    wb.close()
    rows = [r for r in raw if any(c for c in r)]
    if not rows:
        return [], []
    headers = [h.lower().replace(" ", "_") for h in rows[0]]
    return headers, rows[1:]


def get_col(row: list[str], headers: list[str], *names: str) -> str:
    """Devuelve el valor de la primera columna que coincida con alguno de los nombres."""
    for name in names:
        n = name.lower().replace(" ", "_")
        try:
            idx = headers.index(n)
            return row[idx].strip() if idx < len(row) else ""
        except ValueError:
            pass
    return ""


def parse_date(s: str) -> date | None:
    """Acepta DD/MM/YYYY, YYYY-MM-DD o DD-MM-YYYY."""
    if not s:
        return None
    for fmt in ("%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y", "%d/%m/%y"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    return None


def parse_float(s: str) -> float | None:
    """Convierte un string a float aceptando coma como separador decimal.

    Args:
        s: String con el número (p. ej. ``"8,5"`` o ``"8.5"``).

    Returns:
        Valor float, o None si el string está vacío o no es convertible.
    """
    try:
        return float(s.replace(",", ".")) if s else None
    except ValueError:
        return None


def parse_int(s: str) -> int | None:
    """Convierte un string a entero.

    Args:
        s: String con el número entero.

    Returns:
        Valor int, o None si el string está vacío o no es convertible.
    """
    try:
        return int(s) if s else None
    except ValueError:
        return None

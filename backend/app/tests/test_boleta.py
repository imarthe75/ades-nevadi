"""
Tests de boleta NEM y constancia UAEMEX.

Verifican que el generador de PDF produce un archivo válido
cuando la base de datos tiene datos completos. Si la DB no
está disponible, los tests se marcan como skipped.
"""
from __future__ import annotations

import uuid
import pytest
from unittest.mock import patch, MagicMock
from pathlib import Path

# ── Fixtures ────────────────────────────────────────────────────────────────

TEMPLATES_DIR = Path(__file__).parent.parent / "templates" / "boletas"

MOCK_CTX_NEM = {
    "plantel_nombre":    "Metepec",
    "cct":               "15DPR0001A",
    "ciclo_nombre":      "2025-2026",
    "fecha_generacion":  "01/07/2026",
    "nombre_completo":   "GARCÍA LÓPEZ JUAN",
    "curp":              "GALJ100101HMCRPN09",
    "matricula":         "25001",
    "grado_grupo":       "3° — Grupo A",
    "nivel_educativo":   "PRIMARIA",
    "periodos":          ["1er Bimestre", "2do Bimestre", "3er Bimestre", "4to Bimestre", "5to Bimestre"],
    "materias": [
        {
            "materia_nombre":  "Español",
            "campo_formativo": "LENGUAJES",
            "calificaciones":  {"1er Bimestre": 8.5, "2do Bimestre": 9.0, "3er Bimestre": 8.0,
                                "4to Bimestre": 9.5, "5to Bimestre": 8.8},
            "promedio": 8.8,
            "acreditado": True,
        },
        {
            "materia_nombre":  "Matemáticas",
            "campo_formativo": "SABERES_PENSAMIENTO_CIENTIFICO",
            "calificaciones":  {"1er Bimestre": 7.5, "2do Bimestre": 8.0, "3er Bimestre": 7.0,
                                "4to Bimestre": 8.5, "5to Bimestre": 7.8},
            "promedio": 7.8,
            "acreditado": True,
        },
    ],
    "es_nem":            True,
    "campos": [
        {"campo": "Lenguajes",
         "materias": [{"materia_nombre": "Español", "campo_formativo": "LENGUAJES",
                       "calificaciones": {"1er Bimestre": 8.5}, "promedio": 8.8, "acreditado": True}]},
    ],
    "promedio_general":  8.3,
    "acredito_grado":    True,
    "faltas":            3,
    "faltas_justificadas": 1,
    "observaciones":     "Buen desempeño.",
    "plantel_direccion": "Prol. Heriberto Enríquez 1001",
    "plantel_telefono":  "722-297-1441",
}

MOCK_CTX_UAEMEX = {
    "plantel_nombre":       "Metepec",
    "cct":                  "15EMH0001A",
    "plantel_dir":          "Prol. Heriberto Enríquez 1001",
    "plantel_tel":          "722-297-1441",
    "ciclo_nombre":         "2025-2026",
    "fecha_generacion":     "01/07/2026",
    "nombre_completo":      "RAMÍREZ PÉREZ LUCÍA",
    "curp":                 "RAPL010101MMCMRCA5",
    "matricula":            "25100",
    "semestre":             "Primer Semestre",
    "grupo":                "Grupo A",
    "materias": [
        {"materia": "Álgebra", "clave": "MAT-01",
         "ordinario": "8.0", "extraordinario": "—", "definitiva": "8.0",
         "acreditada": True, "inasistencias": 2},
        {"materia": "Taller de Lectura y Redacción", "clave": "ESP-01",
         "ordinario": "7.5", "extraordinario": "—", "definitiva": "7.5",
         "acreditada": True, "inasistencias": 0},
        {"materia": "Historia de México", "clave": "HIS-01",
         "ordinario": "5.5", "extraordinario": "6.5", "definitiva": "6.5",
         "acreditada": True, "inasistencias": 4},
    ],
    "promedio_general":        "7.3",
    "materias_acreditadas":    3,
    "total_materias":          3,
    "acredito_grado":          True,
    "escala":                  "Escala 0 – 10 | Mínima aprobatoria: 6.0 (RGEMS UAEMEX)",
}


# ── Tests ────────────────────────────────────────────────────────────────────

def test_boleta_nem_template_exists():
    """El template NEM debe existir."""
    assert (TEMPLATES_DIR / "boleta.html").is_file(), "boleta.html no encontrado"


def test_boleta_uaemex_template_exists():
    """El template UAEMEX debe existir."""
    assert (TEMPLATES_DIR / "boleta_uaemex.html").is_file(), "boleta_uaemex.html no encontrado"


def test_boleta_nem_renders_pdf():
    """El template NEM + weasyprint producen un PDF válido (%PDF-)."""
    try:
        from jinja2 import Environment, FileSystemLoader
        from weasyprint import HTML
    except ImportError:
        pytest.skip("jinja2/weasyprint no disponibles")

    jenv = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jenv.get_template("boleta.html").render(**MOCK_CTX_NEM)
    pdf = HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()

    assert pdf is not None, "PDF es None"
    assert pdf[:5] == b"%PDF-", f"No empieza con %PDF-: {pdf[:10]!r}"
    assert len(pdf) > 5_000, f"PDF demasiado pequeño ({len(pdf)} bytes)"


def test_boleta_nem_contains_curp():
    """El HTML de la boleta NEM contiene el CURP del alumno."""
    try:
        from jinja2 import Environment, FileSystemLoader
    except ImportError:
        pytest.skip("jinja2 no disponible")

    jenv = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jenv.get_template("boleta.html").render(**MOCK_CTX_NEM)
    assert "GALJ100101HMCRPN09" in html_str


def test_boleta_nem_contains_campos_nem():
    """El HTML de la boleta NEM incluye las etiquetas de campo formativo."""
    try:
        from jinja2 import Environment, FileSystemLoader
    except ImportError:
        pytest.skip("jinja2 no disponible")

    jenv = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jenv.get_template("boleta.html").render(**MOCK_CTX_NEM)
    assert "Lenguajes" in html_str


def test_boleta_uaemex_renders_pdf():
    """El template UAEMEX + weasyprint producen un PDF válido (%PDF-)."""
    try:
        from jinja2 import Environment, FileSystemLoader
        from weasyprint import HTML
    except ImportError:
        pytest.skip("jinja2/weasyprint no disponibles")

    jenv = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jenv.get_template("boleta_uaemex.html").render(**MOCK_CTX_UAEMEX)
    pdf = HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()

    assert pdf is not None, "PDF UAEMEX es None"
    assert pdf[:5] == b"%PDF-", f"No empieza con %PDF-: {pdf[:10]!r}"
    assert len(pdf) > 5_000, f"PDF UAEMEX demasiado pequeño ({len(pdf)} bytes)"


def test_boleta_uaemex_contains_escala():
    """El HTML UAEMEX incluye la escala RGEMS."""
    try:
        from jinja2 import Environment, FileSystemLoader
    except ImportError:
        pytest.skip("jinja2 no disponible")

    jenv = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jenv.get_template("boleta_uaemex.html").render(**MOCK_CTX_UAEMEX)
    assert "RGEMS UAEMEX" in html_str
    assert "RAPL010101MMCMRCA5" in html_str

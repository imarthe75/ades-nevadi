"""
FASE 21 — Stirling-PDF: herramientas de procesamiento PDF.

Endpoints:
  GET  /pdf/status                       — estado del servicio
  POST /pdf/fusionar                     — combina múltiples PDFs en uno
  POST /pdf/marca-agua                   — añade marca de agua a PDF
  POST /pdf/comprimir                    — comprime PDF
  POST /pdf/boletas-grupo/{grupo_id}     — genera y fusiona boletas de todo un grupo

Integra con Carbone (genera PDFs) y Stirling-PDF (procesa los PDFs generados).
"""

from __future__ import annotations

import asyncio
import io
import logging
import uuid
from datetime import date
from jinja2 import Environment, FileSystemLoader
from pathlib import Path

import httpx
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text
from sqlalchemy.orm import selectinload

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.models.personas import Estudiante, Inscripcion

log = logging.getLogger(__name__)
router = APIRouter(prefix="/pdf", tags=["pdf"])

_TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "actas"

def _get_jinja():
    if not _TEMPLATES_DIR.exists():
        _TEMPLATES_DIR.mkdir(parents=True, exist_ok=True)
    return Environment(loader=FileSystemLoader(str(_TEMPLATES_DIR)), autoescape=True)

async def _obtener_datos_acta(ciclo_id: uuid.UUID, db: AsyncSession):
    res = await db.execute(
        text("SELECT * FROM v_indicadores_cierre_ciclo WHERE ciclo_escolar_id = :cid"),
        {"cid": ciclo_id}
    )
    row = res.mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="No se encontraron datos para el ciclo escolar especificado.")
    
    res_inst = await db.execute(
        text("SELECT valor FROM ades_parametros_sistema WHERE clave = 'NOMBRE_INSTITUCION'")
    )
    institucion = res_inst.scalar_one_or_none() or "Instituto Nevadi"

    return dict(row), institucion

_STIRLING_BASE = None


def _stirling_url() -> str:
    return settings.STIRLING_PDF_URL


async def _stirling_available() -> bool:
    try:
        async with httpx.AsyncClient(timeout=5) as c:
            r = await c.get(f"{_stirling_url()}/api/v1/info/status")
            return r.status_code == 200
    except Exception:
        return False


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("/status")
async def pdf_status():
    disponible = await _stirling_available()
    return {"disponible": disponible, "url": settings.STIRLING_PDF_URL}


@router.post("/fusionar")
async def fusionar_pdfs(
    pdfs: list[UploadFile] = File(..., description="PDFs a fusionar (mínimo 2)"),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Fusiona múltiples archivos PDF en uno solo."""
    if len(pdfs) < 2:
        raise HTTPException(400, "Se requieren al menos 2 PDFs para fusionar")

    if not await _stirling_available():
        raise HTTPException(503, "Servicio Stirling-PDF no disponible")

    files = []
    for pdf in pdfs:
        content = await pdf.read()
        files.append(("fileInput", (pdf.filename, content, "application/pdf")))

    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{_stirling_url()}/api/v1/general/merge-pdfs",
            files=files,
        )
        if resp.status_code != 200:
            raise HTTPException(502, f"Stirling error: {resp.text[:200]}")

    return StreamingResponse(
        iter([resp.content]),
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=fusion.pdf"},
    )


@router.post("/marca-agua")
async def marca_agua(
    pdf: UploadFile = File(...),
    texto: str = Form("INSTITUTO NEVADI — COPIA"),
    rotacion: int = Form(30),
    opacidad: float = Form(0.3),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Añade una marca de agua de texto a un PDF."""
    if not await _stirling_available():
        raise HTTPException(503, "Servicio Stirling-PDF no disponible")

    content = await pdf.read()
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{_stirling_url()}/api/v1/stamp/add-text-stamp",
            files={"fileInput": (pdf.filename, content, "application/pdf")},
            data={
                "text":     texto,
                "fontSize": "28",
                "rotation": str(rotacion),
                "opacity":  str(opacidad),
                "color":    "#888888",
                "alphabet":  "latin",
            },
        )
        if resp.status_code != 200:
            raise HTTPException(502, f"Stirling error: {resp.text[:200]}")

    return StreamingResponse(
        iter([resp.content]),
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=watermark_{pdf.filename}"},
    )


@router.post("/comprimir")
async def comprimir_pdf(
    pdf: UploadFile = File(...),
    nivel: int = Form(3, ge=1, le=5, description="Nivel 1=mínimo, 5=máximo"),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Comprime un PDF reduciendo su tamaño."""
    if not await _stirling_available():
        raise HTTPException(503, "Servicio Stirling-PDF no disponible")

    content = await pdf.read()
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(
            f"{_stirling_url()}/api/v1/general/compress-pdf",
            files={"fileInput": (pdf.filename, content, "application/pdf")},
            data={"optimizeLevel": str(nivel)},
        )
        if resp.status_code != 200:
            raise HTTPException(502, f"Stirling error: {resp.text[:200]}")

    return StreamingResponse(
        iter([resp.content]),
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=compressed_{pdf.filename}"},
    )


@router.post("/boletas-grupo/{grupo_id}")
async def boletas_grupo_fusionadas(
    grupo_id: uuid.UUID,
    template_id: str,
    periodo: int | None = None,
    agregar_marca_agua: bool = False,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Genera la boleta de cada alumno del grupo (vía Carbone)
    y las fusiona en un único PDF (vía Stirling-PDF).
    Solo admin y coordinadores.
    """
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Solo coordinadores o superiores")

    if not settings.CARBONE_URL:
        raise HTTPException(503, "Carbone no configurado")

    # Obtener alumnos del grupo
    q = (
        select(Inscripcion)
        .where(Inscripcion.grupo_id == grupo_id, Inscripcion.is_active == True)
        .options(selectinload(Inscripcion.estudiante).selectinload(Estudiante.persona))
    )
    inscripciones = (await db.execute(q)).scalars().all()
    if not inscripciones:
        raise HTTPException(404, "No hay alumnos inscritos en este grupo")

    # Generar boleta por alumno (paralelo, máx 10 a la vez)
    semaphore = asyncio.Semaphore(10)

    async def _gen_boleta(est_id: uuid.UUID) -> bytes | None:
        async with semaphore:
            params = f"template_id={template_id}"
            if periodo:
                params += f"&periodo={periodo}"
            try:
                async with httpx.AsyncClient(timeout=30) as c:
                    r = await c.post(
                        f"{settings.CARBONE_URL}/render/{template_id}",
                        json={"data": {"estudiante_id": str(est_id)}},
                    )
                    return r.content if r.status_code == 200 else None
            except Exception:
                return None

    tasks = [_gen_boleta(ins.estudiante_id) for ins in inscripciones]
    resultados = await asyncio.gather(*tasks)
    pdfs = [r for r in resultados if r]

    if not pdfs:
        raise HTTPException(502, "No se generaron boletas. Verificar Carbone.")

    if len(pdfs) == 1:
        return StreamingResponse(
            iter([pdfs[0]]),
            media_type="application/pdf",
            headers={"Content-Disposition": "attachment; filename=boletas_grupo.pdf"},
        )

    # Fusionar con Stirling-PDF
    if not await _stirling_available():
        # Sin Stirling, devolver zip con PDFs individuales
        import zipfile
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
            for i, pdf in enumerate(pdfs):
                nombre = inscripciones[i].estudiante.persona.apellido_paterno if i < len(inscripciones) else str(i)
                zf.writestr(f"boleta_{nombre}.pdf", pdf)
        buf.seek(0)
        return StreamingResponse(
            buf,
            media_type="application/zip",
            headers={"Content-Disposition": "attachment; filename=boletas_grupo.zip"},
        )

    files = [("fileInput", (f"boleta_{i}.pdf", pdf, "application/pdf")) for i, pdf in enumerate(pdfs)]
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{_stirling_url()}/api/v1/general/merge-pdfs", files=files)
        if resp.status_code != 200:
            raise HTTPException(502, f"Stirling merge error: {resp.text[:200]}")

    result_pdf = resp.content

    if agregar_marca_agua:
        async with httpx.AsyncClient(timeout=60) as client:
            resp2 = await client.post(
                f"{_stirling_url()}/api/v1/stamp/add-text-stamp",
                files={"fileInput": ("boletas.pdf", result_pdf, "application/pdf")},
                data={"text": "INSTITUTO NEVADI", "rotation": "30", "opacity": "0.15", "fontSize": "32"},
            )
            if resp2.status_code == 200:
                result_pdf = resp2.content

    return StreamingResponse(
        iter([result_pdf]),
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=boletas_grupo_{grupo_id}.pdf"},
    )


@router.post("/{ciclo_id}/acta-inicio")
async def acta_inicio(
    ciclo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Genera el PDF WeasyPrint del acta de inicio de ciclo escolar."""
    if ades_user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Acceso denegado.")

    datos, institucion = await _obtener_datos_acta(ciclo_id, db)
    
    ctx = {
        "institucion": institucion,
        "nombre_ciclo": datos["nombre_ciclo"],
        "nombre_nivel": datos["nombre_nivel"],
        "fecha_inicio": datos["fecha_inicio"].strftime("%d/%m/%Y"),
        "matricula_total": datos["matricula_total"],
        "total_docentes": datos["total_docentes"],
        "fecha_generacion": date.today().strftime("%d/%m/%Y"),
        "usuario_generacion": ades_user.nombre_usuario,
    }

    template = _get_jinja().get_template("acta_inicio.html")
    html_str = template.render(**ctx)

    try:
        from weasyprint import HTML
        pdf_bytes = HTML(string=html_str, base_url=str(_TEMPLATES_DIR)).write_pdf()
    except ImportError:
        raise HTTPException(status_code=503, detail="WeasyPrint no disponible en el servidor.")

    filename = f"acta_inicio_{datos['nombre_ciclo']}_{datos['nombre_nivel']}.pdf"
    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@router.post("/{ciclo_id}/acta-cierre")
async def acta_cierre(
    ciclo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Genera el PDF WeasyPrint del acta de cierre de ciclo escolar."""
    if ades_user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Acceso denegado.")

    datos, institucion = await _obtener_datos_acta(ciclo_id, db)
    
    ctx = {
        "institucion": institucion,
        "nombre_ciclo": datos["nombre_ciclo"],
        "nombre_nivel": datos["nombre_nivel"],
        "fecha_fin": datos["fecha_fin"].strftime("%d/%m/%Y"),
        "matricula_total": datos["matricula_total"],
        "promedio_general": datos["promedio_general"],
        "tasa_aprobacion": datos["tasa_aprobacion"],
        "total_bajas": datos["total_bajas"],
        "total_alumnos_activos": datos["total_alumnos_activos"],
        "fecha_generacion": date.today().strftime("%d/%m/%Y"),
        "usuario_generacion": ades_user.nombre_usuario,
    }

    template = _get_jinja().get_template("acta_cierre.html")
    html_str = template.render(**ctx)

    try:
        from weasyprint import HTML
        pdf_bytes = HTML(string=html_str, base_url=str(_TEMPLATES_DIR)).write_pdf()
    except ImportError:
        raise HTTPException(status_code=503, detail="WeasyPrint no disponible en el servidor.")

    filename = f"acta_cierre_{datos['nombre_ciclo']}_{datos['nombre_nivel']}.pdf"
    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )

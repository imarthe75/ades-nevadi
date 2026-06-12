"""
api/v1/expediente.py
====================
FASE 28 — Gestion Documental y Expediente Digital

Endpoints para gestionar el expediente digital del alumno, integrado con
Paperless-ngx como motor OCR. Los documentos se suben via multipart,
Paperless los procesa con tesseract-spa y el texto OCR queda buscable.

  GET  /expediente/alumno/{estudiante_id}                     — expediente activo con docs
  POST /expediente/alumno/{estudiante_id}/documentos          — subir documento (multipart)
  GET  /expediente/alumno/{estudiante_id}/documentos/{doc_id}/preview — proxy PDF/imagen
  DELETE /expediente/{expediente_id}/documentos/{doc_id}      — eliminar documento
  GET  /expediente/buscar                                     — busqueda fulltext via Paperless
  POST /expediente/{expediente_id}/verificar                  — verificar expediente (Director)
  POST /expediente/alumno/{estudiante_id}/analizar-ia         — analisis IA con NVIDIA NIM
"""
from __future__ import annotations

import json
import logging
import re
from uuid import UUID

import httpx
from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile, status
from fastapi.responses import Response
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.services import paperless as paperless_svc

log = logging.getLogger(__name__)

router = APIRouter(prefix="/expediente", tags=["expediente"])

_NIVEL_DIRECTOR = 2
_NIVEL_COORD = 3

# Tipos de documento requeridos (base comun a todos los niveles)
_TIPOS_REQUERIDOS = [
    "CURP",
    "ACTA_NACIMIENTO",
    "CERTIFICADO_PREV",
    "COMPROBANTE_DOMICILIO",
    "FOTOGRAFIA",
]

_LABEL_TIPO: dict[str, str] = {
    "CURP":                   "CURP",
    "ACTA_NACIMIENTO":        "Acta de Nacimiento",
    "CERTIFICADO_PREV":       "Certificado de Nivel Previo",
    "COMPROBANTE_DOMICILIO":  "Comprobante de Domicilio",
    "FOTOGRAFIA":             "Fotografía",
    "NSS":                    "Número de Seguro Social",
    "CREDENCIAL_ESCOLAR":     "Credencial Escolar",
    "CONSTANCIA_INSCRIPCION": "Constancia de Inscripción",
    "OTRO":                   "Otro",
}


# ===========================================================================
# Schemas
# ===========================================================================
class DocumentoOut(BaseModel):
    id: UUID
    expediente_id: UUID
    paperless_doc_id: int | None = None
    tipo_documento: str
    tipo_label: str
    nombre_archivo: str | None = None
    estado_ocr: str
    fecha_carga: str
    metadatos_ia: dict | None = None


class ExpedienteOut(BaseModel):
    id: UUID
    estudiante_id: UUID
    ciclo_escolar_id: UUID
    estado: str
    completitud_pct: float
    revisado_por: UUID | None = None
    fecha_revision: str | None = None
    observaciones: str | None = None
    documentos: list[DocumentoOut]
    documentos_requeridos: list[dict]


class SubirDocumentoResponse(BaseModel):
    id: UUID
    paperless_task_id: str | None = None
    mensaje: str


class VerificarResponse(BaseModel):
    expediente_id: UUID
    estado: str
    mensaje: str


class AnalisisIAResponse(BaseModel):
    expediente_id: UUID
    completitud_pct: float
    documentos_presentes: list[str]
    documentos_faltantes: list[str]
    analisis: str
    recomendaciones: list[str]
    alertas: list[str]


# ===========================================================================
# Helpers
# ===========================================================================
async def _ciclo_activo_id(db: AsyncSession) -> UUID:
    row = await db.execute(
        text("SELECT id FROM public.ades_ciclos_escolares WHERE activo = TRUE ORDER BY fecha_inicio DESC LIMIT 1")
    )
    ciclo = row.scalar_one_or_none()
    if not ciclo:
        raise HTTPException(status_code=400, detail="No hay ciclo escolar activo configurado.")
    return ciclo


async def _obtener_o_crear_expediente(
    db: AsyncSession,
    estudiante_id: UUID,
    ciclo_id: UUID,
) -> dict:
    """Obtiene el expediente del ciclo indicado o lo crea si no existe."""
    row = await db.execute(
        text("""
            SELECT id, estado, completitud_pct, revisado_por, fecha_revision,
                   observaciones, ciclo_escolar_id
            FROM public.ades_expedientes_alumno
            WHERE estudiante_id = :est_id AND ciclo_escolar_id = :ciclo_id
        """),
        {"est_id": str(estudiante_id), "ciclo_id": str(ciclo_id)},
    )
    exp = row.mappings().first()
    if exp:
        return dict(exp)

    result = await db.execute(
        text("""
            INSERT INTO public.ades_expedientes_alumno
                (estudiante_id, ciclo_escolar_id, estado, completitud_pct)
            VALUES (:est_id, :ciclo_id, 'PENDIENTE', 0.00)
            RETURNING id, estado, completitud_pct, revisado_por, fecha_revision,
                      observaciones, ciclo_escolar_id
        """),
        {"est_id": str(estudiante_id), "ciclo_id": str(ciclo_id)},
    )
    await db.commit()
    return dict(result.mappings().first())


# ===========================================================================
# GET /expediente/alumno/{estudiante_id}
# ===========================================================================
@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def obtener_expediente(
    estudiante_id: UUID,
    ciclo_id: UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Obtiene (o crea) el expediente digital del alumno para el ciclo indicado o activo."""
    if not ciclo_id:
        ciclo_id = await _ciclo_activo_id(db)

    exp = await _obtener_o_crear_expediente(db, estudiante_id, ciclo_id)
    exp_id = exp["id"]

    docs_result = await db.execute(
        text("""
            SELECT id, expediente_id, paperless_doc_id, tipo_documento,
                   nombre_archivo, estado_ocr, fecha_carga, metadatos_ia
            FROM public.ades_expediente_documentos
            WHERE expediente_id = :exp_id
            ORDER BY fecha_carga DESC
        """),
        {"exp_id": str(exp_id)},
    )
    docs_raw = docs_result.mappings().all()

    documentos = [
        DocumentoOut(
            id=d["id"],
            expediente_id=d["expediente_id"],
            paperless_doc_id=d["paperless_doc_id"],
            tipo_documento=d["tipo_documento"],
            tipo_label=_LABEL_TIPO.get(d["tipo_documento"], d["tipo_documento"]),
            nombre_archivo=d["nombre_archivo"],
            estado_ocr=d["estado_ocr"],
            fecha_carga=str(d["fecha_carga"]),
            metadatos_ia=d["metadatos_ia"],
        )
        for d in docs_raw
    ]

    tipos_presentes = {d.tipo_documento for d in documentos}
    docs_requeridos = [
        {"tipo": t, "label": _LABEL_TIPO.get(t, t), "presente": t in tipos_presentes}
        for t in _TIPOS_REQUERIDOS
    ]

    return ExpedienteOut(
        id=exp["id"],
        estudiante_id=estudiante_id,
        ciclo_escolar_id=exp["ciclo_escolar_id"],
        estado=exp["estado"],
        completitud_pct=float(exp["completitud_pct"]),
        revisado_por=exp["revisado_por"],
        fecha_revision=str(exp["fecha_revision"]) if exp["fecha_revision"] else None,
        observaciones=exp["observaciones"],
        documentos=documentos,
        documentos_requeridos=docs_requeridos,
    )


# ===========================================================================
# POST /expediente/alumno/{estudiante_id}/documentos
# ===========================================================================
@router.post("/alumno/{estudiante_id}/documentos", response_model=SubirDocumentoResponse, status_code=201)
async def subir_documento(
    estudiante_id: UUID,
    archivo: UploadFile = File(...),
    tipo_documento: str = Form("OTRO"),
    ciclo_id: UUID | None = Form(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Sube un documento al expediente. El archivo se envía a Paperless-ngx para OCR async."""
    if tipo_documento not in _LABEL_TIPO:
        raise HTTPException(status_code=400, detail=f"tipo_documento invalido: {tipo_documento}")

    contenido = await archivo.read()
    if len(contenido) > 20 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Archivo demasiado grande (max 20 MB).")

    tipo_mime = archivo.content_type or "application/octet-stream"
    _MIME_PERMITIDOS = {"application/pdf", "image/jpeg", "image/png", "image/tiff", "image/webp"}
    if tipo_mime not in _MIME_PERMITIDOS:
        raise HTTPException(status_code=415, detail=f"MIME no permitido: {tipo_mime}.")

    if not ciclo_id:
        ciclo_id = await _ciclo_activo_id(db)

    exp = await _obtener_o_crear_expediente(db, estudiante_id, ciclo_id)
    exp_id = exp["id"]

    paperless_task_id = None
    if settings.PAPERLESS_API_TOKEN:
        titulo = f"{_LABEL_TIPO.get(tipo_documento, tipo_documento)} — {estudiante_id}"
        paperless_task_id = await paperless_svc.subir_documento(
            nombre=archivo.filename or "documento.pdf",
            contenido=contenido,
            tipo_mime=tipo_mime,
            titulo=titulo,
        )

    insert_result = await db.execute(
        text("""
            INSERT INTO public.ades_expediente_documentos
                (expediente_id, paperless_doc_id, tipo_documento, nombre_archivo,
                 estado_ocr, cargado_por, fecha_carga)
            VALUES (:exp_id, NULL, :tipo, :nombre, 'PENDIENTE', :usuario, NOW())
            RETURNING id
        """),
        {
            "exp_id": str(exp_id),
            "tipo": tipo_documento,
            "nombre": archivo.filename,
            "usuario": str(ades_user.id) if ades_user.id else None,
        },
    )
    nuevo_id = insert_result.scalar_one()
    await db.commit()

    mensaje = "Documento registrado. "
    mensaje += f"OCR Paperless en cola (tarea: {paperless_task_id})." if paperless_task_id else "Paperless no configurado; OCR pendiente."

    log.info("expediente.subir: exp=%s doc=%s tipo=%s", exp_id, nuevo_id, tipo_documento)
    return SubirDocumentoResponse(id=nuevo_id, paperless_task_id=paperless_task_id, mensaje=mensaje)


# ===========================================================================
# GET /expediente/alumno/{estudiante_id}/documentos/{doc_id}/preview
# ===========================================================================
@router.get("/alumno/{estudiante_id}/documentos/{doc_id}/preview")
async def preview_documento(
    estudiante_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Proxy del PDF/imagen desde Paperless-ngx."""
    row = await db.execute(
        text("SELECT paperless_doc_id, nombre_archivo FROM public.ades_expediente_documentos WHERE id = :id"),
        {"id": str(doc_id)},
    )
    doc = row.mappings().first()
    if not doc:
        raise HTTPException(status_code=404, detail="Documento no encontrado.")
    if not doc["paperless_doc_id"]:
        raise HTTPException(status_code=404, detail="Documento aun no procesado por Paperless.")

    contenido = await paperless_svc.descargar_documento(doc["paperless_doc_id"])
    if not contenido:
        raise HTTPException(status_code=502, detail="No se pudo obtener el documento de Paperless.")

    media_type = "application/pdf"
    if doc["nombre_archivo"]:
        n = doc["nombre_archivo"].lower()
        if n.endswith((".jpg", ".jpeg")):
            media_type = "image/jpeg"
        elif n.endswith(".png"):
            media_type = "image/png"

    return Response(content=contenido, media_type=media_type)


# ===========================================================================
# DELETE /expediente/{expediente_id}/documentos/{doc_id}
# ===========================================================================
@router.delete("/{expediente_id}/documentos/{doc_id}", status_code=204)
async def eliminar_documento(
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Elimina un documento del expediente (y de Paperless si existe)."""
    row = await db.execute(
        text("SELECT paperless_doc_id FROM public.ades_expediente_documentos WHERE id=:id AND expediente_id=:exp_id"),
        {"id": str(doc_id), "exp_id": str(expediente_id)},
    )
    doc = row.mappings().first()
    if not doc:
        raise HTTPException(status_code=404, detail="Documento no encontrado.")

    if doc["paperless_doc_id"]:
        await paperless_svc.eliminar_documento(doc["paperless_doc_id"])

    await db.execute(
        text("DELETE FROM public.ades_expediente_documentos WHERE id=:id"),
        {"id": str(doc_id)},
    )
    await db.commit()


# ===========================================================================
# GET /expediente/buscar
# ===========================================================================
@router.get("/buscar")
async def buscar_documentos(
    q: str = Query(""),
    page: int = Query(1, ge=1),
    page_size: int = Query(25, ge=1, le=100),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Busqueda full-text en Paperless-ngx."""
    if not settings.PAPERLESS_API_TOKEN:
        raise HTTPException(status_code=503, detail="Paperless-ngx no configurado.")
    return await paperless_svc.buscar_documentos(query=q, page=page, page_size=page_size)


# ===========================================================================
# POST /expediente/{expediente_id}/verificar
# ===========================================================================
@router.post("/{expediente_id}/verificar", response_model=VerificarResponse)
async def verificar_expediente(
    expediente_id: UUID,
    observaciones: str | None = None,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Marca el expediente como VERIFICADO. Solo Director (nivel_acceso <= 2)."""
    if ades_user.nivel_acceso > _NIVEL_DIRECTOR:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Solo el Director puede verificar expedientes.")

    result = await db.execute(
        text("""
            UPDATE public.ades_expedientes_alumno
            SET estado='VERIFICADO', revisado_por=:uid, fecha_revision=NOW(),
                observaciones=COALESCE(:obs, observaciones)
            WHERE id=:exp_id
            RETURNING id, estado
        """),
        {"exp_id": str(expediente_id), "uid": str(ades_user.id) if ades_user.id else None, "obs": observaciones},
    )
    updated = result.mappings().first()
    if not updated:
        raise HTTPException(status_code=404, detail="Expediente no encontrado.")
    await db.commit()

    log.info("expediente.verificar: exp=%s por=%s", expediente_id, ades_user.id)
    return VerificarResponse(expediente_id=expediente_id, estado="VERIFICADO", mensaje="Expediente verificado correctamente.")


# ===========================================================================
# POST /expediente/alumno/{estudiante_id}/analizar-ia
# ===========================================================================
@router.post("/alumno/{estudiante_id}/analizar-ia", response_model=AnalisisIAResponse)
async def analizar_expediente_ia(
    estudiante_id: UUID,
    ciclo_id: UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Analiza el expediente con NVIDIA NIM (meta/llama-3.1-70b-instruct). Detecta faltantes e inconsistencias."""
    if not ciclo_id:
        ciclo_id = await _ciclo_activo_id(db)

    alumno_row = await db.execute(
        text("""
            SELECT p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   e.matricula, n.nombre as nivel
            FROM public.ades_estudiantes e
            JOIN public.ades_personas p ON p.id = e.persona_id
            LEFT JOIN public.ades_grupos g ON g.id = e.grupo_id
            LEFT JOIN public.ades_grados gr ON gr.id = g.grado_id
            LEFT JOIN public.ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
            WHERE e.id = :est_id LIMIT 1
        """),
        {"est_id": str(estudiante_id)},
    )
    alumno = alumno_row.mappings().first()
    if not alumno:
        raise HTTPException(status_code=404, detail="Alumno no encontrado.")

    exp = await _obtener_o_crear_expediente(db, estudiante_id, ciclo_id)
    exp_id = exp["id"]

    docs_result = await db.execute(
        text("SELECT tipo_documento, ocr_texto FROM public.ades_expediente_documentos WHERE expediente_id=:exp_id"),
        {"exp_id": str(exp_id)},
    )
    docs = docs_result.mappings().all()
    tipos_presentes = [d["tipo_documento"] for d in docs]
    tipos_faltantes = [t for t in _TIPOS_REQUERIDOS if t not in tipos_presentes]

    ocr_resumen = [
        f"[{d['tipo_documento']}]: {d['ocr_texto'][:300]}"
        for d in docs if d["ocr_texto"]
    ]

    nombre_completo = f"{alumno['nombre']} {alumno['apellido_paterno']} {alumno['apellido_materno'] or ''}".strip()

    prompt = f"""Eres asistente administrativo escolar del Instituto Nevadi, Mexico.

ALUMNO: {nombre_completo} | CURP: {alumno['curp'] or 'N/A'} | Matricula: {alumno['matricula'] or 'N/A'} | Nivel: {alumno['nivel'] or 'N/A'}
DOCUMENTOS PRESENTES: {', '.join(tipos_presentes) if tipos_presentes else 'Ninguno'}
DOCUMENTOS FALTANTES: {', '.join(tipos_faltantes) if tipos_faltantes else 'Ninguno (expediente completo)'}
EXTRACTOS OCR: {chr(10).join(ocr_resumen) if ocr_resumen else 'Sin texto OCR disponible.'}

Responde SOLO con JSON valido:
{{"analisis": "...", "recomendaciones": ["...", "..."], "alertas": ["..."]}}"""

    analisis_texto = "NVIDIA NIM no configurado. Configure OPENAI_API_KEY en Vault."
    recomendaciones: list[str] = []
    alertas: list[str] = []

    if settings.OPENAI_API_KEY:
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(60.0)) as client:
                resp = await client.post(
                    f"{settings.OPENAI_BASE_URL}/chat/completions",
                    headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}", "Content-Type": "application/json"},
                    json={"model": settings.OPENAI_MODEL, "messages": [{"role": "user", "content": prompt}],
                          "temperature": 0.3, "max_tokens": 600},
                )
                resp.raise_for_status()
                content = resp.json()["choices"][0]["message"]["content"]
                json_match = re.search(r'\{.*\}', content, re.DOTALL)
                if json_match:
                    ia_data = json.loads(json_match.group())
                    analisis_texto = ia_data.get("analisis", content)
                    recomendaciones = ia_data.get("recomendaciones", [])
                    alertas = ia_data.get("alertas", [])
                else:
                    analisis_texto = content
        except Exception as exc:
            log.error("expediente.analizar_ia NIM error: %s", exc)
            analisis_texto = f"Error al conectar con NVIDIA NIM: {exc}"

    await db.execute(
        text("UPDATE public.ades_expedientes_alumno SET observaciones=:obs WHERE id=:exp_id"),
        {"obs": analisis_texto[:500], "exp_id": str(exp_id)},
    )
    await db.commit()

    return AnalisisIAResponse(
        expediente_id=exp_id,
        completitud_pct=float(exp["completitud_pct"]),
        documentos_presentes=tipos_presentes,
        documentos_faltantes=tipos_faltantes,
        analisis=analisis_texto,
        recomendaciones=recomendaciones,
        alertas=alertas,
    )

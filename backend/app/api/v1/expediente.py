"""
/expediente — Expediente Digital del Alumno con Paperless-ngx OCR.
FASE 28 — Gestión Documental

  GET    /expediente/alumno/{estudiante_id}                            — expediente + documentos + checklist
  POST   /expediente/alumno/{alumno_id}/documentos                     — subir documento (multipart)
  DELETE /expediente/{expediente_id}/documentos/{doc_id}              — eliminar documento
  GET    /expediente/alumno/{expediente_id}/documentos/{doc_id}/preview — preview desde Paperless
  POST   /expediente/alumno/{alumno_id}/analizar-ia                    — análisis IA del expediente
  POST   /expediente/{expediente_id}/verificar                         — verificar (Director/Admin)
"""
from __future__ import annotations

import json
import logging
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.services import paperless as pl

log = logging.getLogger(__name__)

router = APIRouter(prefix="/expediente", tags=["expediente"])

TIPO_LABELS: dict[str, str] = {
    "CURP":                   "CURP",
    "ACTA_NACIMIENTO":        "Acta de Nacimiento",
    "CERTIFICADO_PREV":       "Certificado de Nivel Previo",
    "COMPROBANTE_DOMICILIO":  "Comprobante de Domicilio",
    "FOTOGRAFIA":             "Fotografía",
    "NSS":                    "NSS",
    "CREDENCIAL_ESCOLAR":     "Credencial Escolar",
    "CONSTANCIA_INSCRIPCION": "Constancia de Inscripción",
    "OTRO":                   "Otro",
}

DOCS_REQUERIDOS = [
    "CURP", "ACTA_NACIMIENTO", "CERTIFICADO_PREV",
    "COMPROBANTE_DOMICILIO", "FOTOGRAFIA",
]


# ── helpers ────────────────────────────────────────────────────────────────────

async def _get_or_create_expediente(db: AsyncSession, estudiante_id: UUID) -> dict:
    """Devuelve el expediente del alumno en el ciclo activo; lo crea si no existe."""
    ciclo_row = await db.execute(
        text("SELECT id FROM ades_ciclos_escolares WHERE activo = TRUE LIMIT 1")
    )
    ciclo = ciclo_row.fetchone()
    if not ciclo:
        raise HTTPException(status_code=404, detail="No hay ciclo escolar activo")
    ciclo_id = ciclo.id

    row = await db.execute(
        text(
            "SELECT id, estudiante_id, ciclo_escolar_id, estado, completitud_pct, "
            "revisado_por, fecha_revision, observaciones "
            "FROM ades_expedientes_alumno "
            "WHERE estudiante_id = :est AND ciclo_escolar_id = :ciclo LIMIT 1"
        ),
        {"est": str(estudiante_id), "ciclo": str(ciclo_id)},
    )
    exp = row.fetchone()

    if not exp:
        ins = await db.execute(
            text(
                "INSERT INTO ades_expedientes_alumno (estudiante_id, ciclo_escolar_id) "
                "VALUES (:est, :ciclo) "
                "RETURNING id, estudiante_id, ciclo_escolar_id, estado, completitud_pct, "
                "revisado_por, fecha_revision, observaciones"
            ),
            {"est": str(estudiante_id), "ciclo": str(ciclo_id)},
        )
        await db.commit()
        exp = ins.fetchone()

    return dict(exp._mapping)


# ── schemas ────────────────────────────────────────────────────────────────────

class DocumentoOut(BaseModel):
    id: str
    expediente_id: str
    paperless_doc_id: int | None
    tipo_documento: str
    tipo_label: str
    nombre_archivo: str | None
    estado_ocr: str
    fecha_carga: str
    metadatos_ia: dict | None


class DocumentoRequeridoOut(BaseModel):
    tipo: str
    label: str
    presente: bool


class ExpedienteOut(BaseModel):
    id: str
    estudiante_id: str
    ciclo_escolar_id: str
    estado: str
    completitud_pct: float
    revisado_por: str | None
    fecha_revision: str | None
    observaciones: str | None
    documentos: list[DocumentoOut]
    documentos_requeridos: list[DocumentoRequeridoOut]


class AnalisisIAOut(BaseModel):
    expediente_id: str
    completitud_pct: float
    documentos_presentes: list[str]
    documentos_faltantes: list[str]
    analisis: str
    recomendaciones: list[str]
    alertas: list[str]


# ── endpoints ──────────────────────────────────────────────────────────────────

@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def get_expediente(
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    exp = await _get_or_create_expediente(db, estudiante_id)

    docs_result = await db.execute(
        text(
            "SELECT id, expediente_id, paperless_doc_id, tipo_documento, nombre_archivo, "
            "estado_ocr, fecha_carga, metadatos_ia "
            "FROM ades_expediente_documentos "
            "WHERE expediente_id = :exp_id ORDER BY fecha_carga DESC"
        ),
        {"exp_id": str(exp["id"])},
    )
    docs_rows = docs_result.fetchall()

    documentos = [
        DocumentoOut(
            id=str(r.id),
            expediente_id=str(r.expediente_id),
            paperless_doc_id=r.paperless_doc_id,
            tipo_documento=r.tipo_documento,
            tipo_label=TIPO_LABELS.get(r.tipo_documento, r.tipo_documento),
            nombre_archivo=r.nombre_archivo,
            estado_ocr=r.estado_ocr,
            fecha_carga=str(r.fecha_carga),
            metadatos_ia=r.metadatos_ia,
        )
        for r in docs_rows
    ]

    tipos_presentes = {d.tipo_documento for d in documentos}
    docs_requeridos = [
        DocumentoRequeridoOut(
            tipo=t,
            label=TIPO_LABELS.get(t, t),
            presente=(t in tipos_presentes),
        )
        for t in DOCS_REQUERIDOS
    ]

    return ExpedienteOut(
        id=str(exp["id"]),
        estudiante_id=str(exp["estudiante_id"]),
        ciclo_escolar_id=str(exp["ciclo_escolar_id"]),
        estado=exp["estado"],
        completitud_pct=float(exp["completitud_pct"]),
        revisado_por=str(exp["revisado_por"]) if exp["revisado_por"] else None,
        fecha_revision=str(exp["fecha_revision"]) if exp["fecha_revision"] else None,
        observaciones=exp["observaciones"],
        documentos=documentos,
        documentos_requeridos=docs_requeridos,
    )


@router.post("/alumno/{alumno_id}/documentos")
async def subir_documento(
    alumno_id: UUID,
    archivo: UploadFile = File(...),
    tipo_documento: str = Form(default="OTRO"),
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
):
    if tipo_documento not in TIPO_LABELS:
        raise HTTPException(status_code=422, detail=f"tipo_documento inválido: {tipo_documento}")

    contenido = await archivo.read()
    if len(contenido) > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Archivo excede 10 MB")

    exp = await _get_or_create_expediente(db, alumno_id)

    task_id = await pl.subir_documento(
        nombre=archivo.filename or f"{tipo_documento}.pdf",
        contenido=contenido,
        tipo_mime=archivo.content_type or "application/pdf",
        titulo=f"{tipo_documento}_{alumno_id}",
    )

    # Insertar registro y recuperar su UUID para disparar la tarea OCR
    res = await db.execute(
        text(
            "INSERT INTO ades_expediente_documentos "
            "(expediente_id, tipo_documento, nombre_archivo, estado_ocr, cargado_por) "
            "VALUES (:exp_id, :tipo, :nombre, 'PENDIENTE', :user_id) "
            "RETURNING id"
        ),
        {
            "exp_id": str(exp["id"]),
            "tipo": tipo_documento,
            "nombre": archivo.filename,
            "user_id": str(current_user.id),
        },
    )
    doc_uuid = str(res.scalar_one())
    await db.commit()

    # Disparar tarea OCR en background si Paperless devolvió un task_id
    if task_id:
        from app.worker.tasks.ocr import resolver_ocr_documento
        resolver_ocr_documento.apply_async(
            args=[doc_uuid, task_id],
            countdown=10,  # pequeña espera para que Paperless registre la tarea
        )

    return {
        "mensaje": "Documento subido. OCR en proceso.",
        "doc_id": doc_uuid,
        "task_id": task_id,
        "tipo": tipo_documento,
    }


@router.get("/alumno/{alumno_id}/buscar")
async def buscar_en_expediente(
    alumno_id: UUID,
    q: str,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Búsqueda full-text en el texto OCR de los documentos del expediente (GIN index)."""
    if len(q.strip()) < 3:
        raise HTTPException(status_code=422, detail="La búsqueda debe tener al menos 3 caracteres")

    exp = await _get_or_create_expediente(db, alumno_id)
    rows = await db.execute(
        text("""
            SELECT d.id, d.tipo_documento, d.nombre_archivo, d.estado_ocr,
                   ts_headline('spanish', d.ocr_texto,
                               plainto_tsquery('spanish', :q),
                               'MaxFragments=3,MaxWords=25,MinWords=5') AS fragmento
              FROM ades_expediente_documentos d
             WHERE d.expediente_id = :exp_id
               AND d.estado_ocr = 'PROCESADO'
               AND to_tsvector('spanish', COALESCE(d.ocr_texto,'')) @@ plainto_tsquery('spanish', :q)
             ORDER BY d.fecha_carga DESC
        """),
        {"exp_id": str(exp["id"]), "q": q},
    )
    resultados = [
        {
            "doc_id": str(r.id),
            "tipo": r.tipo_documento,
            "nombre_archivo": r.nombre_archivo,
            "estado_ocr": r.estado_ocr,
            "fragmento": r.fragmento,
        }
        for r in rows.fetchall()
    ]
    return {"query": q, "total": len(resultados), "resultados": resultados}


@router.get("/{expediente_id}/documentos/{doc_id}/estado-ocr")
async def estado_ocr_documento(
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Devuelve el estado actual del procesamiento OCR de un documento."""
    row = await db.execute(
        text("""
            SELECT id, estado_ocr, paperless_doc_id,
                   ocr_texto IS NOT NULL AND ocr_texto <> '' AS tiene_texto
              FROM ades_expediente_documentos
             WHERE id = :doc_id AND expediente_id = :exp_id
        """),
        {"doc_id": str(doc_id), "exp_id": str(expediente_id)},
    )
    doc = row.fetchone()
    if not doc:
        raise HTTPException(status_code=404, detail="Documento no encontrado")

    return {
        "doc_id": str(doc.id),
        "estado_ocr": doc.estado_ocr,
        "paperless_doc_id": doc.paperless_doc_id,
        "tiene_texto_ocr": bool(doc.tiene_texto),
    }


@router.delete("/{expediente_id}/documentos/{doc_id}", status_code=204)
async def eliminar_documento(
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text(
            "SELECT id, paperless_doc_id FROM ades_expediente_documentos "
            "WHERE id = :doc_id AND expediente_id = :exp_id"
        ),
        {"doc_id": str(doc_id), "exp_id": str(expediente_id)},
    )
    doc = row.fetchone()
    if not doc:
        raise HTTPException(status_code=404, detail="Documento no encontrado")

    if doc.paperless_doc_id:
        await pl.eliminar_documento(doc.paperless_doc_id)

    await db.execute(
        text("DELETE FROM ades_expediente_documentos WHERE id = :doc_id"),
        {"doc_id": str(doc_id)},
    )
    await db.commit()


@router.get("/alumno/{expediente_id}/documentos/{doc_id}/preview")
async def preview_documento(
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text(
            "SELECT paperless_doc_id FROM ades_expediente_documentos "
            "WHERE id = :doc_id AND expediente_id = :exp_id"
        ),
        {"doc_id": str(doc_id), "exp_id": str(expediente_id)},
    )
    doc = row.fetchone()
    if not doc or not doc.paperless_doc_id:
        raise HTTPException(status_code=404, detail="Preview no disponible (OCR pendiente)")

    contenido = await pl.descargar_documento(doc.paperless_doc_id)
    if not contenido:
        raise HTTPException(status_code=502, detail="Error al obtener documento de Paperless")

    return Response(content=contenido, media_type="application/pdf")


@router.post("/alumno/{alumno_id}/analizar-ia", response_model=AnalisisIAOut)
async def analizar_expediente_ia(
    alumno_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    exp = await _get_or_create_expediente(db, alumno_id)

    docs_result = await db.execute(
        text(
            "SELECT tipo_documento, ocr_texto "
            "FROM ades_expediente_documentos WHERE expediente_id = :exp_id"
        ),
        {"exp_id": str(exp["id"])},
    )
    docs = docs_result.fetchall()

    tipos_presentes = [d.tipo_documento for d in docs]
    faltantes = [t for t in DOCS_REQUERIDOS if t not in tipos_presentes]

    if not settings.OPENAI_API_KEY:
        return AnalisisIAOut(
            expediente_id=str(exp["id"]),
            completitud_pct=float(exp["completitud_pct"]),
            documentos_presentes=[TIPO_LABELS.get(t, t) for t in tipos_presentes],
            documentos_faltantes=[TIPO_LABELS.get(t, t) for t in faltantes],
            analisis="Análisis automático no disponible. Revise manualmente los documentos.",
            recomendaciones=["Completar documentos faltantes" if faltantes else "Expediente completo"],
            alertas=[],
        )

    from openai import AsyncOpenAI
    client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY, base_url=settings.OPENAI_BASE_URL)

    ocr_texts = "\n".join(
        f"[{d.tipo_documento}] {(d.ocr_texto or '')[:400]}" for d in docs if d.ocr_texto
    )
    prompt = (
        "Eres el sistema de gestión documental del Instituto Nevadi México.\n"
        f"Documentos presentes: {', '.join(tipos_presentes) or 'Ninguno'}\n"
        f"Documentos faltantes: {', '.join(faltantes) or 'Ninguno'}\n"
        f"Texto OCR:\n{ocr_texts or '(sin texto OCR disponible)'}\n\n"
        "Responde SOLO con JSON válido con los campos:\n"
        "- analisis: string (2-3 oraciones)\n"
        "- recomendaciones: lista de strings\n"
        "- alertas: lista de strings (solo inconsistencias graves)"
    )

    try:
        resp = await client.chat.completions.create(
            model="meta/llama-3.1-70b-instruct",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500,
            temperature=0.3,
        )
        ia_result = json.loads(resp.choices[0].message.content.strip())
    except Exception as exc:
        log.warning("analizar_expediente_ia error: %s", exc)
        ia_result = {
            "analisis": (
                f"Expediente con {len(tipos_presentes)} documento(s). "
                f"{len(faltantes)} pendiente(s) de entregar."
            ),
            "recomendaciones": [f"Subir {TIPO_LABELS.get(t, t)}" for t in faltantes],
            "alertas": [],
        }

    return AnalisisIAOut(
        expediente_id=str(exp["id"]),
        completitud_pct=float(exp["completitud_pct"]),
        documentos_presentes=[TIPO_LABELS.get(t, t) for t in tipos_presentes],
        documentos_faltantes=[TIPO_LABELS.get(t, t) for t in faltantes],
        **ia_result,
    )


@router.post("/{expediente_id}/verificar")
async def verificar_expediente(
    expediente_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
):
    if current_user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Se requiere rol Director o superior")

    result = await db.execute(
        text(
            "UPDATE ades_expedientes_alumno "
            "SET estado = 'VERIFICADO', revisado_por = :user_id, fecha_revision = NOW() "
            "WHERE id = :exp_id "
            "RETURNING id, estado"
        ),
        {"user_id": str(current_user.id), "exp_id": str(expediente_id)},
    )
    row = result.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Expediente no encontrado")
    await db.commit()

    return {"mensaje": "Expediente verificado correctamente", "estado": row.estado}

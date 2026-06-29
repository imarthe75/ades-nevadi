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
import json
import logging
from uuid import UUID

import magic

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, Request, status
from fastapi.responses import Response
from pydantic import BaseModel
from sqlalchemy import text, select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.core.ratelimit import limiter, LIMITS
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


async def _check_expediente_access(
    db: AsyncSession,
    ades_user: AdesUser,
    estudiante_id: UUID,
) -> bool:
    """
    ✅ IDOR FIX: Validar que ades_user tiene acceso a expediente del estudiante.

    Permisos por rol:
    - ADMIN_GLOBAL (plantel_id=None): Acceso a todo
    - ADMIN_PLANTEL: Solo estudiantes de su plantel
    - MAESTRO: Solo estudiantes de sus grupos
    - ESTUDIANTE: Solo su propio expediente
    - PADRE: Solo expedientes de sus hijos

    Returns: True si acceso permitido, False si denegado
    """

    # ADMIN GLOBAL: acceso a todo
    if ades_user.plantel_id is None and ades_user.rol == "ADMIN":
        return True

    # Obtener datos del estudiante
    est_row = await db.execute(
        text("SELECT plantel_id FROM ades_estudiantes WHERE id = :est_id LIMIT 1"),
        {"est_id": str(estudiante_id)},
    )
    est = est_row.fetchone()

    if not est:
        return False  # Estudiante no existe

    # ADMIN DE PLANTEL: acceso si estudiante está en su plantel
    if ades_user.plantel_id is not None and ades_user.rol == "ADMIN":
        return est[0] == ades_user.plantel_id

    # MAESTRO: acceso si es maestro de un grupo que contiene al estudiante
    if ades_user.rol == "MAESTRO":
        stmt = await db.execute(
            text("""
                SELECT 1 FROM ades_grupo_maestro gm
                INNER JOIN ades_inscripciones i ON gm.grupo_id = i.grupo_id
                WHERE gm.maestro_id = :maestro_id
                  AND i.estudiante_id = :est_id
                  AND i.is_active = TRUE
                LIMIT 1
            """),
            {"maestro_id": str(ades_user.persona_id), "est_id": str(estudiante_id)},
        )
        return stmt.fetchone() is not None

    # ESTUDIANTE: acceso solo a su propio expediente
    if ades_user.rol == "ESTUDIANTE":
        est_persona_row = await db.execute(
            text("SELECT persona_id FROM ades_estudiantes WHERE id = :est_id LIMIT 1"),
            {"est_id": str(estudiante_id)},
        )
        est_persona = est_persona_row.fetchone()
        return est_persona and est_persona[0] == ades_user.persona_id

    # PADRE: acceso a expedientes de sus hijos
    if ades_user.rol == "PADRE":
        est_persona_row = await db.execute(
            text("SELECT persona_id FROM ades_estudiantes WHERE id = :est_id LIMIT 1"),
            {"est_id": str(estudiante_id)},
        )
        est_persona = est_persona_row.fetchone()
        if not est_persona:
            return False

        stmt = await db.execute(
            text("""
                SELECT 1 FROM ades_tutor_relacion
                WHERE padre_id = :padre_id AND hijo_id = :hijo_id
                LIMIT 1
            """),
            {"padre_id": str(ades_user.persona_id), "hijo_id": str(est_persona[0])},
        )
        return stmt.fetchone() is not None

    # Por defecto: denegar acceso
    return False


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
@limiter.limit(LIMITS["read"])
async def get_expediente(
    request: Request,
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    GET expediente de un alumno.

    ✅ Validación IDOR:
    - Admin global: ve expedientes de todos
    - Admin de plantel: ve expedientes de su plantel
    - Maestro: ve expedientes de alumnos de sus grupos
    - Alumno: ve solo su propio expediente
    - Padre: ve expedientes de sus hijos
    """

    # ✅ IDOR CHECK: Verificar que usuario tiene acceso a este expediente
    if not await _check_expediente_access(db, ades_user, estudiante_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este expediente",
        )

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


@router.post("/alumno/{alumno_id}/documentos", response_model=None)
@limiter.limit(LIMITS["upload"])
async def subir_documento(
    request: Request,
    alumno_id: UUID,
    archivo: UploadFile = File(...),
    tipo_documento: str = Form(default="OTRO"),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Subir documento al expediente del alumno (con validación IDOR)."""

    # ✅ IDOR CHECK: Validar acceso antes de subir
    if not await _check_expediente_access(db, ades_user, alumno_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso al expediente de este alumno",
        )

    if tipo_documento not in TIPO_LABELS:
        raise HTTPException(status_code=422, detail=f"tipo_documento inválido: {tipo_documento}")

    MIME_PERMITIDOS = {
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/tiff",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }

    contenido = await archivo.read()
    if len(contenido) > 2 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Archivo excede 2 MB")

    # Verificar MIME real por magic bytes — no confiar en Content-Type del cliente
    mime_real = magic.from_buffer(contenido, mime=True)
    if mime_real not in MIME_PERMITIDOS:
        raise HTTPException(
            status_code=415,
            detail=f"Tipo de archivo no permitido: {mime_real}. "
                   f"Se aceptan: PDF, JPEG, PNG, WEBP, TIFF, DOC, DOCX.",
        )

    exp = await _get_or_create_expediente(db, alumno_id)

    task_id = await pl.subir_documento(
        nombre=archivo.filename or f"{tipo_documento}.pdf",
        contenido=contenido,
        tipo_mime=mime_real,  # usar el MIME verificado, no el declarado por el cliente
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
            "user_id": str(ades_user.id),
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
@limiter.limit(LIMITS["read"])
async def buscar_en_expediente(
    request: Request,
    alumno_id: UUID,
    q: str,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Búsqueda full-text en el texto OCR de los documentos del expediente (GIN index)."""

    # ✅ IDOR CHECK: Validar acceso
    if not await _check_expediente_access(db, ades_user, alumno_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso al expediente de este alumno",
        )

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
@limiter.limit(LIMITS["read"])
async def estado_ocr_documento(
    request: Request,
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
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
@limiter.limit(LIMITS["write"])
async def eliminar_documento(
    request: Request,
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Eliminar documento del expediente (con validación IDOR)."""

    # Obtener el expediente para validar acceso
    exp_row = await db.execute(
        text("SELECT estudiante_id FROM ades_expedientes_alumno WHERE id = :exp_id"),
        {"exp_id": str(expediente_id)},
    )
    exp = exp_row.fetchone()
    if not exp:
        raise HTTPException(status_code=404, detail="Expediente no encontrado")

    # ✅ IDOR CHECK: Validar acceso al expediente
    if not await _check_expediente_access(db, ades_user, exp[0]):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este expediente",
        )

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
@limiter.limit(LIMITS["read"])
async def preview_documento(
    request: Request,
    expediente_id: UUID,
    doc_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Preview de documento (con validación IDOR)."""

    # Obtener el expediente para validar acceso
    exp_row = await db.execute(
        text("SELECT estudiante_id FROM ades_expedientes_alumno WHERE id = :exp_id"),
        {"exp_id": str(expediente_id)},
    )
    exp = exp_row.fetchone()
    if not exp:
        raise HTTPException(status_code=404, detail="Expediente no encontrado")

    # ✅ IDOR CHECK: Validar acceso
    if not await _check_expediente_access(db, ades_user, exp[0]):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este expediente",
        )

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
@limiter.limit(LIMITS["write"])
async def analizar_expediente_ia(
    request: Request,
    alumno_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Análisis IA del expediente (con validación IDOR)."""

    # ✅ IDOR CHECK: Validar acceso
    if not await _check_expediente_access(db, ades_user, alumno_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso al expediente de este alumno",
        )

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

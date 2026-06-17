"""
/h5p — FASE 25: Contenido Educativo Interactivo H5P

  GET    /h5p/contenidos                         — listar contenidos (con filtros)
  GET    /h5p/contenidos/{id}                    — detalle de un contenido
  POST   /h5p/subir                              — subir paquete .h5p al servicio H5P
  DELETE /h5p/contenidos/{id}                    — eliminar contenido
  GET    /h5p/tipos                              — catálogo de tipos H5P
  GET    /h5p/player/{h5p_content_id}            — URL del player H5P
  POST   /h5p/asignaciones                       — asignar contenido a grupo/tarea
  GET    /h5p/asignaciones                       — listar asignaciones
  DELETE /h5p/asignaciones/{id}                  — eliminar asignación
  POST   /h5p/xapi-resultado                     — recibir resultado xAPI del servicio H5P
  GET    /h5p/mis-resultados                     — resultados del alumno autenticado
  GET    /h5p/resultados/{contenido_id}          — resultados de un contenido (docente/admin)
"""
from __future__ import annotations

import logging
import uuid as uuid_lib
from uuid import UUID

import httpx
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user

log = logging.getLogger(__name__)
router = APIRouter(prefix="/h5p", tags=["h5p"])

H5P_SERVICE_URL = "http://ades-h5p:8091"


# ──────────────────────────────────────────────────────────────────────────────
# Schemas
# ──────────────────────────────────────────────────────────────────────────────

class AsignacionCreate(BaseModel):
    contenido_id: UUID
    tarea_id: UUID | None = None
    grupo_id: UUID | None = None
    fecha_desde: str | None = None
    fecha_hasta: str | None = None
    intentos_max: int = 3
    puntaje_minimo: float = 60.0

class XApiResultado(BaseModel):
    h5p_content_id: str
    usuario_id: str | None = None
    asignacion_id: UUID | None = None
    score_raw: float | None = None
    score_max: float | None = None
    score_escalado: float | None = None
    completado: bool = False
    aprobado: bool | None = None
    tiempo_segundos: int | None = None
    xapi_statement: dict | None = None


# ──────────────────────────────────────────────────────────────────────────────
# Helper — proxy al servicio H5P
# ──────────────────────────────────────────────────────────────────────────────

async def _h5p_get(path: str) -> dict:
    async with httpx.AsyncClient(timeout=15.0) as client:
        r = await client.get(f"{H5P_SERVICE_URL}{path}")
        r.raise_for_status()
        return r.json()


async def _h5p_delete(path: str) -> dict:
    async with httpx.AsyncClient(timeout=10.0) as client:
        r = await client.delete(f"{H5P_SERVICE_URL}{path}")
        r.raise_for_status()
        return r.json()


# ──────────────────────────────────────────────────────────────────────────────
# Catálogo de tipos
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/tipos")
async def listar_tipos(db: AsyncSession = Depends(get_db)):
    rows = await db.execute(text("SELECT id, clave, nombre, descripcion, icono FROM ades_h5p_tipos WHERE activo ORDER BY nombre"))
    return [dict(r._mapping) for r in rows.fetchall()]


# ──────────────────────────────────────────────────────────────────────────────
# Contenidos
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/contenidos")
async def listar_contenidos(
    plantel_id: UUID | None = None,
    grado_id: UUID | None = None,
    tipo_id: UUID | None = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    where = ["c.activo = TRUE"]
    params: dict = {}
    if plantel_id:
        where.append("c.plantel_id = :plantel_id")
        params["plantel_id"] = str(plantel_id)
    if grado_id:
        where.append("c.grado_id = :grado_id")
        params["grado_id"] = str(grado_id)
    if tipo_id:
        where.append("c.tipo_id = :tipo_id")
        params["tipo_id"] = str(tipo_id)
    # Docentes solo ven sus propios contenidos o los de su plantel
    if user.nivel_acceso >= 4:
        where.append("c.creado_por = :creado_por")
        params["creado_por"] = str(user.persona_id)

    sql = f"""
        SELECT c.id, c.titulo, c.descripcion, c.h5p_content_id, c.h5p_library,
               t.nombre AS tipo_nombre, t.icono AS tipo_icono,
               c.plantel_id, c.grado_id, c.metadatos, c.fecha_creacion
        FROM ades_h5p_contenidos c
        LEFT JOIN ades_h5p_tipos t ON c.tipo_id = t.id
        WHERE {' AND '.join(where)}
        ORDER BY c.fecha_creacion DESC
    """
    rows = await db.execute(text(sql), params)
    return [dict(r._mapping) for r in rows.fetchall()]


@router.get("/contenidos/{contenido_id}")
async def detalle_contenido(
    contenido_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text("""
            SELECT c.*, t.nombre AS tipo_nombre, t.icono AS tipo_icono
            FROM ades_h5p_contenidos c
            LEFT JOIN ades_h5p_tipos t ON c.tipo_id = t.id
            WHERE c.id = :id AND c.activo
        """),
        {"id": str(contenido_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Contenido H5P no encontrado")
    return dict(r._mapping)


@router.post("/subir", status_code=201)
async def subir_h5p(
    titulo: str = Form(...),
    descripcion: str = Form(""),
    tipo_id: str | None = Form(None),
    plantel_id: str | None = Form(None),
    grado_id: str | None = Form(None),
    h5p_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 4:
        raise HTTPException(403, "Solo docentes y superiores pueden subir contenido H5P")
    if not h5p_file.filename.endswith(".h5p"):
        raise HTTPException(400, "Solo se aceptan archivos .h5p")

    # Enviar al servicio H5P
    file_bytes = await h5p_file.read()
    async with httpx.AsyncClient(timeout=60.0) as client:
        r = await client.post(
            f"{H5P_SERVICE_URL}/api/upload",
            data={"usuario_id": str(user.persona_id), "usuario_nombre": user.nombre},
            files={"h5p_file": (h5p_file.filename, file_bytes, "application/zip")},
        )
        if r.status_code != 200:
            raise HTTPException(502, f"Servicio H5P rechazó el archivo: {r.text[:300]}")
        h5p_data = r.json()

    # Persistir metadata en BD ADES
    result = await db.execute(
        text("""
            INSERT INTO ades_h5p_contenidos
                (titulo, descripcion, tipo_id, h5p_content_id, h5p_library,
                 plantel_id, grado_id, creado_por, metadatos)
            VALUES
                (:titulo, :descripcion, :tipo_id, :h5p_content_id, :h5p_library,
                 :plantel_id, :grado_id, :creado_por, :metadatos::jsonb)
            RETURNING id
        """),
        {
            "titulo": titulo,
            "descripcion": descripcion,
            "tipo_id": tipo_id,
            "h5p_content_id": str(h5p_data["h5p_content_id"]),
            "h5p_library": h5p_data.get("library"),
            "plantel_id": plantel_id,
            "grado_id": grado_id,
            "creado_por": str(user.persona_id),
            "metadatos": __import__("json").dumps(h5p_data.get("metadatos", {})),
        },
    )
    await db.commit()
    new_id = result.fetchone()[0]
    return {"id": str(new_id), **h5p_data}


@router.delete("/contenidos/{contenido_id}", status_code=204)
async def eliminar_contenido(
    contenido_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Solo coordinadores y superiores pueden eliminar contenido H5P")

    row = await db.execute(text("SELECT h5p_content_id FROM ades_h5p_contenidos WHERE id = :id"), {"id": str(contenido_id)})
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Contenido no encontrado")

    # Eliminar del servicio H5P
    try:
        await _h5p_delete(f"/api/contenidos/{r.h5p_content_id}")
    except Exception:
        pass  # Si el servicio no lo encuentra, continuar

    await db.execute(text("UPDATE ades_h5p_contenidos SET activo = FALSE WHERE id = :id"), {"id": str(contenido_id)})
    await db.commit()


# ──────────────────────────────────────────────────────────────────────────────
# Player URL
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/player/{contenido_id}")
async def player_url(
    contenido_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text("SELECT h5p_content_id FROM ades_h5p_contenidos WHERE id = :id AND activo"),
        {"id": str(contenido_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Contenido H5P no encontrado")
    player_url = (
        f"{H5P_SERVICE_URL}/api/player/{r.h5p_content_id}"
        f"?usuario_id={user.persona_id}&usuario_nombre={user.nombre}"
    )
    return {"player_url": player_url, "h5p_content_id": r.h5p_content_id}


# ──────────────────────────────────────────────────────────────────────────────
# Asignaciones
# ──────────────────────────────────────────────────────────────────────────────

@router.post("/asignaciones", status_code=201)
async def crear_asignacion(
    body: AsignacionCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 4:
        raise HTTPException(403, "Solo docentes y superiores pueden asignar contenido H5P")
    result = await db.execute(
        text("""
            INSERT INTO ades_h5p_asignaciones
                (contenido_id, tarea_id, grupo_id, fecha_desde, fecha_hasta, intentos_max, puntaje_minimo)
            VALUES
                (:contenido_id, :tarea_id, :grupo_id, :fecha_desde::date, :fecha_hasta::date,
                 :intentos_max, :puntaje_minimo)
            RETURNING id
        """),
        {
            "contenido_id": str(body.contenido_id),
            "tarea_id": str(body.tarea_id) if body.tarea_id else None,
            "grupo_id": str(body.grupo_id) if body.grupo_id else None,
            "fecha_desde": body.fecha_desde,
            "fecha_hasta": body.fecha_hasta,
            "intentos_max": body.intentos_max,
            "puntaje_minimo": body.puntaje_minimo,
        },
    )
    await db.commit()
    return {"id": str(result.fetchone()[0])}


@router.get("/asignaciones")
async def listar_asignaciones(
    grupo_id: UUID | None = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    where = ["a.activo"]
    params: dict = {}
    if grupo_id:
        where.append("a.grupo_id = :grupo_id")
        params["grupo_id"] = str(grupo_id)
    rows = await db.execute(
        text(f"""
            SELECT a.*, c.titulo, c.h5p_content_id, c.h5p_library
            FROM ades_h5p_asignaciones a
            JOIN ades_h5p_contenidos c ON a.contenido_id = c.id
            WHERE {' AND '.join(where)}
            ORDER BY a.fecha_creacion DESC
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


@router.delete("/asignaciones/{asignacion_id}", status_code=204)
async def eliminar_asignacion(
    asignacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Solo coordinadores y superiores pueden eliminar asignaciones")
    await db.execute(
        text("UPDATE ades_h5p_asignaciones SET activo = FALSE WHERE id = :id"),
        {"id": str(asignacion_id)},
    )
    await db.commit()


# ──────────────────────────────────────────────────────────────────────────────
# Resultados xAPI
# ──────────────────────────────────────────────────────────────────────────────

@router.post("/xapi-resultado")
async def recibir_xapi(
    body: XApiResultado,
    db: AsyncSession = Depends(get_db),
):
    """Endpoint interno llamado por el servicio H5P Node.js — sin auth JWT (red interna)."""
    # Buscar contenido_id por h5p_content_id
    row = await db.execute(
        text("SELECT id FROM ades_h5p_contenidos WHERE h5p_content_id = :h5p_id AND activo"),
        {"h5p_id": body.h5p_content_id},
    )
    r = row.fetchone()
    if not r:
        return {"ignorado": True, "razon": "contenido_no_registrado"}
    contenido_id = r.id

    # Buscar estudiante_id por persona_id
    est_row = await db.execute(
        text("SELECT id FROM ades_estudiantes WHERE persona_id = :pid LIMIT 1"),
        {"pid": str(body.usuario_id) if body.usuario_id else uuid_lib.uuid4()},
    )
    est = est_row.fetchone()
    if not est:
        return {"ignorado": True, "razon": "alumno_no_encontrado"}
    estudiante_id = est.id

    # Calcular intento actual
    cnt_row = await db.execute(
        text("SELECT COALESCE(MAX(intento), 0) + 1 FROM ades_h5p_resultados WHERE contenido_id = :cid AND estudiante_id = :eid"),
        {"cid": str(contenido_id), "eid": str(estudiante_id)},
    )
    intento = cnt_row.scalar()

    await db.execute(
        text("""
            INSERT INTO ades_h5p_resultados
                (contenido_id, estudiante_id, asignacion_id, intento,
                 score_raw, score_max, score_escalado, completado, aprobado,
                 tiempo_segundos, xapi_statement)
            VALUES
                (:contenido_id, :estudiante_id, :asignacion_id, :intento,
                 :score_raw, :score_max, :score_escalado, :completado, :aprobado,
                 :tiempo_segundos, :xapi::jsonb)
            ON CONFLICT (contenido_id, estudiante_id, intento) DO UPDATE SET
                score_raw = EXCLUDED.score_raw,
                score_escalado = EXCLUDED.score_escalado,
                completado = EXCLUDED.completado,
                aprobado = EXCLUDED.aprobado
        """),
        {
            "contenido_id": str(contenido_id),
            "estudiante_id": str(estudiante_id),
            "asignacion_id": str(body.asignacion_id) if body.asignacion_id else None,
            "intento": intento,
            "score_raw": body.score_raw,
            "score_max": body.score_max,
            "score_escalado": body.score_escalado,
            "completado": body.completado,
            "aprobado": body.aprobado,
            "tiempo_segundos": body.tiempo_segundos,
            "xapi": __import__("json").dumps(body.xapi_statement) if body.xapi_statement else "null",
        },
    )
    await db.commit()
    return {"guardado": True, "intento": intento}


@router.get("/mis-resultados")
async def mis_resultados(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    rows = await db.execute(
        text("""
            SELECT r.id, r.contenido_id, c.titulo, c.h5p_library, r.intento,
                   r.score_raw, r.score_max, r.score_escalado, r.completado, r.aprobado,
                   r.tiempo_segundos, r.fecha_creacion
            FROM ades_h5p_resultados r
            JOIN ades_h5p_contenidos c ON r.contenido_id = c.id
            JOIN ades_estudiantes e ON r.estudiante_id = e.id
            WHERE e.persona_id = :pid
            ORDER BY r.fecha_creacion DESC
        """),
        {"pid": str(user.persona_id)},
    )
    return [dict(r._mapping) for r in rows.fetchall()]


@router.get("/resultados/{contenido_id}")
async def resultados_contenido(
    contenido_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 4:
        raise HTTPException(403, "Solo docentes y superiores pueden ver resultados de grupo")
    rows = await db.execute(
        text("""
            SELECT r.id, r.estudiante_id, r.intento,
                   r.score_raw, r.score_max, r.score_escalado,
                   r.completado, r.aprobado, r.tiempo_segundos, r.fecha_creacion,
                   COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS nombre_alumno
            FROM ades_h5p_resultados r
            JOIN ades_estudiantes e ON r.estudiante_id = e.id
            JOIN ades_personas p ON e.persona_id = p.id
            WHERE r.contenido_id = :cid
            ORDER BY nombre_alumno, r.intento DESC
        """),
        {"cid": str(contenido_id)},
    )
    return [dict(r._mapping) for r in rows.fetchall()]

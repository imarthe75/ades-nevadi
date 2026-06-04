"""
/planeacion — Planeación de clases y avance del plan de estudios.

  GET  /planeacion/temas              — temas del plan con estado (pendiente/planeado/impartido)
  GET  /planeacion/cobertura/{gid}    — % cobertura curricular por materia del grupo
  GET  /planeacion/clases             — entradas de planeación por grupo/materia
  POST /planeacion/clases             — crear entrada de planeación para un tema
  POST /planeacion/clases/{id}/completar — marcar tema como impartido
  DELETE /planeacion/clases/{id}      — baja lógica
"""
from __future__ import annotations

import datetime
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/planeacion", tags=["planeacion"])


# ── schemas ───────────────────────────────────────────────────────────────────

class PlaneacionCreate(BaseModel):
    grupo_id: UUID
    tema_id: UUID
    fecha_planeada: datetime.date
    descripcion_actividades: Optional[str] = None
    recursos_didacticos: Optional[str] = None


class CompletarAvance(BaseModel):
    clase_id: Optional[UUID] = None
    fecha_ejecucion: datetime.date
    comentarios_profesor: Optional[str] = None


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("/temas")
async def temas_con_estado(
    grupo_id: UUID,
    materia_id: Optional[UUID] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Devuelve los temas del plan de estudios del grupo con su estado:
    IMPARTIDO / PLANEADO / PENDIENTE.
    """
    filters = ["g.id = :grupo_id::uuid", "t.is_active = TRUE"]
    params: dict = {"grupo_id": str(grupo_id)}

    if materia_id:
        filters.append("t.materia_id = :materia_id::uuid")
        params["materia_id"] = str(materia_id)

    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT
            t.id          AS tema_id,
            t.nombre_tema,
            t.descripcion AS descripcion_tema,
            t.orden,
            t.periodo_sugerido,
            m.id          AS materia_id,
            m.nombre_materia,
            pc.id         AS planeacion_id,
            pc.fecha_planeada,
            pc.descripcion_actividades,
            av.id         AS avance_id,
            av.fecha_ejecucion,
            av.es_completado,
            av.comentarios_profesor,
            CASE
                WHEN av.es_completado = TRUE THEN 'IMPARTIDO'
                WHEN pc.id IS NOT NULL       THEN 'PLANEADO'
                ELSE                              'PENDIENTE'
            END           AS estado
        FROM ades_grupos g
        JOIN ades_grados gr         ON gr.id = g.grado_id
        JOIN ades_materias_plan mp  ON mp.nivel_educativo_id = gr.nivel_educativo_id
        JOIN ades_materias m        ON m.id = mp.materia_id
        JOIN ades_temas t           ON t.materia_id = m.id
            AND (t.grado_id IS NULL OR t.grado_id = gr.id)
        LEFT JOIN ades_planeacion_clases pc
            ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
        LEFT JOIN ades_avance_planificacion av
            ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
        WHERE {where}
        ORDER BY m.nombre_materia, t.orden
    """), params)

    return rows.mappings().all()


@router.get("/cobertura/{grupo_id}")
async def cobertura_grupo(
    grupo_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Porcentaje de cobertura curricular por materia para el grupo."""
    rows = await db.execute(text("""
        SELECT
            m.id          AS materia_id,
            m.nombre_materia,
            COUNT(t.id)                                               AS total_temas,
            COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)       AS temas_impartidos,
            COUNT(pc.id) FILTER (WHERE av.id IS NULL)                 AS temas_planeados,
            ROUND(
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                / NULLIF(COUNT(t.id), 0) * 100, 1
            )                                                         AS pct_cobertura
        FROM ades_grupos g
        JOIN ades_grados gr        ON gr.id = g.grado_id
        JOIN ades_materias_plan mp ON mp.nivel_educativo_id = gr.nivel_educativo_id
        JOIN ades_materias m       ON m.id = mp.materia_id
        JOIN ades_temas t          ON t.materia_id = m.id
            AND (t.grado_id IS NULL OR t.grado_id = gr.id)
            AND t.is_active = TRUE
        LEFT JOIN ades_planeacion_clases pc
            ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
        LEFT JOIN ades_avance_planificacion av
            ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
        WHERE g.id = :grupo_id::uuid
        GROUP BY m.id, m.nombre_materia
        ORDER BY m.nombre_materia
    """), {"grupo_id": str(grupo_id)})
    return rows.mappings().all()


@router.get("/clases")
async def listar_planeacion(
    grupo_id: UUID,
    materia_id: Optional[UUID] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["pc.grupo_id = :grupo_id::uuid", "pc.is_active = TRUE"]
    params: dict = {"grupo_id": str(grupo_id)}

    if materia_id:
        filters.append("t.materia_id = :materia_id::uuid")
        params["materia_id"] = str(materia_id)

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            pc.id, pc.fecha_planeada,
            pc.descripcion_actividades, pc.recursos_didacticos,
            t.id AS tema_id, t.nombre_tema, t.orden,
            m.nombre_materia,
            av.es_completado, av.fecha_ejecucion, av.comentarios_profesor
        FROM ades_planeacion_clases pc
        JOIN ades_temas t    ON t.id = pc.tema_id
        JOIN ades_materias m ON m.id = t.materia_id
        LEFT JOIN ades_avance_planificacion av
            ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
        WHERE {where}
        ORDER BY pc.fecha_planeada, m.nombre_materia, t.orden
    """), params)
    return rows.mappings().all()


@router.post("/clases", status_code=201)
async def crear_planeacion(
    body: PlaneacionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_planeacion_clases
            (grupo_id, tema_id, fecha_planeada, descripcion_actividades, recursos_didacticos)
        VALUES
            (:grupo_id::uuid, :tema_id::uuid, :fecha, :descripcion, :recursos)
        ON CONFLICT DO NOTHING
        RETURNING id, tema_id, fecha_planeada
    """), {
        "grupo_id":    str(body.grupo_id),
        "tema_id":     str(body.tema_id),
        "fecha":       body.fecha_planeada,
        "descripcion": body.descripcion_actividades,
        "recursos":    body.recursos_didacticos,
    })
    await db.commit()
    result = row.mappings().first()
    if not result:
        return {"ok": True, "mensaje": "Ya existía una planeación para este tema/grupo"}
    return result


@router.post("/clases/{planeacion_id}/completar")
async def completar_tema(
    planeacion_id: UUID,
    body: CompletarAvance,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Registra que el tema fue efectivamente impartido en clase."""
    row = await db.execute(text("""
        INSERT INTO ades_avance_planificacion
            (planeacion_clase_id, clase_id, fecha_ejecucion, es_completado, comentarios_profesor)
        VALUES
            (:pc_id::uuid, :clase_id, :fecha, TRUE, :comentarios)
        ON CONFLICT DO NOTHING
        RETURNING id, es_completado, fecha_ejecucion
    """), {
        "pc_id":       str(planeacion_id),
        "clase_id":    str(body.clase_id) if body.clase_id else None,
        "fecha":       body.fecha_ejecucion,
        "comentarios": body.comentarios_profesor,
    })
    await db.commit()
    return row.mappings().first() or {"ok": True, "mensaje": "Ya registrado"}


@router.delete("/clases/{planeacion_id}", status_code=204)
async def eliminar_planeacion(
    planeacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_planeacion_clases SET is_active = FALSE WHERE id = :id::uuid
    """), {"id": str(planeacion_id)})
    await db.commit()

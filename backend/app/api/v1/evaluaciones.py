"""
/evaluaciones — Exámenes y evaluaciones formales (ordinarios, finales, extraordinarios).

  GET    /evaluaciones                          — lista por grupo/periodo/tipo
  POST   /evaluaciones                          — programar evaluación
  GET    /evaluaciones/{id}                     — detalle con estadísticas
  DELETE /evaluaciones/{id}                     — baja lógica
  GET    /evaluaciones/{id}/calificaciones       — alumnos del grupo con su calificación
  POST   /evaluaciones/{id}/calificaciones/bulk  — guardar calificaciones en bloque
"""
from __future__ import annotations

import datetime
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser

router = APIRouter(prefix="/evaluaciones", tags=["evaluaciones"])


# ── schemas ───────────────────────────────────────────────────────────────────

class EvaluacionCreate(BaseModel):
    grupo_id: UUID
    materia_id: UUID
    periodo_evaluacion_id: UUID
    nombre_evaluacion: str
    descripcion: Optional[str] = None
    fecha_evaluacion: datetime.date
    tipo_evaluacion: str = "ORDINARIO"   # ORDINARIO | FINAL | EXTRAORDINARIO | DIAGNOSTICO
    puntaje_maximo: float = 10.0


class CalificacionItem(BaseModel):
    estudiante_id: UUID
    calificacion: float
    comentarios: Optional[str] = None


class BulkCalificaciones(BaseModel):
    calificaciones: list[CalificacionItem]


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_evaluaciones(
    grupo_id: Optional[UUID] = None,
    periodo_evaluacion_id: Optional[UUID] = None,
    tipo_evaluacion: Optional[str] = None,
    ciclo_id: Optional[UUID] = None,
    limit: int = 100,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["e.is_active = TRUE"]
    params: dict = {"limit": limit}

    if grupo_id:
        filters.append("e.grupo_id = :grupo_id::uuid")
        params["grupo_id"] = str(grupo_id)
    if periodo_evaluacion_id:
        filters.append("e.periodo_evaluacion_id = :periodo_id::uuid")
        params["periodo_id"] = str(periodo_evaluacion_id)
    if tipo_evaluacion:
        filters.append("e.tipo_evaluacion = :tipo")
        params["tipo"] = tipo_evaluacion
    if ciclo_id:
        filters.append("pe.ciclo_escolar_id = :ciclo_id::uuid")
        params["ciclo_id"] = str(ciclo_id)

    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT
            e.id, e.nombre_evaluacion, e.descripcion,
            e.grupo_id, g.nombre_grupo,
            e.materia_id, m.nombre_materia,
            e.periodo_evaluacion_id, pe.nombre_periodo, pe.numero_periodo,
            e.fecha_evaluacion, e.tipo_evaluacion, e.puntaje_maximo,
            COUNT(ce.id)                                         AS total_calificados,
            ROUND(AVG(ce.calificacion), 2)                       AS promedio,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = TRUE)  AS aprobados,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = FALSE) AS reprobados
        FROM ades_evaluaciones e
        JOIN ades_grupos              g   ON g.id  = e.grupo_id
        JOIN ades_materias            m   ON m.id  = e.materia_id
        JOIN ades_periodos_evaluacion pe  ON pe.id = e.periodo_evaluacion_id
        LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = e.id
        WHERE {where}
        GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo
        ORDER BY e.fecha_evaluacion DESC
        LIMIT :limit
    """), params)

    return rows.mappings().all()


@router.post("", status_code=201)
async def crear_evaluacion(
    body: EvaluacionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_evaluaciones
            (grupo_id, materia_id, periodo_evaluacion_id, nombre_evaluacion,
             descripcion, fecha_evaluacion, tipo_evaluacion, puntaje_maximo)
        VALUES
            (:grupo_id::uuid, :materia_id::uuid, :periodo_id::uuid, :nombre,
             :descripcion, :fecha, :tipo, :puntaje)
        RETURNING id, nombre_evaluacion, tipo_evaluacion, fecha_evaluacion
    """), {
        "grupo_id":    str(body.grupo_id),
        "materia_id":  str(body.materia_id),
        "periodo_id":  str(body.periodo_evaluacion_id),
        "nombre":      body.nombre_evaluacion,
        "descripcion": body.descripcion,
        "fecha":       body.fecha_evaluacion,
        "tipo":        body.tipo_evaluacion,
        "puntaje":     body.puntaje_maximo,
    })
    await db.commit()
    return row.mappings().first()


@router.get("/{evaluacion_id}")
async def detalle_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        SELECT
            e.*,
            g.nombre_grupo, m.nombre_materia,
            pe.nombre_periodo, pe.numero_periodo,
            COUNT(ce.id)                                         AS total_calificados,
            ROUND(AVG(ce.calificacion), 2)                       AS promedio,
            ROUND(MIN(ce.calificacion), 2)                       AS minimo,
            ROUND(MAX(ce.calificacion), 2)                       AS maximo,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = TRUE)  AS aprobados,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = FALSE) AS reprobados
        FROM ades_evaluaciones e
        JOIN ades_grupos              g   ON g.id  = e.grupo_id
        JOIN ades_materias            m   ON m.id  = e.materia_id
        JOIN ades_periodos_evaluacion pe  ON pe.id = e.periodo_evaluacion_id
        LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = e.id
        WHERE e.id = :id::uuid AND e.is_active = TRUE
        GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo
    """), {"id": str(evaluacion_id)})
    result = row.mappings().first()
    if not result:
        raise HTTPException(status_code=404, detail="Evaluación no encontrada")
    return result


@router.get("/{evaluacion_id}/calificaciones")
async def calificaciones_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Retorna lista de todos los alumnos del grupo con su calificación (NULL si no tiene)."""
    row_eval = await db.execute(text("""
        SELECT grupo_id FROM ades_evaluaciones WHERE id = :id::uuid AND is_active = TRUE
    """), {"id": str(evaluacion_id)})
    eval_row = row_eval.fetchone()
    if not eval_row:
        raise HTTPException(status_code=404, detail="Evaluación no encontrada")

    rows = await db.execute(text("""
        SELECT
            est.id                                                    AS estudiante_id,
            p.primer_apellido || ' ' || COALESCE(p.segundo_apellido,'') || ', ' || p.nombres AS nombre_alumno,
            ce.id                                                     AS calificacion_id,
            ce.calificacion,
            ce.es_acreditado,
            ce.comentarios
        FROM ades_inscripciones i
        JOIN ades_estudiantes est ON est.id = i.estudiante_id
        JOIN ades_personas     p   ON p.id  = est.persona_id
        LEFT JOIN ades_calificaciones_evaluaciones ce
            ON ce.evaluacion_id = :eval_id::uuid AND ce.estudiante_id = est.id
        WHERE i.grupo_id = :grupo_id::uuid AND i.is_active = TRUE
        ORDER BY p.primer_apellido, p.segundo_apellido, p.nombres
    """), {"eval_id": str(evaluacion_id), "grupo_id": str(eval_row[0])})

    return rows.mappings().all()


@router.post("/{evaluacion_id}/calificaciones/bulk")
async def guardar_calificaciones_bulk(
    evaluacion_id: UUID,
    body: BulkCalificaciones,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Guarda o actualiza calificaciones de múltiples alumnos para una evaluación."""
    total = 0
    for item in body.calificaciones:
        if item.calificacion is None:
            continue
        await db.execute(text("""
            INSERT INTO ades_calificaciones_evaluaciones
                (evaluacion_id, estudiante_id, calificacion, comentarios)
            VALUES
                (:eval_id::uuid, :est_id::uuid, :cal, :comentarios)
            ON CONFLICT (evaluacion_id, estudiante_id)
            DO UPDATE SET
                calificacion  = EXCLUDED.calificacion,
                comentarios   = EXCLUDED.comentarios,
                fecha_modificacion = NOW()
        """), {
            "eval_id":    str(evaluacion_id),
            "est_id":     str(item.estudiante_id),
            "cal":        item.calificacion,
            "comentarios": item.comentarios,
        })
        total += 1

    await db.commit()
    return {"ok": True, "guardadas": total}


@router.delete("/{evaluacion_id}", status_code=204)
async def eliminar_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_evaluaciones SET is_active = FALSE WHERE id = :id::uuid
    """), {"id": str(evaluacion_id)})
    await db.commit()


# ═══════════════════════════════════════════════════════════════════════════════
# CIERRE FORMAL DE PERÍODO (EV-006)
# ═══════════════════════════════════════════════════════════════════════════════

class CierrePeriodoBody(BaseModel):
    grupo_id: UUID
    notas: Optional[str] = None


_ROLES_CIERRE = {"ADMIN_GLOBAL", "ADMIN_PLANTEL", "DIRECTOR", "COORDINADOR_ACADEMICO"}


@router.get("/periodos/{periodo_id}/validar-cierre")
async def validar_cierre(
    periodo_id: UUID,
    grupo_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Valida si un período puede cerrarse para el grupo indicado.
    Retorna: puede_cerrar, alumnos_faltantes, materias_incompletas, detalles.
    """
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Solo DIRECTOR/COORDINADOR/ADMIN puede cerrar períodos")

    row = await db.execute(
        text("SELECT * FROM validar_cierre_periodo(:periodo_id::uuid, :grupo_id::uuid)"),
        {"periodo_id": str(periodo_id), "grupo_id": str(grupo_id)},
    )
    result = row.mappings().first()
    if not result:
        raise HTTPException(404, "Período o grupo no encontrado")
    return dict(result)


@router.post("/periodos/{periodo_id}/cerrar", status_code=200)
async def cerrar_periodo_formal(
    periodo_id: UUID,
    body: CierrePeriodoBody,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Cierre formal de período:
    1. Valida que todos los alumnos tengan calificación
    2. Genera snapshot histórico
    3. Bloquea edición (cerrada = TRUE)
    4. Registra en ades_cierre_periodo_log
    """
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Solo DIRECTOR/COORDINADOR/ADMIN puede cerrar períodos")

    grupo_id  = str(body.grupo_id)
    per_id    = str(periodo_id)
    usuario_id = str(ades_user.usuario_id)

    # ── 1. Validar ─────────────────────────────────────────────────────────────
    val = await db.execute(
        text("SELECT * FROM validar_cierre_periodo(:per_id::uuid, :grupo_id::uuid)"),
        {"per_id": per_id, "grupo_id": grupo_id},
    )
    validacion = val.mappings().first()
    if not validacion:
        raise HTTPException(404, "Período o grupo no encontrado")
    if not validacion["puede_cerrar"]:
        raise HTTPException(
            422,
            {
                "detail": "No se puede cerrar: hay calificaciones faltantes",
                "alumnos_faltantes": validacion["alumnos_faltantes"],
                "materias_incompletas": validacion["materias_incompletas"],
                "detalles": validacion["detalles"],
            },
        )

    # ── 2. Obtener ciclo_escolar_id del período ─────────────────────────────────
    ciclo_row = await db.execute(
        text("SELECT ciclo_escolar_id FROM ades_periodos_evaluacion WHERE id = :id::uuid"),
        {"id": per_id},
    )
    ciclo_id_val = ciclo_row.scalar_one_or_none()

    # ── 3. Crear registro de log (devuelve el ID del cierre) ────────────────────
    log_row = await db.execute(
        text("""
            INSERT INTO ades_cierre_periodo_log
                (periodo_evaluacion_id, grupo_id, ciclo_escolar_id,
                 calificaciones_cerradas, alumnos_sin_calificacion,
                 estado, cerrado_por, notas)
            VALUES
                (:per_id::uuid, :grupo_id::uuid, :ciclo_id::uuid,
                 :cal_cerradas, 0,
                 'CERRADO', :usuario_id::uuid, :notas)
            RETURNING id
        """),
        {
            "per_id":        per_id,
            "grupo_id":      grupo_id,
            "ciclo_id":      str(ciclo_id_val) if ciclo_id_val else None,
            "cal_cerradas":  int(validacion["detalles"].get("total_esperadas", 0)),
            "usuario_id":    usuario_id,
            "notas":         body.notas,
        },
    )
    cierre_id = str(log_row.scalar_one())

    # ── 4. Snapshot histórico ───────────────────────────────────────────────────
    await db.execute(
        text("""
            INSERT INTO ades_calificaciones_historico
                (cierre_id, cal_periodo_id, estudiante_id, grupo_id,
                 materia_id, periodo_evaluacion_id,
                 calificacion_final, calificacion_calculada, ajuste_manual, es_acreditado)
            SELECT
                :cierre_id::uuid,
                cp.id, cp.estudiante_id, cp.grupo_id,
                cp.materia_id, cp.periodo_evaluacion_id,
                cp.calificacion_final, cp.calificacion_calculada,
                cp.ajuste_manual, cp.es_acreditado
            FROM ades_calificaciones_periodo cp
            WHERE cp.grupo_id              = :grupo_id::uuid
              AND cp.periodo_evaluacion_id = :per_id::uuid
              AND cp.is_active             = TRUE
        """),
        {"cierre_id": cierre_id, "grupo_id": grupo_id, "per_id": per_id},
    )

    # ── 5. Bloquear edición ─────────────────────────────────────────────────────
    result = await db.execute(
        text("""
            UPDATE ades_calificaciones_periodo
               SET cerrada            = TRUE,
                   fecha_cierre       = now(),
                   cerrado_por        = :usuario_id::uuid,
                   fecha_modificacion = now()
             WHERE grupo_id              = :grupo_id::uuid
               AND periodo_evaluacion_id = :per_id::uuid
               AND is_active             = TRUE
               AND cerrada               = FALSE
            RETURNING id
        """),
        {"usuario_id": usuario_id, "grupo_id": grupo_id, "per_id": per_id},
    )
    filas_cerradas = len(result.fetchall())

    await db.commit()

    return {
        "ok": True,
        "cierre_id": cierre_id,
        "calificaciones_cerradas": filas_cerradas,
        "message": f"Período cerrado correctamente. {filas_cerradas} calificaciones bloqueadas.",
    }

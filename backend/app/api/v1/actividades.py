"""
Actividades evaluables — CRUD de ades_tareas con campos extendidos
del módulo Gradebook (tipo_item, plan_trabajo_id, rubrica_id, etc.)
Al crear una actividad, genera automáticamente un slot de entrega
(ades_tareas_entregas) para cada alumno inscrito en el grupo.
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user
from typing import Optional
from pydantic import BaseModel
from datetime import date

router = APIRouter(prefix="/actividades", tags=["Gradebook – Actividades"])


class ActividadIn(BaseModel):
    titulo: str
    descripcion: Optional[str] = None
    grupo_id: str
    materia_id: str
    periodo_evaluacion_id: Optional[str] = None
    tipo_item: str = "tarea"
    tema_id: Optional[str] = None
    plan_trabajo_id: Optional[str] = None
    rubrica_id: Optional[str] = None
    fecha_asignacion: date
    fecha_entrega: date
    fecha_examen: Optional[date] = None
    puntaje_maximo: float = 10.0
    instrucciones_url: Optional[str] = None
    permite_entrega_tarde: bool = False


class CalificarMasivoItem(BaseModel):
    alumno_id: str
    calificacion: float
    comentario: Optional[str] = None


# ── GET /actividades/grupo/{grupo_materia} ────────────────────
@router.get("/grupo/{grupo_id}")
async def actividades_de_grupo(
    grupo_id: str,
    materia_id: Optional[str] = None,
    periodo_id: Optional[str] = None,
    tipo_item: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    filters = "WHERE t.grupo_id = :gid AND t.is_active = TRUE"
    params: dict = {"gid": grupo_id}
    if materia_id:
        filters += " AND t.materia_id = :mid"
        params["mid"] = materia_id
    if periodo_id:
        filters += " AND t.periodo_evaluacion_id = :pid"
        params["pid"] = periodo_id
    if tipo_item:
        filters += " AND t.tipo_item = :tipo"
        params["tipo"] = tipo_item

    rows = await db.execute(
        text(f"""
            SELECT t.id, t.titulo, t.descripcion, t.tipo_item,
                   t.fecha_asignacion, t.fecha_entrega, t.fecha_examen,
                   t.puntaje_maximo, t.permite_entrega_tarde,
                   t.instrucciones_url,
                   m.nombre_materia,
                   te_stats.total_alumnos,
                   te_stats.entregadas,
                   te_stats.calificadas,
                   pe.nombre_periodo,
                   tm.nombre_tema
              FROM ades_tareas t
              JOIN ades_materias m ON m.id = t.materia_id
              LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
              LEFT JOIN ades_temas tm ON tm.id = t.tema_id
              LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS total_alumnos,
                         COUNT(*) FILTER (WHERE te.estatus_entrega IN ('ENTREGADA','CALIFICADA')) AS entregadas,
                         COUNT(*) FILTER (WHERE te.estatus_entrega = 'CALIFICADA') AS calificadas
                    FROM ades_tareas_entregas te
                   WHERE te.tarea_id = t.id
              ) te_stats ON TRUE
             {filters}
             ORDER BY t.fecha_entrega, t.tipo_item
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── POST /actividades ─────────────────────────────────────────
@router.post("", status_code=201)
async def crear_actividad(
    body: ActividadIn,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    # Crear la tarea
    row = await db.execute(
        text("""
            INSERT INTO ades_tareas
                   (titulo, descripcion, grupo_id, materia_id,
                    periodo_evaluacion_id, tipo_item, tema_id,
                    plan_trabajo_id, rubrica_id,
                    fecha_asignacion, fecha_entrega, fecha_examen,
                    puntaje_maximo, instrucciones_url,
                    permite_entrega_tarde, origen)
            VALUES (:titulo, :desc, :gid, :mid,
                    :pid, :tipo, :tema,
                    :plan, :rubrica,
                    :fa, :fe, :fex,
                    :pmax, :iurl,
                    :tarde, 'MANUAL')
            RETURNING id
        """),
        {
            "titulo": body.titulo,
            "desc": body.descripcion,
            "gid": body.grupo_id,
            "mid": body.materia_id,
            "pid": body.periodo_evaluacion_id,
            "tipo": body.tipo_item,
            "tema": body.tema_id,
            "plan": body.plan_trabajo_id,
            "rubrica": body.rubrica_id,
            "fa": body.fecha_asignacion,
            "fe": body.fecha_entrega,
            "fex": body.fecha_examen,
            "pmax": body.puntaje_maximo,
            "iurl": body.instrucciones_url,
            "tarde": body.permite_entrega_tarde,
        },
    )
    tarea_id = row.scalar()

    # Generar slot de entrega para cada alumno del grupo
    alumnos = await db.execute(
        text("""
            SELECT i.estudiante_id
              FROM ades_inscripciones i
             WHERE i.grupo_id = :gid AND i.is_active = TRUE
        """),
        {"gid": body.grupo_id},
    )
    slots_creados = 0
    for alumno_row in alumnos.fetchall():
        await db.execute(
            text("""
                INSERT INTO ades_tareas_entregas (tarea_id, estudiante_id, estatus_entrega)
                VALUES (:tid, :eid, 'PENDIENTE')
                ON CONFLICT (tarea_id, estudiante_id) DO NOTHING
            """),
            {"tid": tarea_id, "eid": alumno_row.estudiante_id},
        )
        slots_creados += 1

    await db.commit()
    return {
        "id": str(tarea_id),
        "slots_creados": slots_creados,
        "message": "Actividad creada y slots generados",
    }


# ── GET /actividades/{id}/entregas ────────────────────────────
@router.get("/{actividad_id}/entregas")
async def entregas_de_actividad(
    actividad_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    rows = await db.execute(
        text("""
            SELECT te.id, te.estudiante_id, te.estatus_entrega,
                   te.fecha_entrega, te.es_tarde,
                   te.calificacion_obtenida, te.comentario_profesor,
                   te.archivo_url,
                   p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                   est.numero_matricula
              FROM ades_tareas_entregas te
              JOIN ades_estudiantes est ON est.id = te.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
             WHERE te.tarea_id = :tid
             ORDER BY p.apellido_paterno, p.nombre
        """),
        {"tid": actividad_id},
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── PATCH /actividades/{id}/calificar-masivo ─────────────────
@router.patch("/{actividad_id}/calificar-masivo")
async def calificar_masivo(
    actividad_id: str,
    items: list[CalificarMasivoItem],
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    sub = current_user.get("sub")
    user_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :s"), {"s": sub}
    )
    user_id = user_row.scalar()

    actualizados = 0
    for item in items:
        r = await db.execute(
            text("""
                UPDATE ades_tareas_entregas
                   SET calificacion_obtenida = :cal,
                       comentario_profesor = :com,
                       calificado_por = :uid,
                       fecha_calificacion_docente = now(),
                       estatus_entrega = 'CALIFICADA',
                       fcmodificacion = now(),
                       row_version = row_version + 1
                 WHERE tarea_id = :tid AND estudiante_id = :eid
            """),
            {
                "cal": item.calificacion,
                "com": item.comentario,
                "uid": user_id,
                "tid": actividad_id,
                "eid": item.alumno_id,
            },
        )
        actualizados += r.rowcount

    await db.commit()
    return {"actualizados": actualizados}

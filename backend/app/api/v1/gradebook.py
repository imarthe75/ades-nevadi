"""
Gradebook — Calificaciones de período con desglose por ítem, ajustes manuales
y recálculo bajo demanda. Complementa al módulo calificaciones.py existente.
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user
from typing import Optional
from pydantic import BaseModel, field_validator

router = APIRouter(prefix="/gradebook", tags=["Gradebook – Calificaciones"])

MIN_JUSTIFICACION = 20


class AjusteIn(BaseModel):
    ajuste_manual: float
    justificacion_ajuste: str

    @field_validator("justificacion_ajuste")
    @classmethod
    def justificacion_minima(cls, v):
        if len(v.strip()) < MIN_JUSTIFICACION:
            raise ValueError(
                f"La justificación debe tener al menos {MIN_JUSTIFICACION} caracteres"
            )
        return v.strip()


# ── GET /gradebook/periodo/{periodo_id}/grupo/{grupo_id} ──────
@router.get("/periodo/{periodo_id}/grupo/{grupo_id}")
async def tabla_calificaciones_grupo(
    periodo_id: str,
    grupo_id: str,
    materia_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """
    Tabla completa: todos los alumnos × materias del grupo
    con desglose de score_por_item y calificación final.
    """
    filters = "WHERE i.grupo_id = :gid AND i.is_active = TRUE"
    params: dict = {"gid": grupo_id, "pid": periodo_id}
    if materia_id:
        filters += " AND cp.materia_id = :mid"
        params["mid"] = materia_id

    rows = await db.execute(
        text(f"""
            SELECT p.nombre, p.apellido_paterno, p.apellido_materno,
                   est.matricula,
                   cp.materia_id,
                   m.nombre_materia,
                   cp.score_por_item,
                   cp.calificacion_calculada,
                   cp.ajuste_manual,
                   cp.calificacion_final,
                   cp.cerrada,
                   cp.fecha_calculo,
                   cp.id AS cal_periodo_id,
                   ne.escala_maxima,
                   ne.minimo_aprobatorio
              FROM ades_inscripciones i
              JOIN ades_estudiantes est ON est.id = i.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
              LEFT JOIN ades_calificaciones_periodo cp
                     ON cp.estudiante_id = i.estudiante_id
                    AND cp.grupo_id = i.grupo_id
                    AND cp.periodo_evaluacion_id = :pid
              LEFT JOIN ades_materias m ON m.id = cp.materia_id
              LEFT JOIN ades_grados gr ON gr.id = (
                  SELECT g.grado_id FROM ades_grupos g WHERE g.id = i.grupo_id
              )
              LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
             {filters}
             ORDER BY p.apellido_paterno, p.nombre, m.nombre_materia
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── GET /gradebook/alumno/{alumno_id}/boleta ─────────────────
@router.get("/alumno/{alumno_id}/boleta")
async def boleta_alumno(
    alumno_id: str,
    periodo_id: Optional[str] = None,
    ciclo_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    filters = "WHERE cp.estudiante_id = :eid AND cp.is_active = TRUE"
    params: dict = {"eid": alumno_id}
    if periodo_id:
        filters += " AND cp.periodo_evaluacion_id = :pid"
        params["pid"] = periodo_id
    if ciclo_id:
        filters += " AND pe.ciclo_escolar_id = :cid"
        params["cid"] = ciclo_id

    rows = await db.execute(
        text(f"""
            SELECT m.nombre_materia,
                   pe.nombre_periodo, pe.numero_periodo,
                   cp.score_por_item,
                   cp.calificacion_calculada,
                   cp.ajuste_manual,
                   cp.calificacion_final,
                   cp.cerrada,
                   ne.escala_maxima,
                   ne.minimo_aprobatorio,
                   (cp.calificacion_final >= ne.minimo_aprobatorio) AS acreditado
              FROM ades_calificaciones_periodo cp
              JOIN ades_materias m ON m.id = cp.materia_id
              JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
              JOIN ades_grados gr ON gr.id = (
                  SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id
              )
              JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
             {filters}
             ORDER BY m.nombre_materia, pe.numero_periodo
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── POST /gradebook/{cal_periodo_id}/ajuste-manual ───────────
@router.post("/{cal_periodo_id}/ajuste-manual")
async def ajuste_manual(
    cal_periodo_id: str,
    body: AjusteIn,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    # Verificar que no esté cerrada (solo ADMIN puede modificar cerradas)
    row = await db.execute(
        text("SELECT cerrada, calificacion_calculada FROM ades_calificaciones_periodo WHERE id = :id AND is_active = TRUE"),
        {"id": cal_periodo_id},
    )
    cal = row.fetchone()
    if not cal:
        raise HTTPException(404, "Registro de calificación no encontrado")
    if cal.cerrada:
        roles = current_user.get("roles", [])
        if "ADMIN_GLOBAL" not in roles and "ADMIN_PLANTEL" not in roles:
            raise HTTPException(403, "Calificación cerrada. Solo ADMIN puede modificarla.")

    cal_base = float(cal.calificacion_calculada or 0)
    cal_final = round(cal_base + body.ajuste_manual, 2)

    await db.execute(
        text("""
            UPDATE ades_calificaciones_periodo
               SET ajuste_manual         = :aj,
                   justificacion_ajuste  = :just,
                   calificacion_final    = :cal_final,
                   fecha_modificacion    = now(),
                   row_version           = row_version + 1
             WHERE id = :id
        """),
        {"aj": body.ajuste_manual, "just": body.justificacion_ajuste,
         "cal_final": cal_final, "id": cal_periodo_id},
    )
    await db.commit()
    return {"message": "Ajuste aplicado", "calificacion_final": cal_final}


# ── POST /gradebook/periodo/{periodo_id}/recalcular-todo ──────
@router.post("/periodo/{periodo_id}/recalcular-todo")
async def recalcular_periodo(
    periodo_id: str,
    grupo_id: Optional[str] = None,
    materia_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Recalcula todas las calificaciones de un período completo."""
    filters = "WHERE i.is_active = TRUE AND g.is_active = TRUE"
    params: dict = {"pid": periodo_id}
    if grupo_id:
        filters += " AND i.grupo_id = :gid"
        params["gid"] = grupo_id

    # Obtener todos los combos alumno/grupo/materia del período
    combos = await db.execute(
        text(f"""
            SELECT DISTINCT i.estudiante_id, i.grupo_id,
                   ad.materia_id
              FROM ades_inscripciones i
              JOIN ades_grupos g ON g.id = i.grupo_id
              JOIN ades_asignaciones_docentes ad ON ad.grupo_id = i.grupo_id
             WHERE ad.ciclo_escolar_id = (
                     SELECT ciclo_escolar_id FROM ades_periodos_evaluacion
                      WHERE id = :pid
                 )
               {filters.replace('WHERE', 'AND')}
             ORDER BY i.grupo_id, i.estudiante_id
        """),
        params,
    )

    filas = combos.fetchall()
    if materia_id:
        filas = [c for c in filas if str(c.materia_id) == materia_id]

    if not filas:
        return {"recalculados": 0}

    # Recalcular en bloque usando unnest — evita N+1 queries
    eids = [str(c.estudiante_id) for c in filas]
    gids = [str(c.grupo_id)      for c in filas]
    mids = [str(c.materia_id)    for c in filas]
    pids = [periodo_id] * len(filas)

    await db.execute(
        text("""
            SELECT calcular_calificacion_periodo(e::uuid, g::uuid, m::uuid, p::uuid)
              FROM unnest(:eids, :gids, :mids, :pids)
                     AS t(e text, g text, m text, p text)
        """),
        {"eids": eids, "gids": gids, "mids": mids, "pids": pids},
    )
    await db.commit()
    return {"recalculados": len(filas)}


# ── POST /gradebook/{cal_periodo_id}/cerrar ──────────────────
@router.post("/{cal_periodo_id}/cerrar")
async def cerrar_calificacion(
    cal_periodo_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Cierra el período. Solo DIRECTOR/COORD_ACADEMICO/ADMIN."""
    roles = current_user.get("roles", [])
    permitidos = {"ADMIN_GLOBAL", "ADMIN_PLANTEL", "DIRECTOR", "COORDINADOR_ACADEMICO"}
    if not any(r in permitidos for r in roles):
        raise HTTPException(403, "Sin permiso para cerrar períodos")

    await db.execute(
        text("""
            UPDATE ades_calificaciones_periodo
               SET cerrada = TRUE, fecha_cierre = now(),
                   fecha_modificacion = now()
             WHERE id = :id AND cerrada = FALSE
        """),
        {"id": cal_periodo_id},
    )
    await db.commit()
    return {"message": "Período cerrado"}


# ── GET /gradebook/grupo/{grupo_id}/concentrado ───────────────
@router.get("/grupo/{grupo_id}/concentrado")
async def concentrado_grupo(
    grupo_id: str,
    periodo_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Reporte concentrado: alumnos × materias, promedio del grupo, alumnos en riesgo."""
    rows = await db.execute(
        text("""
            SELECT p.nombre || ' ' || p.apellido_paterno AS alumno,
                   est.matricula,
                   cp.materia_id,
                   m.nombre_materia,
                   cp.calificacion_final,
                   ne.minimo_aprobatorio,
                   (cp.calificacion_final < ne.minimo_aprobatorio) AS en_riesgo
              FROM ades_calificaciones_periodo cp
              JOIN ades_estudiantes est ON est.id = cp.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
              JOIN ades_materias m ON m.id = cp.materia_id
              JOIN ades_grados gr ON gr.id = (
                  SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id
              )
              JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
             WHERE cp.grupo_id = :gid
               AND cp.periodo_evaluacion_id = :pid
               AND cp.is_active = TRUE
             ORDER BY p.apellido_paterno, m.nombre_materia
        """),
        {"gid": grupo_id, "pid": periodo_id},
    )
    data = [dict(r._mapping) for r in rows.fetchall()]

    # Promedio por materia
    materias: dict = {}
    for row in data:
        mn = row["nombre_materia"]
        if mn not in materias:
            materias[mn] = {"calificaciones": [], "en_riesgo": 0}
        if row["calificacion_final"] is not None:
            materias[mn]["calificaciones"].append(float(row["calificacion_final"]))
        if row["en_riesgo"]:
            materias[mn]["en_riesgo"] += 1

    promedios = {
        m: {
            "promedio": round(sum(v["calificaciones"]) / len(v["calificaciones"]), 2)
            if v["calificaciones"]
            else None,
            "en_riesgo": v["en_riesgo"],
        }
        for m, v in materias.items()
    }

    return {"detalle": data, "promedios_por_materia": promedios}


# ── GET /gradebook/grupo/{grupo_id}/cobertura-curricular ──────
@router.get("/grupo/{grupo_id}/cobertura-curricular")
async def cobertura_curricular(
    grupo_id: str,
    materia_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Temas del programa: cuáles tienen actividades registradas vs. sin evidencia."""
    filters = "WHERE tm.is_active = TRUE"
    params: dict = {"gid": grupo_id}
    if materia_id:
        filters += " AND tm.materia_id = :mid"
        params["mid"] = materia_id

    rows = await db.execute(
        text(f"""
            SELECT tm.id, tm.nombre_tema, tm.orden,
                   m.nombre_materia,
                   COUNT(t.id) AS num_actividades,
                   COUNT(t.id) > 0 AS tiene_evidencia
              FROM ades_temas tm
              JOIN ades_materias m ON m.id = tm.materia_id
              LEFT JOIN ades_tareas t
                     ON t.tema_id = tm.id AND t.grupo_id = :gid AND t.is_active = TRUE
             {filters}
             GROUP BY tm.id, tm.nombre_tema, tm.orden, m.nombre_materia
             ORDER BY m.nombre_materia, tm.orden
        """),
        params,
    )
    data = [dict(r._mapping) for r in rows.fetchall()]
    total = len(data)
    con_evidencia = sum(1 for r in data if r["tiene_evidencia"])
    return {
        "total_temas": total,
        "con_evidencia": con_evidencia,
        "sin_evidencia": total - con_evidencia,
        "pct_cobertura": round(con_evidencia / total * 100, 1) if total else 0,
        "temas": data,
    }

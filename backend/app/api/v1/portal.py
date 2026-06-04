"""
Portal del Alumno — vista 360° del expediente académico.
Endpoints de solo lectura; usados por el rol ALUMNO y PADRE_FAMILIA.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/portal", tags=["portal"])


async def _ciclo_activo(db: AsyncSession, estudiante_id: str) -> str | None:
    r = await db.execute(text("""
        SELECT ins.ciclo_escolar_id FROM ades_inscripciones ins
         WHERE ins.estudiante_id = :eid
         ORDER BY ins.fccreacion DESC LIMIT 1
    """), {"eid": estudiante_id})
    row = r.fetchone()
    return str(row[0]) if row else None


# ── Búsqueda rápida de alumnos (declarada antes de /{estudiante_id}/...) ──────

@router.get("/buscar")
async def buscar_alumnos_portal(
    q: str = Query(..., min_length=2),
    plantel_id: str | None = None,
    ciclo_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    params: dict = {"q": f"%{q}%"}
    extra_filters = []
    if plantel_id:
        extra_filters.append("AND gr.plantel_id = :plantel_id")
        params["plantel_id"] = plantel_id
    if ciclo_id:
        extra_filters.append("AND ins.ciclo_escolar_id = :ciclo_id")
        params["ciclo_id"] = ciclo_id
    extra = " ".join(extra_filters)

    rows = await db.execute(text(f"""
        SELECT DISTINCT e.id, e.matricula,
               p.nombre, p.apellido_paterno, p.apellido_materno,
               g.nombre_grupo,
               pl.nombre  AS nombre_plantel,
               ne.nombre  AS nivel
          FROM ades_estudiantes       e
          JOIN ades_personas          p   ON p.id  = e.persona_id
          JOIN ades_inscripciones     ins ON ins.estudiante_id = e.id
          JOIN ades_grupos            g   ON g.id  = ins.grupo_id
          JOIN ades_grados            gr  ON gr.id = g.grado_id
          JOIN ades_planteles         pl  ON pl.id = gr.plantel_id
          JOIN ades_niveles_educativos ne  ON ne.id = gr.nivel_educativo_id
         WHERE e.is_active = TRUE
           AND (
               p.nombre           ILIKE :q
            OR p.apellido_paterno ILIKE :q
            OR p.apellido_materno ILIKE :q
            OR e.matricula        ILIKE :q
            OR (p.nombre || ' ' || p.apellido_paterno) ILIKE :q
           )
           {extra}
         ORDER BY p.apellido_paterno, p.nombre
         LIMIT 20
    """), params)
    return [dict(r._mapping) for r in rows.fetchall()]


# ── Resumen 360° ──────────────────────────────────────────────────────────────

@router.get("/{estudiante_id}/resumen")
async def resumen_alumno(
    estudiante_id: str,
    ciclo_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    ciclo_ref = ciclo_id or await _ciclo_activo(db, estudiante_id)
    if not ciclo_ref:
        raise HTTPException(404, "Alumno sin inscripciones")

    # Datos personales + contexto académico
    alumno_row = await db.execute(text("""
        SELECT e.id, e.matricula, e.fecha_ingreso,
               p.nombre, p.apellido_paterno, p.apellido_materno, p.foto_url,
               p.fecha_nacimiento, p.genero,
               g.nombre_grupo,
               ne.nombre  AS nivel,
               pl.nombre  AS plantel,
               ce.nombre  AS ciclo
          FROM ades_estudiantes       e
          JOIN ades_personas          p   ON p.id  = e.persona_id
          JOIN ades_inscripciones     ins ON ins.estudiante_id = e.id
                                        AND ins.ciclo_escolar_id = :cid
          JOIN ades_grupos            g   ON g.id  = ins.grupo_id
          JOIN ades_grados            gr  ON gr.id = g.grado_id
          JOIN ades_planteles         pl  ON pl.id = gr.plantel_id
          JOIN ades_niveles_educativos ne  ON ne.id = gr.nivel_educativo_id
          JOIN ades_ciclos_escolares  ce  ON ce.id = ins.ciclo_escolar_id
         WHERE e.id = :eid AND e.is_active = TRUE
         LIMIT 1
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    alumno = alumno_row.fetchone()
    if not alumno:
        raise HTTPException(404, "Alumno no encontrado en el ciclo indicado")

    nombre_completo = (
        f"{alumno.nombre} {alumno.apellido_paterno}"
        f" {alumno.apellido_materno or ''}".strip()
    )

    # KPI: promedio general
    prom_row = await db.execute(text("""
        SELECT ROUND(AVG(cp.calificacion_final), 2) AS promedio
          FROM ades_calificaciones_periodo cp
          JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
         WHERE cp.estudiante_id = :eid AND pe.ciclo_escolar_id = :cid
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    promedio = float(prom_row.scalar() or 0)

    # KPI: % asistencia — join via grupos inscrito
    asist_row = await db.execute(text("""
        SELECT COUNT(a.id)                                                          AS total,
               COUNT(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 END)       AS presentes
          FROM ades_asistencias a
          JOIN ades_clases cl ON cl.id = a.clase_id
         WHERE a.estudiante_id = :eid
           AND cl.grupo_id IN (
               SELECT grupo_id FROM ades_inscripciones
                WHERE estudiante_id = :eid AND ciclo_escolar_id = :cid
           )
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    ar = asist_row.fetchone()
    total_cl = int(ar.total or 0)
    pct_asistencia = round(100 * int(ar.presentes or 0) / total_cl, 1) if total_cl else 0

    # KPI: tareas pendientes
    pendientes_row = await db.execute(text("""
        SELECT COUNT(*) AS cnt
          FROM ades_tareas t
         WHERE t.is_active = TRUE
           AND t.grupo_id IN (
               SELECT grupo_id FROM ades_inscripciones
                WHERE estudiante_id = :eid AND ciclo_escolar_id = :cid
           )
           AND NOT EXISTS (
               SELECT 1 FROM ades_tareas_entregas te
                WHERE te.tarea_id = t.id AND te.estudiante_id = :eid
           )
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    tareas_pendientes = int(pendientes_row.scalar() or 0)

    # KPI: badges
    badges_cnt = await db.execute(text("""
        SELECT COUNT(*) FROM ades_badge_otorgados
         WHERE estudiante_id = :eid AND ciclo_id = :cid
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    badges_count = int(badges_cnt.scalar() or 0)

    # Alertas activas
    alertas_rows = await db.execute(text("""
        SELECT tipo_alerta, nivel_riesgo, descripcion
          FROM ades_alertas_academicas
         WHERE estudiante_id = :eid AND atendida = FALSE AND is_active = TRUE
         ORDER BY nivel_riesgo DESC LIMIT 5
    """), {"eid": estudiante_id})
    alertas = [dict(r._mapping) for r in alertas_rows.fetchall()]

    # Badges obtenidos
    badges_rows = await db.execute(text("""
        SELECT b.nombre, b.icono, b.color, b.tipo, o.fecha_otorgado, o.motivo
          FROM ades_badge_otorgados o
          JOIN ades_badges b ON b.id = o.badge_id
         WHERE o.estudiante_id = :eid AND o.ciclo_id = :cid
         ORDER BY o.fecha_otorgado DESC
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    badges = [dict(r._mapping) for r in badges_rows.fetchall()]

    # Learning paths
    lp_rows = await db.execute(text("""
        SELECT lp.nombre, la.pct_completado, la.estatus, la.fcinicio, la.fccompletado
          FROM ades_lp_asignaciones la
          JOIN ades_learning_paths  lp ON lp.id = la.path_id
         WHERE la.estudiante_id = :eid
         ORDER BY la.fccreacion DESC LIMIT 5
    """), {"eid": estudiante_id})
    learning_paths = [dict(r._mapping) for r in lp_rows.fetchall()]

    return {
        "alumno": {
            "id":               estudiante_id,
            "nombre":           nombre_completo,
            "matricula":        alumno.matricula,
            "grupo":            alumno.nombre_grupo,
            "nivel":            alumno.nivel,
            "plantel":          alumno.plantel,
            "ciclo":            alumno.ciclo,
            "foto_url":         alumno.foto_url,
            "fecha_nacimiento": str(alumno.fecha_nacimiento) if alumno.fecha_nacimiento else None,
            "genero":           alumno.genero,
        },
        "kpis": {
            "promedio_general":  promedio,
            "pct_asistencia":    pct_asistencia,
            "tareas_pendientes": tareas_pendientes,
            "badges_count":      badges_count,
        },
        "alertas":        alertas,
        "badges":         badges,
        "learning_paths": learning_paths,
    }


# ── Calificaciones detalladas ─────────────────────────────────────────────────

@router.get("/{estudiante_id}/calificaciones")
async def calificaciones_alumno(
    estudiante_id: str,
    ciclo_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    ciclo_ref = ciclo_id or await _ciclo_activo(db, estudiante_id)
    if not ciclo_ref:
        return []

    rows = await db.execute(text("""
        SELECT m.nombre_materia                      AS materia,
               pe.nombre_periodo                     AS periodo,
               pe.numero_periodo                     AS orden,
               cp.calificacion_final                 AS calificacion,
               cp.es_acreditado,
               cp.observaciones
          FROM ades_calificaciones_periodo cp
          JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
          JOIN ades_materias            m  ON m.id  = cp.materia_id
         WHERE cp.estudiante_id = :eid AND pe.ciclo_escolar_id = :cid
         ORDER BY m.nombre_materia, pe.numero_periodo
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    raw = [dict(r._mapping) for r in rows.fetchall()]

    materias: dict[str, dict] = {}
    for r in raw:
        m = r["materia"]
        if m not in materias:
            materias[m] = {"materia": m, "periodos": [], "promedio": 0}
        materias[m]["periodos"].append({
            "periodo":       r["periodo"],
            "orden":         r["orden"],
            "calificacion":  float(r["calificacion"]) if r["calificacion"] else None,
            "acreditado":    r["es_acreditado"],
            "observaciones": r["observaciones"],
        })

    for v in materias.values():
        cals = [p["calificacion"] for p in v["periodos"] if p["calificacion"] is not None]
        v["promedio"] = round(sum(cals) / len(cals), 2) if cals else 0

    return list(materias.values())


# ── Asistencias detalladas ────────────────────────────────────────────────────

@router.get("/{estudiante_id}/asistencias")
async def asistencias_alumno(
    estudiante_id: str,
    ciclo_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    ciclo_ref = ciclo_id or await _ciclo_activo(db, estudiante_id)
    if not ciclo_ref:
        return {"resumen": {}, "detalle": []}

    grupo_filter = """
        cl.grupo_id IN (
            SELECT grupo_id FROM ades_inscripciones
             WHERE estudiante_id = :eid AND ciclo_escolar_id = :cid
        )
    """

    res = await db.execute(text(f"""
        SELECT COUNT(*)                                                           AS total,
               COUNT(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 END)     AS presentes,
               COUNT(CASE WHEN a.estatus_asistencia = 'AUSENTE'  THEN 1 END)     AS ausentes,
               COUNT(CASE WHEN a.estatus_asistencia = 'TARDE'    THEN 1 END)     AS tardes
          FROM ades_asistencias a
          JOIN ades_clases cl ON cl.id = a.clase_id
         WHERE a.estudiante_id = :eid AND {grupo_filter}
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    rr = res.fetchone()
    total = int(rr.total or 0)
    resumen = {
        "total":          total,
        "presentes":      int(rr.presentes or 0),
        "ausentes":       int(rr.ausentes or 0),
        "tardes":         int(rr.tardes or 0),
        "pct_asistencia": round(100 * int(rr.presentes or 0) / total, 1) if total else 0,
    }

    det = await db.execute(text(f"""
        SELECT cl.fecha_clase                  AS fecha,
               m.nombre_materia                AS materia,
               a.estatus_asistencia            AS estado,
               a.observacion
          FROM ades_asistencias a
          JOIN ades_clases  cl ON cl.id  = a.clase_id
          JOIN ades_materias m  ON m.id  = cl.materia_id
         WHERE a.estudiante_id = :eid AND {grupo_filter}
         ORDER BY cl.fecha_clase DESC
         LIMIT 100
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    return {"resumen": resumen, "detalle": [dict(r._mapping) for r in det.fetchall()]}


# ── Tareas detalladas ─────────────────────────────────────────────────────────

@router.get("/{estudiante_id}/tareas")
async def tareas_alumno(
    estudiante_id: str,
    ciclo_id: str | None = None,
    solo_pendientes: bool = False,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    ciclo_ref = ciclo_id or await _ciclo_activo(db, estudiante_id)
    if not ciclo_ref:
        return []

    pendiente_filter = """
        AND NOT EXISTS (
            SELECT 1 FROM ades_tareas_entregas te
             WHERE te.tarea_id = t.id AND te.estudiante_id = :eid
        )
    """ if solo_pendientes else ""

    rows = await db.execute(text(f"""
        SELECT t.id, t.titulo, t.descripcion,
               m.nombre_materia                AS materia,
               t.fecha_asignacion, t.fecha_entrega, t.puntaje_maximo,
               te.id                           AS entrega_id,
               te.fecha_entrega                AS fecha_entregado,
               te.es_tarde,
               te.estatus_entrega,
               ct.calificacion                 AS calificacion_tarea
          FROM ades_tareas t
          JOIN ades_materias m ON m.id = t.materia_id
          LEFT JOIN ades_tareas_entregas      te ON te.tarea_id = t.id
                                                AND te.estudiante_id = :eid
          LEFT JOIN ades_calificaciones_tareas ct ON ct.tarea_entrega_id = te.id
         WHERE t.is_active = TRUE
           AND t.grupo_id IN (
               SELECT grupo_id FROM ades_inscripciones
                WHERE estudiante_id = :eid AND ciclo_escolar_id = :cid
           )
           {pendiente_filter}
         ORDER BY t.fecha_entrega DESC
         LIMIT 50
    """), {"eid": estudiante_id, "cid": ciclo_ref})
    return [dict(r._mapping) for r in rows.fetchall()]

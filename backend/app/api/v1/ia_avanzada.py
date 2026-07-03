"""
FASE 38 — IA Avanzada

  IA-005  POST /ia-avanzada/prediccion-abandono/{alumno_id} — modelo riesgo abandono
  IA-009  GET  /ia-avanzada/historial-conversaciones        — historial del chatbot IA
  IA-014  GET  /ia-avanzada/recomendaciones/{alumno_id}     — recomendaciones pedagógicas
  IA-015  POST /ia-avanzada/analizar-grupo/{grupo_id}       — análisis grupal con IA
"""
from __future__ import annotations
import json
import logging
import uuid
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.services.llm_service import LLMService, get_llm_service

log = logging.getLogger(__name__)

router = APIRouter(prefix="/ia-avanzada", tags=["ia-avanzada"])

_NIVEL_DOCENTE = 4


# ── IA-005: Predicción de abandono ───────────────────────────────────────────

@router.post("/prediccion-abandono/{alumno_id}")
async def prediccion_abandono(
    alumno_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    # Recopilar indicadores de riesgo desde BD
    indicadores_r = await db.execute(text("""
        SELECT
            -- Asistencia
            ROUND(100.0 * SUM(CASE WHEN a.estatus='PRESENTE' THEN 1 ELSE 0 END) /
                NULLIF(COUNT(a.id),0), 2) as pct_asistencia,
            -- Calificaciones
            ROUND(AVG(c.calificacion_final)::numeric, 2) as promedio_calificaciones,
            -- Incidentes conducta
            (SELECT COUNT(*) FROM ades_incidentes_conducta WHERE alumno_id = :aid AND is_active) as incidentes_conducta,
            -- Tareas no entregadas
            (SELECT COUNT(*) FROM ades_entregas e2
             JOIN ades_actividades act ON act.id = e2.actividad_id
             WHERE act.tipo IN ('TAREA','PROYECTO') AND e2.estado = 'NO_ENTREGADA') as tareas_pendientes,
            -- Justificaciones pendientes
            (SELECT COUNT(*) FROM ades_justificaciones j
             JOIN ades_asistencias asj ON asj.id = j.asistencia_id
             JOIN ades_inscripciones i2 ON i2.id = asj.inscripcion_id
             WHERE i2.estudiante_id = :aid AND j.estado = 'PENDIENTE') as justificaciones_pendientes
        FROM ades_asistencias a
        JOIN ades_inscripciones i ON i.id = a.inscripcion_id
        LEFT JOIN ades_calificaciones c ON c.inscripcion_id = i.id
        WHERE i.estudiante_id = :aid AND i.is_active
    """), {"aid": str(alumno_id)})

    indicadores = dict(indicadores_r.fetchone()._mapping)

    # Algoritmo heurístico de riesgo (sin ML externo)
    score = 0
    pct_asist = float(indicadores.get("pct_asistencia") or 100)
    promedio = float(indicadores.get("promedio_calificaciones") or 10)
    incidentes = int(indicadores.get("incidentes_conducta") or 0)

    if pct_asist < 70:  score += 40
    elif pct_asist < 80: score += 20
    elif pct_asist < 90: score += 10

    if promedio < 6:    score += 35
    elif promedio < 7:  score += 20
    elif promedio < 8:  score += 5

    if incidentes >= 3: score += 25
    elif incidentes >= 1: score += 10

    riesgo = "ALTO" if score >= 60 else "MEDIO" if score >= 30 else "BAJO"

    # Guardar evaluación
    await db.execute(text("""
        INSERT INTO ades_evaluaciones_riesgo
          (alumno_id, score_riesgo, nivel_riesgo, indicadores_json,
           usuario_creacion, usuario_modificacion)
        VALUES (:aid, :score, :riesgo, :indicadores::jsonb, :uname, :uname)
        ON CONFLICT (alumno_id) DO UPDATE SET
          score_riesgo = EXCLUDED.score_riesgo,
          nivel_riesgo = EXCLUDED.nivel_riesgo,
          indicadores_json = EXCLUDED.indicadores_json,
          usuario_modificacion = EXCLUDED.usuario_modificacion
    """), {
        "aid": str(alumno_id), "score": score, "riesgo": riesgo,
        "indicadores": str(indicadores).replace("'", '"'), "uname": user.id,
    })
    await db.commit()

    acciones = []
    if pct_asist < 80:
        acciones.append("Contactar a familia por baja asistencia")
    if promedio < 7:
        acciones.append("Asignar sesiones de tutoría académica")
    if incidentes >= 2:
        acciones.append("Sesión con orientador/psicólogo")

    return {
        "alumno_id": str(alumno_id),
        "score_riesgo": score,
        "nivel_riesgo": riesgo,
        "indicadores": indicadores,
        "acciones_sugeridas": acciones,
    }


@router.get("/prediccion-abandono/{alumno_id}")
async def obtener_prediccion_abandono(
    alumno_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    r = await db.execute(text("""
        SELECT score_riesgo, nivel_riesgo, indicadores_json, fecha_modificacion
        FROM ades_evaluaciones_riesgo
        WHERE alumno_id = :aid
    """), {"aid": str(alumno_id)})
    row = r.fetchone()
    if not row:
        return await prediccion_abandono(alumno_id, user, db)
    
    mapping = row._mapping
    score = mapping["score_riesgo"]
    riesgo = mapping["nivel_riesgo"]
    indicadores = mapping["indicadores_json"]
    
    acciones = []
    pct_asist = float(indicadores.get("pct_asistencia") or 100)
    promedio = float(indicadores.get("promedio_calificaciones") or 10)
    incidentes = int(indicadores.get("incidentes_conducta") or 0)
    if pct_asist < 80:
        acciones.append("Contactar a familia por baja asistencia")
    if promedio < 7:
        acciones.append("Asignar sesiones de tutoría académica")
    if incidentes >= 2:
        acciones.append("Sesión con orientador/psicólogo")
        
    return {
        "alumno_id": str(alumno_id),
        "score_riesgo": score,
        "nivel_riesgo": riesgo,
        "indicadores": indicadores,
        "acciones_sugeridas": acciones,
        "fecha_actualizacion": mapping["fecha_modificacion"].isoformat()
    }


# ── IA-009: Historial de conversaciones ──────────────────────────────────────


@router.get("/historial-conversaciones")
async def historial_conversaciones(
    usuario_id: Optional[str] = None,
    limit: int = Query(50, le=200),
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > 3 and not usuario_id:
        usuario_id = user.id

    filters = ["1=1"]
    params: dict = {"limit": limit}
    if usuario_id:
        filters.append("usuario_id = :uid"); params["uid"] = usuario_id

    r = await db.execute(text(f"""
        SELECT id, usuario_id, mensaje_usuario, respuesta_ia,
               tokens_usados, latencia_ms, fecha_creacion
        FROM ades_conversaciones_ia
        WHERE {" AND ".join(filters)}
        ORDER BY fecha_creacion DESC
        LIMIT :limit
    """), params)
    return [dict(row._mapping) for row in r.fetchall()]


# ── IA-014: Recomendaciones pedagógicas ──────────────────────────────────────

@router.get("/recomendaciones/{alumno_id}")
async def recomendaciones_pedagogicas(
    alumno_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > _NIVEL_DOCENTE:
        raise HTTPException(status_code=403, detail="Acceso denegado")

    # Materias con bajo rendimiento
    bajas_r = await db.execute(text("""
        SELECT m.nombre_materia, AVG(c.calificacion_final) as promedio
        FROM ades_calificaciones c
        JOIN ades_materias m ON m.id = c.materia_id
        JOIN ades_inscripciones i ON i.id = c.inscripcion_id
        WHERE i.estudiante_id = :aid AND i.is_active AND c.calificacion_final IS NOT NULL
        GROUP BY m.nombre_materia
        HAVING AVG(c.calificacion_final) < 7
        ORDER BY promedio
    """), {"aid": str(alumno_id)})
    materias_bajas = [dict(r._mapping) for r in bajas_r.fetchall()]

    # NEE activas
    nee_r = await db.execute(text("""
        SELECT tipo_nee, descripcion, apoyos_requeridos
        FROM ades_nee WHERE alumno_id = :aid AND activa = TRUE
    """), {"aid": str(alumno_id)})
    nees = [dict(r._mapping) for r in nee_r.fetchall()]

    recomendaciones = []
    for m in materias_bajas:
        recomendaciones.append({
            "materia": m["nombre_materia"],
            "tipo": "REFUERZO_ACADEMICO",
            "descripcion": f"Promedio {m['promedio']:.1f} — Se recomienda tutoría diferenciada y actividades de reforzamiento",
            "recursos_sugeridos": ["Khan Academy", "ejercicios de práctica", "asesoría extraclase"],
        })

    for nee in nees:
        recomendaciones.append({
            "materia": "GENERAL",
            "tipo": "ADAPTACION_NEE",
            "descripcion": f"NEE: {nee['tipo_nee']} — {nee['descripcion']}",
            "recursos_sugeridos": [nee.get("apoyos_requeridos", "Consultar con especialista")],
        })

    return {
        "alumno_id": str(alumno_id),
        "recomendaciones": recomendaciones,
        "total": len(recomendaciones),
    }


# ── IA-014: Narrativa de recomendación para una asignación de learning path ──
# Proxeada desde Spring BFF (LearningPathsController.recomendarIa) por asig_id.

_NARRATIVA_FALLBACK = {
    "resumen": "No fue posible generar una recomendación personalizada en este momento.",
    "fortalezas": [],
    "areas_mejora": [],
    "estrategias": ["Continuar con la ruta de aprendizaje asignada a tu propio ritmo."],
    "recursos_priorizados": [],
    "mensaje_motivacional": "¡Sigue adelante, cada paso cuenta!",
}


@router.post("/learning-path-narrativa/{asignacion_id}")
async def learning_path_narrativa(
    asignacion_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
    llm: LLMService = Depends(get_llm_service),
):
    """IA-014: genera una recomendación pedagógica narrativa (resumen, fortalezas,
    áreas de mejora, estrategias, recursos priorizados, mensaje motivacional) para
    una asignación de learning path, usando el progreso registrado como contexto."""
    if user.nivel_acceso > _NIVEL_DOCENTE:
        raise HTTPException(status_code=403, detail="Acceso denegado")

    asig_r = await db.execute(text("""
        SELECT a.id, a.estudiante_id, a.pct_completado, lp.nombre AS path_nombre,
               COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS alumno_nombre
        FROM ades_lp_asignaciones a
        JOIN ades_learning_paths lp ON lp.id = a.path_id
        JOIN ades_estudiantes e ON e.id = a.estudiante_id
        JOIN ades_personas p ON p.id = e.persona_id
        WHERE a.id = :aid AND a.is_active = TRUE
        """), {"aid": str(asignacion_id)})
    asig = asig_r.mappings().first()
    if not asig:
        raise HTTPException(status_code=404, detail="Asignación no encontrada")

    progreso_r = await db.execute(text("""
        SELECT r.titulo, r.tipo, pr.completado, pr.calificacion
        FROM ades_lp_recursos r
        LEFT JOIN ades_lp_progreso pr ON pr.recurso_id = r.id AND pr.asignacion_id = :aid
        WHERE r.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = :aid) AND r.is_active = TRUE
        ORDER BY r.orden
        """), {"aid": str(asignacion_id)})
    recursos = [dict(r._mapping) for r in progreso_r.fetchall()]

    if not llm.available:
        log.warning("LLM no disponible para learning-path-narrativa — usando fallback por reglas")
        return _NARRATIVA_FALLBACK

    contexto = {
        "alumno": asig["alumno_nombre"],
        "ruta": asig["path_nombre"],
        "pct_completado": float(asig["pct_completado"] or 0),
        "recursos": recursos,
    }

    try:
        completion = await llm.async_complete(
            messages=[
                {"role": "system", "content": (
                    "Eres un asesor pedagógico. Responde ÚNICAMENTE con un objeto JSON válido, "
                    "sin texto adicional, con las claves exactas: resumen (string), "
                    "fortalezas (lista de strings), areas_mejora (lista de strings), "
                    "estrategias (lista de strings), recursos_priorizados (lista de strings), "
                    "mensaje_motivacional (string). Tono breve, cálido y accionable para un "
                    "alumno de educación básica/media superior en México."
                )},
                {"role": "user", "content": json.dumps(contexto, ensure_ascii=False, default=str)},
            ],
            temperature=0.6,
            max_tokens=600,
        )
        texto = completion.choices[0].message.content or ""
        inicio, fin = texto.find("{"), texto.rfind("}")
        if inicio == -1 or fin == -1:
            raise ValueError("Respuesta del LLM sin JSON detectable")
        data = json.loads(texto[inicio:fin + 1])
        for clave in _NARRATIVA_FALLBACK:
            data.setdefault(clave, _NARRATIVA_FALLBACK[clave])
        return data
    except Exception as exc:
        log.error("Error generando narrativa IA-014, usando fallback: %s", exc)
        return _NARRATIVA_FALLBACK


# ── IA-015: Análisis grupal ───────────────────────────────────────────────────

@router.post("/analizar-grupo/{grupo_id}")
async def analizar_grupo(
    grupo_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > _NIVEL_DOCENTE:
        raise HTTPException(status_code=403, detail="Acceso denegado")

    stats_r = await db.execute(text("""
        SELECT
            COUNT(DISTINCT i.estudiante_id) as total_alumnos,
            ROUND(AVG(c.calificacion_final)::numeric, 2) as promedio_grupo,
            ROUND(100.0 * SUM(CASE WHEN a.estatus='PRESENTE' THEN 1 ELSE 0 END) /
                NULLIF(COUNT(a.id),0), 2) as pct_asistencia_grupo,
            SUM(CASE WHEN c.calificacion_final < 6 THEN 1 ELSE 0 END) as reprobados,
            SUM(CASE WHEN c.calificacion_final >= 9 THEN 1 ELSE 0 END) as excelentes
        FROM ades_inscripciones i
        LEFT JOIN ades_calificaciones c ON c.inscripcion_id = i.id
        LEFT JOIN ades_asistencias a ON a.inscripcion_id = i.id
        WHERE i.grupo_id = :gid AND i.is_active
    """), {"gid": str(grupo_id)})
    stats = dict(stats_r.fetchone()._mapping)

    # Distribución de calificaciones
    dist_r = await db.execute(text("""
        SELECT
            SUM(CASE WHEN c.calificacion_final < 6 THEN 1 ELSE 0 END) as reprobados,
            SUM(CASE WHEN c.calificacion_final BETWEEN 6 AND 6.9 THEN 1 ELSE 0 END) as suficiente,
            SUM(CASE WHEN c.calificacion_final BETWEEN 7 AND 7.9 THEN 1 ELSE 0 END) as regular,
            SUM(CASE WHEN c.calificacion_final BETWEEN 8 AND 8.9 THEN 1 ELSE 0 END) as bueno,
            SUM(CASE WHEN c.calificacion_final >= 9 THEN 1 ELSE 0 END) as excelente
        FROM ades_inscripciones i
        JOIN ades_calificaciones c ON c.inscripcion_id = i.id
        WHERE i.grupo_id = :gid AND i.is_active AND c.calificacion_final IS NOT NULL
    """), {"gid": str(grupo_id)})
    distribucion = dict(dist_r.fetchone()._mapping)

    pct_asist = float(stats.get("pct_asistencia_grupo") or 0)
    promedio = float(stats.get("promedio_grupo") or 0)

    alertas = []
    if pct_asist < 85:
        alertas.append(f"Asistencia grupal baja: {pct_asist:.1f}%")
    if promedio < 7:
        alertas.append(f"Promedio grupal bajo: {promedio:.2f}")

    return {
        "grupo_id": str(grupo_id),
        "estadisticas": stats,
        "distribucion_calificaciones": distribucion,
        "alertas": alertas,
        "recomendaciones_grupo": [
            "Implementar actividades colaborativas para elevar promedio grupal" if promedio < 7 else "Mantener estrategias didácticas actuales",
            "Revisar causas de ausentismo con prefectura" if pct_asist < 85 else "Reforzar asistencia mediante motivación",
        ],
    }

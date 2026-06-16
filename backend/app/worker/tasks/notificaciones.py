"""
Tareas Celery: notificaciones y mantenimiento de vistas BI.

  - enviar_notificacion_alerta   — encola notificación para un docente/director
  - scan_alertas_todos_grupos    — beat nocturno: escanea todos los grupos activos
  - refresh_vistas_materializadas — beat horario: refresca MATERIALIZED VIEWs de BI
"""
from __future__ import annotations

import logging
import uuid

from celery import shared_task
from sqlalchemy import create_engine, select, text
from sqlalchemy.orm import Session

log = logging.getLogger(__name__)


def _get_db_engine():
    from app.core.config import settings
    return create_engine(settings.DATABASE_URL_SYNC, pool_pre_ping=True)


# ── Notificaciones ────────────────────────────────────────────────────────────

@shared_task(name="app.worker.tasks.notificaciones.enviar_notificacion_alerta")
def enviar_notificacion_alerta(
    alerta_id: str,
    destinatarios: list[str],
    canal: str = "interna",  # "interna" | "email" (Google Workspace relay)
) -> dict:
    """
    Notifica a los destinatarios sobre una alerta académica.

    canal="interna"  → persiste en ades_notificaciones (leída desde la app)
    canal="email"    → envía correo vía SMTP (Google Workspace relay) — FASE futura
    """
    engine = _get_db_engine()
    alerta_uuid = uuid.UUID(alerta_id)

    with Session(engine) as session:
        # Marcar la alerta como notificada
        session.execute(
            text("""
                UPDATE ades_alertas_academicas
                   SET notificada = TRUE,
                       fecha_modificacion = NOW()
                 WHERE id = :id
            """),
            {"id": alerta_uuid},
        )

        if canal == "interna":
            for dest in destinatarios:
                session.execute(
                    text("""
                        INSERT INTO ades_notificaciones
                            (id, usuario_id, tipo, entidad_tipo, entidad_id,
                             titulo, cuerpo, leido, fecha_creacion)
                        VALUES
                            (uuidv7(), :dest::uuid, 'ALERTA', 'ALERTA_ACADEMICA', :ref,
                             'Nueva alerta académica', 'Se detectó una alerta académica que requiere atención.',
                             FALSE, NOW())
                        ON CONFLICT DO NOTHING
                    """),
                    {"dest": dest, "ref": alerta_uuid},
                )

        session.commit()

    log.info("notificacion_enviada alerta=%s canal=%s destinatarios=%d", alerta_id, canal, len(destinatarios))
    return {"estado": "ok", "alerta_id": alerta_id, "canal": canal, "enviadas": len(destinatarios)}


# ── Escaneo nocturno de alertas ───────────────────────────────────────────────

@shared_task(name="app.worker.tasks.notificaciones.scan_alertas_todos_grupos")
def scan_alertas_todos_grupos() -> dict:
    """
    Tarea beat nocturna: para cada grupo activo, detecta alumnos con:
      - promedio < 6.0 en el ciclo vigente (riesgo de reprobación)
      - asistencia < 80 % en el mes actual
    y registra alertas en ades_alertas_academicas (skip si ya existe alerta activa).
    """
    engine = _get_db_engine()
    creadas = 0
    grupos_procesados = 0

    with Session(engine) as session:
        # Grupos activos del ciclo vigente
        grupos = session.execute(
            text("""
                SELECT DISTINCT i.grupo_id
                  FROM ades_inscripciones i
                  JOIN ades_ciclos_escolares c ON c.id = i.ciclo_escolar_id
                 WHERE c.es_vigente = TRUE AND i.is_active = TRUE
            """)
        ).fetchall()

        for (grupo_id,) in grupos:
            grupos_procesados += 1

            # Alumnos en riesgo de reprobación
            reprobacion = session.execute(
                text("""
                    SELECT e.id,
                           ROUND(AVG(cp.calificacion_final)::numeric, 2) AS promedio
                      FROM ades_inscripciones i
                      JOIN ades_estudiantes e ON e.id = i.estudiante_id
                      JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = e.id
                                                          AND cp.grupo_id = i.grupo_id
                     WHERE i.grupo_id = :gid
                       AND i.is_active = TRUE
                     GROUP BY e.id
                    HAVING AVG(cp.calificacion_final) < 6.0
                """),
                {"gid": str(grupo_id)},
            ).fetchall()

            for est_id, promedio in reprobacion:
                # Insertar solo si no hay alerta activa del mismo tipo
                session.execute(
                    text("""
                        INSERT INTO ades_alertas_academicas
                            (id, estudiante_id, grupo_id, tipo_alerta, nivel_riesgo,
                             descripcion, datos_calculo, generada_por, atendida, fecha_creacion)
                        SELECT uuidv7(), :est, :gid, 'RIESGO_REPROBACION',
                               CASE WHEN :prom < 5.0 THEN 'ALTO' ELSE 'MEDIO' END,
                               'Promedio general ' || :prom || ' — riesgo de reprobación',
                               jsonb_build_object('promedio', :prom),
                               'celery-beat', FALSE, NOW()
                         WHERE NOT EXISTS (
                               SELECT 1 FROM ades_alertas_academicas
                                WHERE estudiante_id = :est
                                  AND grupo_id = :gid
                                  AND tipo_alerta = 'RIESGO_REPROBACION'
                                  AND atendida = FALSE
                         )
                    """),
                    {"est": est_id, "gid": grupo_id, "prom": float(promedio)},
                )
                creadas += 1

            # Alumnos con asistencia < 80 % en los últimos 30 días
            # (asistencias se vinculan vía clase_id → ades_clases.fecha_clase)
            ausentismo = session.execute(
                text("""
                    SELECT e.id,
                           ROUND(
                               100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
                               / NULLIF(COUNT(a.id), 0)
                           , 1) AS pct_asistencia
                      FROM ades_inscripciones i
                      JOIN ades_estudiantes e ON e.id = i.estudiante_id
                      JOIN ades_clases cl ON cl.grupo_id = i.grupo_id
                      JOIN ades_asistencias a ON a.estudiante_id = e.id
                                             AND a.clase_id = cl.id
                                             AND cl.fecha_clase >= CURRENT_DATE - INTERVAL '30 days'
                     WHERE i.grupo_id = :gid AND i.is_active = TRUE
                     GROUP BY e.id
                    HAVING 100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
                           / NULLIF(COUNT(a.id), 0) < 80
                """),
                {"gid": str(grupo_id)},
            ).fetchall()

            for est_id, pct in ausentismo:
                session.execute(
                    text("""
                        INSERT INTO ades_alertas_academicas
                            (id, estudiante_id, grupo_id, tipo_alerta, nivel_riesgo,
                             descripcion, datos_calculo, generada_por, atendida, fecha_creacion)
                        SELECT uuidv7(), :est, :gid, 'AUSENTISMO_CRITICO',
                               CASE WHEN :pct < 70 THEN 'ALTO' ELSE 'MEDIO' END,
                               'Asistencia ' || :pct || '% en los últimos 30 días',
                               jsonb_build_object('pct_asistencia', :pct),
                               'celery-beat', FALSE, NOW()
                         WHERE NOT EXISTS (
                               SELECT 1 FROM ades_alertas_academicas
                                WHERE estudiante_id = :est
                                  AND grupo_id = :gid
                                  AND tipo_alerta = 'AUSENTISMO_CRITICO'
                                  AND atendida = FALSE
                         )
                    """),
                    {"est": est_id, "gid": grupo_id, "pct": float(pct or 0)},
                )
                creadas += 1

        session.commit()

    log.info("scan_alertas_ok grupos=%d alertas_creadas=%d", grupos_procesados, creadas)
    return {"grupos_procesados": grupos_procesados, "alertas_creadas": creadas}


# ── Refresco de vistas materializadas BI ─────────────────────────────────────

@shared_task(name="app.worker.tasks.notificaciones.refresh_vistas_materializadas")
def refresh_vistas_materializadas() -> dict:
    """
    Refresca CONCURRENTLY todas las vistas materializadas del esquema ades_bi.
    Se ejecuta cada hora desde celery-beat para mantener los datos de Superset frescos.
    CONCURRENT permite lectura durante el refresco (no bloquea).
    """
    VISTAS = [
        "ades_bi.mv_asistencia_diaria",
        "ades_bi.mv_calificaciones_grupo",
        "ades_bi.mv_riesgo_academico",
        "ades_bi.mv_resumen_plantel",
        "ades_bi.mv_cobertura_curricular",
        # Schema public — índices únicos en mig 078
        "public.v_asistencias_resumen",
        "public.v_tareas_entregas_resumen",
    ]

    engine = _get_db_engine()
    refrescadas = []
    errores = []

    with Session(engine) as session:
        for vista in VISTAS:
            try:
                session.execute(text(f"REFRESH MATERIALIZED VIEW CONCURRENTLY {vista}"))
                session.commit()
                refrescadas.append(vista)
            except Exception as exc:
                session.rollback()
                log.warning("refresh_vista_error vista=%s error=%s", vista, exc)
                errores.append({"vista": vista, "error": str(exc)})

    log.info("refresh_vistas_ok refrescadas=%d errores=%d", len(refrescadas), len(errores))
    return {"refrescadas": refrescadas, "errores": errores}

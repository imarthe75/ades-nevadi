"""
Celery application — ADES worker.

Broker/backend: Valkey (Redis-compatible) en DB 0.
Tareas registradas:
  - app.worker.tasks.boletas.*
  - app.worker.tasks.notificaciones.*
"""
from __future__ import annotations

from celery import Celery
from celery.schedules import crontab

from app.core.config import settings

celery_app = Celery(
    "ades",
    broker=settings.CELERY_BROKER_URL,
    backend=settings.CELERY_RESULT_URL,
    include=[
        "app.worker.tasks.boletas",
        "app.worker.tasks.notificaciones",
        "app.worker.tasks.blockchain",
        "app.worker.tasks.sepomex",
        "app.worker.tasks.ocr",
    ],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="America/Mexico_City",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    result_expires=86400,          # resultados disponibles 24 h
    task_soft_time_limit=300,      # 5 min advertencia
    task_time_limit=600,           # 10 min límite duro
    # redbeat — beat scheduler Valkey-backed
    redbeat_redis_url=settings.CELERY_BROKER_URL,
    redbeat_key_prefix="ades:celery:beat:",
    redbeat_lock_timeout=5 * 60,   # 5 min — evita lock muerto si beat se cae
)

# ── Tareas programadas (beat) ─────────────────────────────────────────────────
celery_app.conf.beat_schedule = {
    # Escaneo nocturno de alertas académicas — todos los grupos activos
    "scan-alertas-academicas-noche": {
        "task": "app.worker.tasks.notificaciones.scan_alertas_todos_grupos",
        "schedule": crontab(hour=23, minute=0),
    },
    # Refresco de vistas materializadas BI — cada hora
    "refresh-vistas-bi": {
        "task": "app.worker.tasks.notificaciones.refresh_vistas_materializadas",
        "schedule": crontab(minute=5),   # XX:05 de cada hora
    },
    # Sincronización semanal de SEPOMEX — Domingos a las 3:00 AM
    "sync-sepomex-semanal": {
        "task": "app.worker.tasks.sepomex.sync_sepomex_weekly",
        "schedule": crontab(hour=3, minute=0, day_of_week=0),
    },
}

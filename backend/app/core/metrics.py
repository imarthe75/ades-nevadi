"""
FASE 22 / FASE 35 — Prometheus metrics para FastAPI ADES.

Expone /metrics con:
  - http_requests_total{method,handler,status_code}
  - http_request_duration_seconds (histograma)
  - http_requests_in_progress
  - ades_database_size_bytes (tamaño de la base de datos PostgreSQL)
  - ades_minio_size_bytes (tamaño consumido en MinIO)
  - ades_active_sessions (usuarios activos concurrentes en Valkey)
"""

from __future__ import annotations

import logging
from fastapi import FastAPI, Request
from prometheus_client import Gauge

log = logging.getLogger(__name__)

# Gauges de telemetría de servidor
db_size_gauge = Gauge("ades_database_size_bytes", "Total size of the ADES PostgreSQL database in bytes")
minio_size_gauge = Gauge("ades_minio_size_bytes", "Total size of the ADES MinIO storage bucket in bytes")
active_sessions_gauge = Gauge("ades_active_sessions", "Number of concurrent active sessions in the last 15 minutes")


async def update_custom_metrics(db_session, settings) -> None:
    """Calcula y actualiza los valores de los Gauges de telemetría."""
    # 1. DB Size
    try:
        from sqlalchemy import text
        res = await db_session.execute(text("SELECT pg_database_size(current_database())"))
        db_size = res.scalar_one()
        db_size_gauge.set(db_size)
    except Exception as e:
        log.error("Error updating db_size_gauge: %s", e)

    # 2. MinIO Size
    try:
        from minio import Minio
        client = Minio(
            endpoint=settings.MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            secure=settings.MINIO_SECURE,
        )
        if client.bucket_exists(settings.MINIO_BUCKET):
            objects = client.list_objects(settings.MINIO_BUCKET, recursive=True)
            total_size = 0
            for obj in objects:
                total_size += obj.size
            minio_size_gauge.set(total_size)
        else:
            minio_size_gauge.set(0)
    except Exception as e:
        log.error("Error updating minio_size_gauge: %s", e)

    # 3. Valkey active sessions
    try:
        import redis.asyncio as aioredis
        r = await aioredis.from_url(settings.VALKEY_URL)
        keys = await r.keys("ades:session:*")
        active_sessions_gauge.set(len(keys))
        await r.aclose()
    except Exception as e:
        log.error("Error updating active_sessions_gauge: %s", e)


def setup_metrics(app: FastAPI) -> None:
    """Configura Prometheus instrumentator y middleware en la app FastAPI."""
    try:
        from prometheus_fastapi_instrumentator import Instrumentator

        Instrumentator(
            should_group_status_codes=False,
            should_ignore_untemplated=True,
            should_group_untemplated=True,
            excluded_handlers=["/metrics", "/api/v1/health"],
        ).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

        # Middleware para actualizar Gauges antes de que Prometheus lea la respuesta
        @app.middleware("http")
        async def update_prometheus_metrics_middleware(request: Request, call_next):
            if request.url.path == "/metrics":
                try:
                    from app.core.database import AsyncSessionLocal
                    from app.core.config import settings
                    async with AsyncSessionLocal() as db:
                        await update_custom_metrics(db, settings)
                except Exception as e:
                    log.error("Failed to update custom prometheus metrics: %s", e)
            return await call_next(request)

        log.info("Prometheus metrics y telemetría de servidor habilitados en /metrics")
    except ImportError:
        log.warning("prometheus-fastapi-instrumentator no instalado — métricas desactivadas")

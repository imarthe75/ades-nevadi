"""
FASE 22 — Prometheus metrics para FastAPI ADES.

Expone /metrics con:
  - http_requests_total{method,handler,status_code}
  - http_request_duration_seconds (histograma)
  - http_requests_in_progress
  + métricas propias del proceso Python

Uso en main.py:
    from app.core.metrics import setup_metrics
    setup_metrics(app)
"""

from __future__ import annotations

import logging
from fastapi import FastAPI

log = logging.getLogger(__name__)


def setup_metrics(app: FastAPI) -> None:
    """Configura Prometheus instrumentator en la app FastAPI."""
    try:
        from prometheus_fastapi_instrumentator import Instrumentator

        Instrumentator(
            should_group_status_codes=False,
            should_ignore_untemplated=True,
            should_group_untemplated=True,
            excluded_handlers=["/metrics", "/api/v1/health"],
        ).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

        log.info("Prometheus metrics habilitado en /metrics")
    except ImportError:
        log.warning("prometheus-fastapi-instrumentator no instalado — métricas desactivadas")

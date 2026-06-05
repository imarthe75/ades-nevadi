"""
FASE 23 — Automatización (n8n webhooks + triggers).

ADES actúa como fuente de eventos que n8n consume vía webhooks.
n8n actúa como orquestador enviando notificaciones, generando reportes batch, etc.

Endpoints del lado ADES:

  GET  /automations/status               — estado de n8n
  POST /automations/webhook/asistencia   — disparado cuando asistencia < umbral
  POST /automations/webhook/calificacion — disparado cuando calificación < 6.0
  POST /automations/webhook/periodo-cierre — disparado al cerrar periodo de evaluación
  POST /automations/webhook/comunicado   — disparado al publicar comunicado
  GET  /automations/workflows            — lista workflows activos en n8n (proxy)

Autenticación interna: ADES_INTERNAL_API_KEY en header X-ADES-Key

Flujos pre-configurados en n8n (ver infrastructure/n8n/workflows/):
  1. attendance_alert.json   — asistencia < 85% → push a padre vía ntfy
  2. grade_alert.json        — calificación < 6 → push a padre vía ntfy
  3. period_close.json       — periodo cerrado → generar boletas batch + enviar
  4. weekly_report.json      — reporte semanal de asistencia (cron viernes 18h)
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

import httpx
from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel

from app.core.config import settings
from app.core.security import AdesUser, get_ades_user

log = logging.getLogger(__name__)
router = APIRouter(prefix="/automations", tags=["automations"])


def _check_internal_key(x_ades_key: str | None = Header(None, alias="X-ADES-Key")) -> None:
    """Valida la API key interna para endpoints de sistema."""
    if settings.ADES_INTERNAL_API_KEY and x_ades_key != settings.ADES_INTERNAL_API_KEY:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "API key interna requerida")


class AsistenciaAlertPayload(BaseModel):
    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    plantel_id: uuid.UUID
    nombre_alumno: str
    pct_asistencia: float
    inasistencias: int
    padre_usuario_id: uuid.UUID | None = None


class CalificacionAlertPayload(BaseModel):
    estudiante_id: uuid.UUID
    materia: str
    periodo: int
    calificacion: float
    padre_usuario_id: uuid.UUID | None = None
    nombre_alumno: str


class PeriodoCierrePayload(BaseModel):
    ciclo_id: uuid.UUID
    numero_periodo: int
    nivel: str
    plantel_id: uuid.UUID
    total_alumnos: int


class ComunicadoPayload(BaseModel):
    comunicado_id: uuid.UUID
    titulo: str
    tipo: str
    destinatario_ids: list[uuid.UUID] = []


async def _dispatch_n8n(webhook_path: str, payload: dict) -> bool:
    """Dispara un webhook de n8n. Fallo silencioso."""
    if not settings.N8N_WEBHOOK_URL:
        return False
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{settings.N8N_WEBHOOK_URL}/webhook/{webhook_path}",
                json=payload,
            )
            return resp.status_code in (200, 202)
    except Exception as exc:
        log.debug("n8n webhook %s failed: %s", webhook_path, exc)
        return False


# ── Endpoints de status ────────────────────────────────────────────────────────

@router.get("/status")
async def automation_status():
    """Estado del servicio n8n."""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{settings.N8N_URL}/healthz")
            return {
                "n8n_disponible": resp.status_code == 200,
                "n8n_url": settings.N8N_URL,
                "ntfy_url": settings.NTFY_URL,
            }
    except Exception as exc:
        return {"n8n_disponible": False, "error": str(exc)}


# ── Webhooks disparados por ADES ───────────────────────────────────────────────

@router.post("/webhook/asistencia")
async def webhook_asistencia(
    body: AsistenciaAlertPayload,
    _key: None = Depends(_check_internal_key),
):
    """
    Disparado cuando la asistencia de un alumno cae por debajo del 85%.
    n8n recibe el evento y envía push notification al padre vía ntfy.
    """
    dispatched = await _dispatch_n8n("ades-attendance-alert", body.model_dump(mode="json"))
    return {"dispatched": dispatched, "evento": "asistencia_alerta"}


@router.post("/webhook/calificacion")
async def webhook_calificacion(
    body: CalificacionAlertPayload,
    _key: None = Depends(_check_internal_key),
):
    """
    Disparado cuando se registra una calificación reprobatoria (< 6.0).
    n8n notifica al padre con detalle de materia y periodo.
    """
    dispatched = await _dispatch_n8n("ades-grade-alert", body.model_dump(mode="json"))
    return {"dispatched": dispatched, "evento": "calificacion_alerta"}


@router.post("/webhook/periodo-cierre")
async def webhook_periodo_cierre(
    body: PeriodoCierrePayload,
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Disparado manualmente al cerrar un periodo de evaluación.
    n8n inicia el proceso de generación de boletas batch.
    Solo coordinadores o superiores.
    """
    if ades_user.nivel_acceso > 2:
        raise HTTPException(403, "Solo coordinadores o superiores")
    dispatched = await _dispatch_n8n("ades-period-close", body.model_dump(mode="json"))
    return {"dispatched": dispatched, "evento": "periodo_cierre", "periodo": body.numero_periodo}


@router.post("/webhook/comunicado")
async def webhook_comunicado(
    body: ComunicadoPayload,
    _key: None = Depends(_check_internal_key),
):
    """
    Disparado cuando se publica un nuevo comunicado.
    n8n envía push notifications a los destinatarios vía ntfy.
    """
    dispatched = await _dispatch_n8n("ades-new-comunicado", body.model_dump(mode="json"))
    return {"dispatched": dispatched, "evento": "comunicado_publicado"}


# ── Lista de workflows (proxy a n8n API) ──────────────────────────────────────

@router.get("/workflows")
async def listar_workflows(
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Lista los workflows activos en n8n. Solo administradores."""
    if ades_user.nivel_acceso > 1:
        raise HTTPException(403, "Solo administradores")
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(f"{settings.N8N_URL}/api/v1/workflows?active=true")
            if resp.status_code == 200:
                return resp.json()
            return {"workflows": [], "error": f"n8n status {resp.status_code}"}
    except Exception as exc:
        return {"workflows": [], "error": str(exc)}

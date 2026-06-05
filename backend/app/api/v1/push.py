"""
FASE 20 — ntfy Push Notifications.

Endpoints:
  GET  /push/status                      — salud del servicio ntfy
  GET  /push/suscripcion/{usuario_id}    — devuelve URL y token de suscripción
  POST /push/enviar                      — envío manual (admin)
  POST /push/enviar-lote                 — envío a múltiples usuarios (admin/sistema)

La suscripción del browser se hace directamente contra ntfy vía SSE:
  GET https://notify.ades.setag.mx/ades_{usuario_id}/json?token=<token>

Las notificaciones automáticas se disparan desde:
  - calificaciones.py (nueva calificación reprobatoria)
  - asistencias.py   (asistencia < 85%)
  - comunicados.py   (nuevo comunicado publicado)
  - n8n workflows    (batch alerts)
"""

from __future__ import annotations

import asyncio
import uuid
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, status
from app.core.security import AdesUser, get_ades_user
from app.services import push_service

router = APIRouter(prefix="/push", tags=["push"])


class PushPayload(BaseModel):
    usuario_id: uuid.UUID
    titulo: str
    mensaje: str
    prioridad: str = "default"
    tags: list[str] = []
    url: str | None = None


class PushLotePayload(BaseModel):
    usuario_ids: list[uuid.UUID]
    titulo: str
    mensaje: str
    prioridad: str = "default"
    tags: list[str] = []
    url: str | None = None


@router.get("/status")
async def push_status():
    """Estado del servicio ntfy."""
    return await push_service.status()


@router.get("/suscripcion")
async def datos_suscripcion(
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Devuelve los datos necesarios para que el navegador se suscriba al topic del usuario.
    El frontend usa estos datos para escuchar eventos SSE de ntfy.
    """
    from app.core.config import settings
    base = settings.NTFY_URL.replace("http://ades-ntfy:80", "https://notify.ades.setag.mx")
    topic = f"ades_{ades_user.id}"
    return {
        "topic": topic,
        "url_sse": f"{base}/{topic}/sse",
        "url_json": f"{base}/{topic}/json",
        "url_ws":   f"{base}/{topic}/ws",
        "token_requerido": bool(settings.NTFY_ADMIN_TOKEN),
    }


@router.post("/enviar")
async def enviar_push(
    body: PushPayload,
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Envía una notificación push a un usuario específico. Solo admin."""
    if ades_user.nivel_acceso > 2:
        raise HTTPException(status.HTTP_403_FORBIDDEN)
    ok = await push_service.send(
        body.usuario_id, body.titulo, body.mensaje,
        body.prioridad, body.tags, body.url,
    )
    return {"enviado": ok}


@router.post("/enviar-lote")
async def enviar_push_lote(
    body: PushLotePayload,
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Envía notificación push a múltiples usuarios. Solo admin."""
    if ades_user.nivel_acceso > 1:
        raise HTTPException(status.HTTP_403_FORBIDDEN)
    enviados = await push_service.send_batch(
        body.usuario_ids, body.titulo, body.mensaje,
        body.prioridad, body.tags, body.url,
    )
    return {"total": len(body.usuario_ids), "enviados": enviados}

"""
Push Notification Service — FASE 20 (ntfy).

Envía notificaciones push a través de ntfy.sh self-hosted.
Cada usuario tiene un topic dedicado: ades_{usuario_id}

Uso:
    await push_service.send(
        usuario_id=uuid,
        titulo="Calificación registrada",
        mensaje="8.5 en Matemáticas — 1er Bimestre",
        prioridad="default",   # min | low | default | high | urgent
        tags=["calificaciones"],
        url="https://ades.setag.mx/calificaciones",
    )
"""

from __future__ import annotations

import asyncio
import logging
import uuid

import httpx

from app.core.config import settings

log = logging.getLogger(__name__)

_PRIORITY = {"min", "low", "default", "high", "urgent"}


async def send(
    usuario_id: uuid.UUID,
    titulo: str,
    mensaje: str,
    prioridad: str = "default",
    tags: list[str] | None = None,
    url: str | None = None,
) -> bool:
    """
    Envía una push notification a un usuario específico vía ntfy.
    Devuelve True si se envió correctamente, False si falló.
    No lanza excepciones — la notificación es best-effort.
    """
    if not settings.NTFY_URL:
        return False

    topic = f"ades_{usuario_id}"
    headers: dict[str, str] = {
        "Title": titulo[:250],
        "Priority": prioridad if prioridad in _PRIORITY else "default",
        "Content-Type": "text/plain; charset=utf-8",
    }
    if tags:
        headers["Tags"] = ",".join(tags[:10])
    if url:
        headers["Click"] = url

    if settings.NTFY_ADMIN_TOKEN:
        headers["Authorization"] = f"Bearer {settings.NTFY_ADMIN_TOKEN}"

    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.post(
                f"{settings.NTFY_URL}/{topic}",
                content=mensaje.encode("utf-8"),
                headers=headers,
            )
            if resp.status_code not in (200, 201):
                log.warning("ntfy send failed (%s): %s", resp.status_code, resp.text[:200])
                return False
        return True
    except Exception as exc:
        log.debug("ntfy unavailable: %s", exc)
        return False


async def send_batch(
    usuario_ids: list[uuid.UUID],
    titulo: str,
    mensaje: str,
    prioridad: str = "default",
    tags: list[str] | None = None,
    url: str | None = None,
) -> int:
    """
    Envía la misma notificación a múltiples usuarios en paralelo.
    Devuelve el número de envíos exitosos.
    """
    tasks = [
        send(uid, titulo, mensaje, prioridad, tags, url)
        for uid in usuario_ids
    ]
    results = await asyncio.gather(*tasks, return_exceptions=True)
    return sum(1 for r in results if r is True)


async def status() -> dict:
    """Verifica si el servicio ntfy está disponible."""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{settings.NTFY_URL}/v1/health")
            return {"disponible": resp.status_code == 200, "url": settings.NTFY_URL}
    except Exception as exc:
        return {"disponible": False, "url": settings.NTFY_URL, "error": str(exc)}

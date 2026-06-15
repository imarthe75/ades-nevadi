import asyncio
import hashlib
import hmac
import json
import logging
import time
from typing import Any, Dict
import httpx
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

log = logging.getLogger("ades.webhooks")

async def dispatch_webhook(event_type: str, data: Dict[str, Any], db: AsyncSession) -> None:
    """
    Busca webhooks activos registrados para 'event_type' (o '*') y los despacha.
    Inserta logs del resultado en la tabla public.ades_webhook_logs de manera asíncrona.
    """
    try:
        # 1. Obtener webhooks activos
        r = await db.execute(text("""
            SELECT id, url, event_type, secret_token
            FROM public.ades_webhooks
            WHERE is_active = TRUE AND (event_type = :event_type OR event_type = '*')
        """), {"event_type": event_type})
        webhooks = r.fetchall()
        
        if not webhooks:
            log.debug("No active webhooks found for event: %s", event_type)
            return

        # 2. Despachar cada webhook en background
        for wh in webhooks:
            w = dict(wh._mapping)
            asyncio.create_task(_send_webhook_request(
                webhook_id=w["id"],
                url=w["url"],
                event_type=event_type,
                payload=data,
                secret_token=w["secret_token"],
                db_session=db
            ))
    except Exception as e:
        log.error("Error dispatching webhooks for event %s: %s", event_type, str(e))

async def _send_webhook_request(
    webhook_id: str,
    url: str,
    event_type: str,
    payload: Dict[str, Any],
    secret_token: str | None,
    db_session: AsyncSession
) -> None:
    """
    Envía la petición HTTP POST, firma el body si hay token y guarda la traza en la base de datos.
    """
    event_id = str(hashlib.sha256(f"{webhook_id}-{event_type}-{time.time()}".encode()).hexdigest()[:16])
    body_data = {
        "event_id": event_id,
        "event_type": event_type,
        "timestamp": int(time.time()),
        "data": payload
    }
    
    body_str = json.dumps(body_data)
    headers = {
        "Content-Type": "application/json",
        "X-Ades-Event-Id": event_id,
        "X-Ades-Event-Type": event_type
    }
    
    if secret_token:
        # Calcular firma HMAC-SHA256
        signature = hmac.new(
            secret_token.encode("utf-8"),
            body_str.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        headers["X-Ades-Signature"] = signature

    status_code = None
    response_body = None
    exitoso = False
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url, headers=headers, content=body_str)
            status_code = resp.status_code
            response_body = resp.text[:1000]  # Truncar si es muy larga
            exitoso = (200 <= status_code < 300)
    except Exception as e:
        response_body = f"Connection Error: {str(e)}"
        status_code = 0
        exitoso = False
        log.warning("Failed to send webhook to %s: %s", url, str(e))

    # Guardar en log (usar una nueva sesión o ejecutar directo si la sesión sigue activa)
    try:
        # Nota: Como asyncio.create_task corre fuera del loop principal de la request,
        # necesitamos asegurarnos de que la sesión de base de datos no se cierre.
        # Para evitar problemas con la sesión de la request, creamos una conexión síncrona
        # o usamos la existente. Como get_db provee una por request, ejecutamos la query de inserción.
        await db_session.execute(text("""
            INSERT INTO public.ades_webhook_logs
                (webhook_id, event_type, payload, status_code, response_body, intentos, exitoso, fecha_envio)
            VALUES
                (:wh_id, :evt, :payload, :status, :resp, 1, :ok, NOW())
        """), {
            "wh_id": webhook_id,
            "evt": event_type,
            "payload": json.dumps(body_data),
            "status": status_code,
            "resp": response_body,
            "ok": exitoso
        })
        await db_session.commit()
    except Exception as db_exc:
        log.error("Failed to save webhook log to DB: %s", str(db_exc))

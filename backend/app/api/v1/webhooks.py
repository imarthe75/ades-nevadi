import uuid
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser

router = APIRouter(prefix="/webhooks", tags=["webhooks"])

_NIVEL_ADMIN = 2

# ── Schemas ───────────────────────────────────────────────────────────────────

class WebhookCreate(BaseModel):
    url: str = Field(..., max_length=500)
    event_type: str = Field(..., max_length=50)  # ej. ALUMNO_INSCRITO, *
    secret_token: Optional[str] = Field(None, max_length=255)
    is_active: bool = True

class WebhookUpdate(BaseModel):
    url: Optional[str] = Field(None, max_length=500)
    event_type: Optional[str] = Field(None, max_length=50)
    secret_token: Optional[str] = Field(None, max_length=255)
    is_active: Optional[bool] = None

# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_webhooks(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(status_code=403, detail="Solo administradores pueden gestionar webhooks")

    r = await db.execute(text("""
        SELECT id, url, event_type, secret_token, is_active, fecha_creacion
        FROM public.ades_webhooks
        ORDER BY fecha_creacion DESC
    """))
    return [dict(row._mapping) for row in r.fetchall()]

@router.post("", status_code=201)
async def crear_webhook(
    data: WebhookCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(status_code=403, detail="Solo administradores pueden gestionar webhooks")

    r = await db.execute(text("""
        INSERT INTO public.ades_webhooks (url, event_type, secret_token, is_active, usuario_creacion, usuario_modificacion)
        VALUES (:url, :event_type, :secret, :active, :user, :user)
        RETURNING id, url, event_type, is_active
    """), {
        "url": data.url,
        "event_type": data.event_type,
        "secret": data.secret_token,
        "active": data.is_active,
        "user": user.id
    })
    await db.commit()
    return dict(r.fetchone()._mapping)

@router.patch("/{webhook_id}")
async def actualizar_webhook(
    webhook_id: uuid.UUID,
    data: WebhookUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(status_code=403, detail="Solo administradores pueden gestionar webhooks")

    # Verificar existencia
    check = await db.execute(text("SELECT id FROM public.ades_webhooks WHERE id = :id"), {"id": str(webhook_id)})
    if not check.fetchone():
        raise HTTPException(status_code=404, detail="Webhook no encontrado")

    updates = []
    params = {"id": str(webhook_id), "user": user.id}

    if data.url is not None:
        updates.append("url = :url")
        params["url"] = data.url
    if data.event_type is not None:
        updates.append("event_type = :event_type")
        params["event_type"] = data.event_type
    if data.secret_token is not None:
        updates.append("secret_token = :secret")
        params["secret"] = data.secret_token
    if data.is_active is not None:
        updates.append("is_active = :active")
        params["active"] = data.is_active

    if not updates:
        raise HTTPException(status_code=400, detail="No se enviaron campos para actualizar")

    updates.append("usuario_modificacion = :user")
    updates.append("fecha_modificacion = NOW()")

    await db.execute(text(f"""
        UPDATE public.ades_webhooks
        SET {', '.join(updates)}
        WHERE id = :id
    """), params)
    await db.commit()

    return {"message": "Webhook actualizado correctamente"}

@router.delete("/{webhook_id}")
async def eliminar_webhook(
    webhook_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(status_code=403, detail="Solo administradores pueden gestionar webhooks")

    r = await db.execute(text("DELETE FROM public.ades_webhooks WHERE id = :id RETURNING id"), {"id": str(webhook_id)})
    if not r.fetchone():
        raise HTTPException(status_code=404, detail="Webhook no encontrado")

    await db.commit()
    return {"message": "Webhook eliminado correctamente"}

@router.get("/{webhook_id}/logs")
async def ver_logs_webhook(
    webhook_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(status_code=403, detail="Solo administradores pueden ver logs de webhooks")

    r = await db.execute(text("""
        SELECT id, event_type, payload, status_code, response_body, intentos, exitoso, fecha_envio
        FROM public.ades_webhook_logs
        WHERE webhook_id = :id
        ORDER BY fecha_envio DESC
        LIMIT 100
    """), {"id": str(webhook_id)})
    return [dict(row._mapping) for row in r.fetchall()]

"""
/notificaciones — Notificaciones internas del usuario.

  GET  /notificaciones/mis-notificaciones  — lista (filtro solo_no_leidas)
  GET  /notificaciones/no-leidas-count     — contador rápido para badge topbar
  PUT  /notificaciones/{id}/leer           — marcar una notificación leída
  PUT  /notificaciones/leer-todas          — marcar todas leídas
"""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/notificaciones", tags=["notificaciones"])


async def _resolve_usuario_id(db: AsyncSession, jwt_sub: str) -> str | None:
    row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub AND is_active = TRUE"),
        {"sub": jwt_sub},
    )
    r = row.fetchone()
    return str(r[0]) if r else None


@router.get("/no-leidas-count")
async def no_leidas_count(
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)
    if not uid:
        return {"total": 0}
    row = await db.execute(text("""
        SELECT COUNT(*) AS total
        FROM ades_notificaciones
        WHERE usuario_id = :uid::uuid AND leido = FALSE AND is_active = TRUE
    """), {"uid": uid})
    return {"total": row.scalar() or 0}


@router.get("/mis-notificaciones")
async def mis_notificaciones(
    solo_no_leidas: bool = False,
    limit: int = Query(20, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)
    if not uid:
        return []

    extra = "AND leido = FALSE" if solo_no_leidas else ""
    rows = await db.execute(text(f"""
        SELECT id, titulo, cuerpo, tipo, entidad_tipo, entidad_id,
               leido, fecha_leido, canal, fccreacion
        FROM ades_notificaciones
        WHERE usuario_id = :uid::uuid AND is_active = TRUE {extra}
        ORDER BY fccreacion DESC
        LIMIT :limit
    """), {"uid": uid, "limit": limit})
    return rows.mappings().all()


@router.put("/leer-todas")
async def leer_todas(
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)
    if not uid:
        return {"ok": True, "actualizadas": 0}

    result = await db.execute(text("""
        UPDATE ades_notificaciones
        SET leido = TRUE, fecha_leido = NOW()
        WHERE usuario_id = :uid::uuid AND leido = FALSE
    """), {"uid": uid})
    await db.commit()
    return {"ok": True, "actualizadas": result.rowcount}


@router.put("/{notif_id}/leer")
async def marcar_leida(
    notif_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)
    if not uid:
        return {"ok": True}

    await db.execute(text("""
        UPDATE ades_notificaciones
        SET leido = TRUE, fecha_leido = NOW()
        WHERE id = :id::uuid AND usuario_id = :uid::uuid
    """), {"id": str(notif_id), "uid": uid})
    await db.commit()
    return {"ok": True}

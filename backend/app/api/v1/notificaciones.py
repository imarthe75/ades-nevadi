"""
/notificaciones — Notificaciones internas del usuario.
"""
from __future__ import annotations
import uuid as _uuid
from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/notificaciones", tags=["notificaciones"])


async def _get_usuario_id(db: AsyncSession, jwt_sub: str) -> str | None:
    """Resuelve el UUID del usuario ADES a partir del sub del JWT."""
    row = (await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub AND is_active = TRUE"),
        {"sub": jwt_sub},
    )).fetchone()
    return str(row[0]) if row else None


@router.get("/no-leidas-count")
async def no_leidas_count(
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    uid = await _get_usuario_id(db, current_user.get("sub", ""))
    if not uid:
        return {"total": 0}
    row = await db.execute(
        text("SELECT COUNT(*) FROM ades_notificaciones WHERE usuario_id = CAST(:uid AS uuid) AND leido = FALSE AND is_active = TRUE"),
        {"uid": uid},
    )
    return {"total": row.scalar() or 0}


@router.get("/mis-notificaciones")
async def mis_notificaciones(
    solo_no_leidas: bool = False,
    limit: int = Query(20, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    uid = await _get_usuario_id(db, current_user.get("sub", ""))
    if not uid:
        return []
    filtro = "AND leido = FALSE" if solo_no_leidas else ""
    rows = await db.execute(
        text(f"""
            SELECT id, titulo, cuerpo, tipo, leido
            FROM ades_notificaciones
            WHERE usuario_id = CAST(:uid AS uuid) AND is_active = TRUE {filtro}
            ORDER BY fccreacion DESC LIMIT :lim
        """),
        {"uid": uid, "lim": limit},
    )
    return [dict(r._mapping) for r in rows]


@router.put("/leer-todas")
async def leer_todas(
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    uid = await _get_usuario_id(db, current_user.get("sub", ""))
    if not uid:
        return {"ok": True, "actualizadas": 0}
    result = await db.execute(
        text("UPDATE ades_notificaciones SET leido = TRUE, fecha_leido = NOW() WHERE usuario_id = CAST(:uid AS uuid) AND leido = FALSE"),
        {"uid": uid},
    )
    await db.commit()
    return {"ok": True, "actualizadas": result.rowcount}


@router.put("/{notif_id}/leer")
async def marcar_leida(
    notif_id: _uuid.UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    uid = await _get_usuario_id(db, current_user.get("sub", ""))
    if not uid:
        return {"ok": True}
    await db.execute(
        text("UPDATE ades_notificaciones SET leido = TRUE, fecha_leido = NOW() WHERE id = CAST(:id AS uuid) AND usuario_id = CAST(:uid AS uuid)"),
        {"id": str(notif_id), "uid": uid},
    )
    await db.commit()
    return {"ok": True}

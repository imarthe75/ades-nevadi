from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func
from app.models.notificaciones import NotificacionSistema
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
import uuid

router = APIRouter(prefix="/notificaciones", tags=["notificaciones"])


@router.get("/mis-notificaciones")
async def mis_notificaciones(
    solo_no_leidas: bool = False,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    q = select(NotificacionSistema).where(
        NotificacionSistema.usuario_id == user.id
    ).order_by(NotificacionSistema.fecha_creacion.desc()).limit(limit)

    if solo_no_leidas:
        q = q.where(NotificacionSistema.leido == False)

    result = await db.execute(q)
    # The frontend expects 'cuerpo' instead of 'mensaje' based on the notif-item code
    # We will map it here to match the UI interface `Notif { id, titulo, cuerpo, tipo, leido }`
    notificaciones = []
    for r in result.scalars().all():
        notificaciones.append({
            "id": str(r.id),
            "titulo": r.titulo,
            "cuerpo": r.mensaje,
            "tipo": r.tipo,
            "leido": r.leido
        })
    return notificaciones


@router.get("/no-leidas-count")
async def no_leidas_count(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    q = select(func.count(NotificacionSistema.id)).where(
        NotificacionSistema.usuario_id == user.id,
        NotificacionSistema.leido == False
    )
    result = await db.execute(q)
    count = result.scalar() or 0
    return {"total": count}


@router.put("/{notif_id}/leer")
async def marcar_leida(
    notif_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    await db.execute(
        update(NotificacionSistema)
        .where(
            NotificacionSistema.id == notif_id,
            NotificacionSistema.usuario_id == user.id
        )
        .values(leido=True)
    )
    await db.commit()
    return {"ok": True}


@router.put("/leer-todas")
async def marcar_todas_leidas(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    await db.execute(
        update(NotificacionSistema)
        .where(
            NotificacionSistema.usuario_id == user.id,
            NotificacionSistema.leido == False
        )
        .values(leido=True)
    )
    await db.commit()
    return {"ok": True}


# ── Función de utilidad para crear notificaciones desde cualquier parte del backend ──

async def crear_notificacion(
    db: AsyncSession,
    usuario_id: uuid.UUID,
    titulo: str,
    mensaje: str,
    tipo: str = 'INFO',  # INFO | WARN | ERROR | SUCCESS
):
    """
    Crea una notificación in-app para un usuario.
    """
    notif = NotificacionSistema(
        usuario_id=usuario_id,
        titulo=titulo,
        mensaje=mensaje,
        tipo=tipo,
    )
    db.add(notif)
    return notif

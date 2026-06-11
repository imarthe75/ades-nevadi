"""
FASE 15 — Auditoría: endpoints de consulta de ades_audit_log.
Solo accesibles para ADMIN_GLOBAL (nivel_acceso == 0).
"""

from __future__ import annotations

import uuid
from datetime import datetime
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc

from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.models.personas import AuditLog
from app.schemas.base import AdesResponse, AdesSchema

router = APIRouter(prefix="/auditoria", tags=["auditoria"])


class AuditLogOut(AdesResponse):
    usuario_id: uuid.UUID | None = None
    nombre_usuario: str | None = None
    ip_origen: str | None = None
    accion: str
    entidad: str
    entidad_id: uuid.UUID | None = None
    endpoint: str | None = None
    metodo_http: str | None = None
    codigo_respuesta: int | None = None
    duracion_ms: int | None = None
    fecha_creacion: datetime | None = None


@router.get("", response_model=list[AuditLogOut])
async def listar_audit_log(
    limite: int = Query(200, le=500),
    entidad: str | None = None,
    accion: str | None = None,
    usuario_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Últimas N entradas del log de auditoría (solo ADMIN_GLOBAL)."""
    if ades_user.nivel_acceso > 0:
        from fastapi import HTTPException, status
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Solo ADMIN_GLOBAL")

    q = select(AuditLog).order_by(desc(AuditLog.fecha_creacion)).limit(limite)
    if entidad:
        q = q.where(AuditLog.entidad == entidad)
    if accion:
        q = q.where(AuditLog.accion == accion)
    if usuario_id:
        q = q.where(AuditLog.usuario_id == usuario_id)

    rows = (await db.execute(q)).scalars().all()
    return rows

"""
Mixin de auditoría compartido por todos los modelos ADES.
Replica las columnas que el trigger auditoria.fn_auditoria_biu() gestiona.
"""
from __future__ import annotations
import uuid
from datetime import datetime
from sqlalchemy import Boolean, DateTime, Integer, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.database import Base


class AuditMixin:
    """Columnas de auditoría presentes en TODAS las tablas ades_*."""
    ref: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), default=uuid.uuid4, nullable=False, unique=True
    )
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    fccreacion: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    fcmodificacion: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    usuario_creacion: Mapped[str] = mapped_column(String(150), server_default=func.current_user(), nullable=False)
    usuario_modificacion: Mapped[str] = mapped_column(String(150), server_default=func.current_user(), nullable=False)
    row_version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)

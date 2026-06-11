import uuid
from datetime import datetime
from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.core.database import Base


class NotificacionSistema(Base):
    """
    Notificaciones in-app generadas automáticamente por el sistema.
    Complementa el push via ntfy.
    """
    __tablename__ = "ades_notificaciones_sistema"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    usuario_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id", ondelete="CASCADE"), nullable=False)
    titulo: Mapped[str] = mapped_column(String(200), nullable=False)
    mensaje: Mapped[str | None] = mapped_column(Text)
    tipo: Mapped[str] = mapped_column(String(20), default="INFO", nullable=False)  # INFO | WARN | ERROR | SUCCESS
    leido: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    fecha_creacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    usuario = relationship("Usuario")

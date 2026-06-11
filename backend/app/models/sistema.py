"""
Modelos para Variables del Sistema y Catálogos Dinámicos — FASE 26-A.
"""
from __future__ import annotations
import uuid
from sqlalchemy import String, Boolean, Integer, ForeignKey, UniqueConstraint, CheckConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.dialects.postgresql import UUID
from app.core.database import Base
from app.models.base import AuditMixin


class Catalogo(AuditMixin, Base):
    """Cabecera de catálogos dinámicos administrables desde el módulo Admin."""
    __tablename__ = "ades_catalogos"

    id:          Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default="uuidv7()")
    codigo:      Mapped[str]       = mapped_column(String(80), nullable=False, unique=True)
    nombre:      Mapped[str]       = mapped_column(String(150), nullable=False)
    descripcion: Mapped[str | None]= mapped_column(String(500))

    items: Mapped[list["CatalogoItem"]] = relationship(
        back_populates="catalogo",
        cascade="all, delete-orphan",
        lazy="selectin",
        order_by="CatalogoItem.orden",
    )


class CatalogoItem(AuditMixin, Base):
    """Item/valor de un catálogo dinámico."""
    __tablename__ = "ades_catalogo_items"
    __table_args__ = (UniqueConstraint("catalogo_id", "valor", name="uq_catalogo_valor"),)

    id:          Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default="uuidv7()")
    catalogo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_catalogos.id"), nullable=False)
    valor:       Mapped[str]       = mapped_column(String(200), nullable=False)
    descripcion: Mapped[str | None]= mapped_column(String(500))
    orden:       Mapped[int]       = mapped_column(Integer, default=0)

    catalogo: Mapped["Catalogo"] = relationship(back_populates="items")


TIPOS_VALOR_VALIDOS = ('TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD')


class VariableSistema(AuditMixin, Base):
    """Variable de configuración del sistema administrable desde el módulo Admin."""
    __tablename__ = "ades_variables_sistema"
    __table_args__ = (
        CheckConstraint(
            "tipo_valor IN ('TEXTO','BOOLEANO','JSON','NUMERO','FECHA','HORA','PASSWORD')",
            name="ck_tipo_valor"
        ),
    )

    id:           Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default="uuidv7()")
    nombre:       Mapped[str]       = mapped_column(String(100), nullable=False, unique=True)
    tipo_valor:   Mapped[str]       = mapped_column(String(20), nullable=False, default='TEXTO')
    valor:        Mapped[str | None]= mapped_column()
    descripcion:  Mapped[str | None]= mapped_column(String(500))
    encriptado:   Mapped[bool]      = mapped_column(Boolean, default=False)
    solo_lectura: Mapped[bool]      = mapped_column(Boolean, default=False)
    grupo:        Mapped[str | None]= mapped_column(String(50))

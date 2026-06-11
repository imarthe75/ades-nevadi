"""
Modelos para Menús Dinámicos — FASE 26-B.
Integración del patrón de Oracle APEX (Navigation Menus) → ADES.
"""
from typing import Optional
import uuid
from sqlalchemy import String, Integer, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .base import AuditMixin, Base


class Menu(AuditMixin, Base):
    """Árbol de navegación administrable desde Admin."""
    __tablename__ = "ades_menus"

    id:            Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    label:         Mapped[str] = mapped_column(String(100), nullable=False)
    route:         Mapped[Optional[str]] = mapped_column(String(200))
    icon:          Mapped[Optional[str]] = mapped_column(String(80))
    parent_id:     Mapped[Optional[int]] = mapped_column(ForeignKey("ades_menus.id"))
    permission_id: Mapped[Optional[str]] = mapped_column(String(80))
    peso:          Mapped[int] = mapped_column(Integer, default=100, nullable=False)

    # Relación jerárquica para cargar hijos
    children: Mapped[list["Menu"]] = relationship(
        "Menu",
        backref="parent",
        remote_side=[id],
        order_by="Menu.peso"
    )
    
    # Roles asignados
    roles: Mapped[list["MenuRol"]] = relationship(
        back_populates="menu",
        cascade="all, delete-orphan"
    )


class MenuRol(AuditMixin, Base):
    """Asignación de menús a roles."""
    __tablename__ = "ades_menu_roles"

    menu_id: Mapped[int] = mapped_column(ForeignKey("ades_menus.id", ondelete="CASCADE"), primary_key=True)
    rol_id:  Mapped[uuid.UUID] = mapped_column(ForeignKey("ades_roles.id", ondelete="CASCADE"), primary_key=True)

    menu: Mapped["Menu"] = relationship(back_populates="roles")

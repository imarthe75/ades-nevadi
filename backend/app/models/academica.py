"""
Modelos SQLAlchemy para el dominio Estructura Académica.
Tablas: escuelas, planteles, plantel_niveles, niveles_educativos,
        grados, grupos, ciclos_escolares, periodos_evaluacion.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime
from sqlalchemy import (
    Boolean, Date, DateTime, ForeignKey, Integer,
    Numeric, String, Text, UniqueConstraint, func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.core.database import Base
from .base import AuditMixin


class Escuela(AuditMixin, Base):
    __tablename__ = "ades_escuelas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_escuela: Mapped[str] = mapped_column(String(150), nullable=False, unique=True)
    sitio_web: Mapped[str | None] = mapped_column(String(255))
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    planteles: Mapped[list[Plantel]] = relationship(back_populates="escuela")


class Plantel(AuditMixin, Base):
    __tablename__ = "ades_planteles"
    __table_args__ = (UniqueConstraint("nombre_plantel", "escuela_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_plantel: Mapped[str] = mapped_column(String(100), nullable=False)
    escuela_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_escuelas.id"), nullable=False)
    clave_ct: Mapped[str | None] = mapped_column(String(20))
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    escuela: Mapped[Escuela] = relationship(back_populates="planteles")
    grados: Mapped[list[Grado]] = relationship(back_populates="plantel")
    plantel_niveles: Mapped[list[PlantelNivel]] = relationship(back_populates="plantel")


class NivelEducativo(AuditMixin, Base):
    __tablename__ = "ades_niveles_educativos"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_nivel: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    autoridad_educativa: Mapped[str] = mapped_column(String(50), nullable=False)  # SEP | UAEMEX
    tipo_ciclo: Mapped[str] = mapped_column(String(20), nullable=False)            # ANUAL | SEMESTRAL
    num_periodos_eval: Mapped[int] = mapped_column(Integer, nullable=False)
    tiene_extraordinario: Mapped[bool] = mapped_column(Boolean, default=False)

    grados: Mapped[list[Grado]] = relationship(back_populates="nivel")
    ciclos: Mapped[list[CicloEscolar]] = relationship(back_populates="nivel")


class PlantelNivel(AuditMixin, Base):
    __tablename__ = "ades_plantel_niveles"
    __table_args__ = (UniqueConstraint("plantel_id", "nivel_educativo_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    nivel_educativo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    plantel: Mapped[Plantel] = relationship(back_populates="plantel_niveles")
    nivel: Mapped[NivelEducativo] = relationship()


class Grado(AuditMixin, Base):
    __tablename__ = "ades_grados"
    __table_args__ = (UniqueConstraint("numero_grado", "nivel_educativo_id", "plantel_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    numero_grado: Mapped[int] = mapped_column(Integer, nullable=False)
    nombre_grado: Mapped[str] = mapped_column(String(50), nullable=False)
    nivel_educativo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    nivel: Mapped[NivelEducativo] = relationship(back_populates="grados")
    plantel: Mapped[Plantel] = relationship(back_populates="grados")
    grupos: Mapped[list[Grupo]] = relationship(back_populates="grado")


class CicloEscolar(AuditMixin, Base):
    __tablename__ = "ades_ciclos_escolares"
    __table_args__ = (UniqueConstraint("nombre_ciclo", "nivel_educativo_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_ciclo: Mapped[str] = mapped_column(String(20), nullable=False)
    nivel_educativo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"), nullable=False)
    fecha_inicio: Mapped[date] = mapped_column(Date, nullable=False)
    fecha_fin: Mapped[date] = mapped_column(Date, nullable=False)
    tipo_ciclo: Mapped[str] = mapped_column(String(20), nullable=False)
    es_vigente: Mapped[bool] = mapped_column(Boolean, default=False)

    nivel: Mapped[NivelEducativo] = relationship(back_populates="ciclos")
    grupos: Mapped[list[Grupo]] = relationship(back_populates="ciclo")


class Grupo(AuditMixin, Base):
    __tablename__ = "ades_grupos"
    __table_args__ = (UniqueConstraint("nombre_grupo", "grado_id", "ciclo_escolar_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_grupo: Mapped[str] = mapped_column(String(10), nullable=False)
    grado_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grados.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    profesor_titular_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"))
    capacidad_maxima: Mapped[int] = mapped_column(Integer, default=35)
    turno: Mapped[str] = mapped_column(String(20), default="MATUTINO")
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    grado: Mapped[Grado] = relationship(back_populates="grupos")
    ciclo: Mapped[CicloEscolar] = relationship(back_populates="grupos")
    inscripciones: Mapped[list] = relationship("Inscripcion", back_populates="grupo")


class IdentidadInstitucional(AuditMixin, Base):
    """Branding e identidad visual del sistema por institución/plantel."""
    __tablename__ = 'ades_identidad_institucional'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    tipo_elemento: Mapped[str] = mapped_column(String(50), nullable=False)
    texto_elemento: Mapped[str | None] = mapped_column(Text)
    url_archivo: Mapped[str | None] = mapped_column(String(255))
    color_hex: Mapped[str | None] = mapped_column(String(7))
    escuela_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    plantel_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey('ades_planteles.id'))
    nivel_educativo_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey('ades_niveles_educativos.id'))


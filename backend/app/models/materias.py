"""
Modelos: Materia, MateriasPlan, Tema, AsignacionDocente.
"""
from __future__ import annotations
import uuid
from sqlalchemy import Boolean, ForeignKey, Integer, Numeric, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.core.database import Base
from .base import AuditMixin


class Materia(AuditMixin, Base):
    __tablename__ = "ades_materias"
    __table_args__ = (UniqueConstraint("nombre_materia", "nivel_educativo_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_materia: Mapped[str] = mapped_column(String(150), nullable=False)
    clave_materia: Mapped[str | None] = mapped_column(String(20))
    nivel_educativo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"), nullable=False)
    horas_semana: Mapped[float | None] = mapped_column(Numeric(4, 1))
    es_inglés: Mapped[bool] = mapped_column(Boolean, default=False)

    nivel: Mapped["NivelEducativo"] = relationship("NivelEducativo", foreign_keys=[nivel_educativo_id])
    planes: Mapped[list[MateriaPlan]] = relationship(back_populates="materia")


class MateriaPlan(AuditMixin, Base):
    """Vincula materia con un grado/ciclo específico (plan de estudios activo)."""
    __tablename__ = "ades_materias_plan"
    __table_args__ = (UniqueConstraint("materia_id", "grado_id", "ciclo_escolar_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    grado_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grados.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    horas_semana: Mapped[float | None] = mapped_column(Numeric(4, 1))
    orden: Mapped[int | None] = mapped_column(Integer)

    materia: Mapped[Materia] = relationship(back_populates="planes")
    temas: Mapped[list[Tema]] = relationship(back_populates="materia_plan")
    asignaciones: Mapped[list[AsignacionDocente]] = relationship(back_populates="materia_plan")


class Tema(AuditMixin, Base):
    __tablename__ = "ades_temas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    materia_plan_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias_plan.id"), nullable=False)
    numero_tema: Mapped[int] = mapped_column(Integer, nullable=False)
    nombre_tema: Mapped[str] = mapped_column(String(255), nullable=False)
    descripcion: Mapped[str | None] = mapped_column(Text)
    horas_estimadas: Mapped[float | None] = mapped_column(Numeric(5, 1))

    materia_plan: Mapped[MateriaPlan] = relationship(back_populates="temas")


class AsignacionDocente(AuditMixin, Base):
    __tablename__ = "ades_asignaciones_docentes"
    __table_args__ = (UniqueConstraint("grupo_id", "materia_id", "ciclo_escolar_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    profesor_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)

    materia_plan: Mapped[MateriaPlan | None] = relationship(
        back_populates="asignaciones",
        primaryjoin="and_(AsignacionDocente.materia_id == MateriaPlan.materia_id, "
                    "AsignacionDocente.ciclo_escolar_id == MateriaPlan.ciclo_escolar_id)",
        foreign_keys="[AsignacionDocente.materia_id, AsignacionDocente.ciclo_escolar_id]",
        viewonly=True,
    )

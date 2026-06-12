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
    tipo_materia: Mapped[str | None] = mapped_column(String(50))
    reporta_a_sep_uaemex: Mapped[bool] = mapped_column(Boolean, default=True)
    incluir_en_boleta: Mapped[bool] = mapped_column(Boolean, default=True)
    codigo_sep: Mapped[str | None] = mapped_column(String(30))
    ponderacion_default: Mapped[float | None] = mapped_column(Numeric(5, 4))

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
    es_obligatoria: Mapped[bool] = mapped_column(Boolean, default=True)
    orden: Mapped[int | None] = mapped_column(Integer)

    materia: Mapped[Materia] = relationship(back_populates="planes")
    asignaciones: Mapped[list[AsignacionDocente]] = relationship(
        back_populates="materia_plan",
        primaryjoin="and_(MateriaPlan.materia_id == AsignacionDocente.materia_id, "
                    "MateriaPlan.ciclo_escolar_id == AsignacionDocente.ciclo_escolar_id)",
        foreign_keys="[AsignacionDocente.materia_id, AsignacionDocente.ciclo_escolar_id]",
        viewonly=True,
    )


class Tema(AuditMixin, Base):
    __tablename__ = "ades_temas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    grado_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grados.id"))
    ciclo_escolar_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"))
    nombre_tema: Mapped[str] = mapped_column(String(255), nullable=False)
    descripcion: Mapped[str | None] = mapped_column(Text)
    orden: Mapped[int] = mapped_column(Integer, default=1)
    periodo_sugerido: Mapped[int | None] = mapped_column(Integer)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)


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

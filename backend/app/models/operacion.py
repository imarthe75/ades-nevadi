"""
Modelos FASE 2 — Operación Académica:
  PeriodoEvaluacion, Clase, Asistencia, CalificacionPeriodo,
  Tarea, TareaEntrega, Archivo.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime, time
from decimal import Decimal
from sqlalchemy import (
    BigInteger, Boolean, Date, DateTime, ForeignKey,
    Integer, Numeric, String, Text, Time, UniqueConstraint, func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.core.database import Base
from .base import AuditMixin


class PeriodoEvaluacion(AuditMixin, Base):
    __tablename__ = "ades_periodos_evaluacion"
    __table_args__ = (UniqueConstraint("numero_periodo", "tipo_periodo", "ciclo_escolar_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_periodo: Mapped[str] = mapped_column(String(100), nullable=False)
    numero_periodo: Mapped[int] = mapped_column(Integer, nullable=False)
    tipo_periodo: Mapped[str] = mapped_column(String(20), default="ORDINARIO")   # ORDINARIO | FINAL | EXTRAORDINARIO
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    fecha_inicio: Mapped[date] = mapped_column(Date, nullable=False)
    fecha_fin: Mapped[date] = mapped_column(Date, nullable=False)
    fecha_entrega_boletas: Mapped[date | None] = mapped_column(Date)


class Clase(AuditMixin, Base):
    __tablename__ = "ades_clases"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    horario_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_horarios.id"))
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    profesor_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"), nullable=False)
    fecha_clase: Mapped[date] = mapped_column(Date, nullable=False)
    hora_inicio: Mapped[time] = mapped_column(Time, nullable=False)
    hora_fin: Mapped[time] = mapped_column(Time, nullable=False)
    tema_visto: Mapped[str | None] = mapped_column(Text)
    observaciones: Mapped[str | None] = mapped_column(Text)
    estatus_clase: Mapped[str] = mapped_column(String(20), default="PROGRAMADA")  # PROGRAMADA | IMPARTIDA | CANCELADA | SUSPENDIDA

    asistencias: Mapped[list[Asistencia]] = relationship(back_populates="clase", cascade="all, delete-orphan")


class Asistencia(AuditMixin, Base):
    __tablename__ = "ades_asistencias"
    __table_args__ = (UniqueConstraint("clase_id", "estudiante_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    clase_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_clases.id"), nullable=False)
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    estatus_asistencia: Mapped[str] = mapped_column(String(20), default="PRESENTE")  # PRESENTE | AUSENTE | TARDE | JUSTIFICADO
    observacion: Mapped[str | None] = mapped_column(String(255))

    clase: Mapped[Clase] = relationship(back_populates="asistencias")


class CalificacionPeriodo(AuditMixin, Base):
    __tablename__ = "ades_calificaciones_periodo"
    __table_args__ = (UniqueConstraint("estudiante_id", "materia_id", "periodo_evaluacion_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    periodo_evaluacion_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_periodos_evaluacion.id"), nullable=False)
    calificacion_final: Mapped[Decimal] = mapped_column(Numeric(5, 2), nullable=False)
    # Determinado por trigger trg_calificacion_periodo_acreditado según el nivel educativo
    es_acreditado: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="FALSE")
    observaciones: Mapped[str | None] = mapped_column(Text)


class Tarea(AuditMixin, Base):
    __tablename__ = "ades_tareas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    titulo: Mapped[str] = mapped_column(String(255), nullable=False)
    descripcion: Mapped[str | None] = mapped_column(Text)
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    tema_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_temas.id"))
    periodo_evaluacion_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_periodos_evaluacion.id"))
    fecha_asignacion: Mapped[date] = mapped_column(Date, server_default=func.current_date())
    fecha_entrega: Mapped[date] = mapped_column(Date, nullable=False)
    puntaje_maximo: Mapped[Decimal] = mapped_column(Numeric(5, 2), default=Decimal("10.0"))
    permite_entrega_tarde: Mapped[bool] = mapped_column(Boolean, default=False)
    origen: Mapped[str] = mapped_column(String(20), default="MANUAL")  # MANUAL | AUTO

    entregas: Mapped[list[TareaEntrega]] = relationship(back_populates="tarea", cascade="all, delete-orphan")


class TareaEntrega(AuditMixin, Base):
    __tablename__ = "ades_tareas_entregas"
    __table_args__ = (UniqueConstraint("tarea_id", "estudiante_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    tarea_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_tareas.id"), nullable=False)
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    fecha_entrega: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    es_tarde: Mapped[bool] = mapped_column(Boolean, default=False)
    comentario_alumno: Mapped[str | None] = mapped_column(Text)
    estatus_entrega: Mapped[str] = mapped_column(String(20), default="PENDIENTE")  # PENDIENTE | ENTREGADO | CALIFICADO | TARDE

    tarea: Mapped[Tarea] = relationship(back_populates="entregas")
    calificacion: Mapped[CalificacionEntrega | None] = relationship(
        back_populates="entrega", uselist=False,
        foreign_keys="[CalificacionEntrega.tarea_entrega_id]",
    )
    archivos: Mapped[list[Archivo]] = relationship(
        "Archivo",
        primaryjoin="and_(Archivo.entidad_tipo == 'TAREA_ENTREGA', "
                    "Archivo.entidad_id == TareaEntrega.id)",
        foreign_keys="[Archivo.entidad_id]",
        viewonly=True,
    )


class CalificacionEntrega(AuditMixin, Base):
    """Calificación de una entrega específica (tarea)."""
    __tablename__ = "ades_calificaciones_tareas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    tarea_entrega_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_tareas_entregas.id"), nullable=False, unique=True)
    calificacion: Mapped[Decimal] = mapped_column(Numeric(5, 2), nullable=False)
    comentarios_docente: Mapped[str | None] = mapped_column(Text)
    fecha_calificacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    entrega: Mapped[TareaEntrega] = relationship(back_populates="calificacion", foreign_keys=[tarea_entrega_id])


class Archivo(AuditMixin, Base):
    """Archivo almacenado en MinIO — FK polimórfica via entidad_tipo + entidad_id."""
    __tablename__ = "ades_archivos"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_original: Mapped[str] = mapped_column(String(255), nullable=False)
    nombre_almacenado: Mapped[str] = mapped_column(String(255), nullable=False)  # clave en MinIO
    bucket: Mapped[str] = mapped_column(String(100), default="ades-archivos")
    mime_type: Mapped[str | None] = mapped_column(String(100))
    tamanio_bytes: Mapped[int | None] = mapped_column(BigInteger)
    entidad_tipo: Mapped[str] = mapped_column(String(50), nullable=False)   # TAREA_ENTREGA, EXPEDIENTE_MEDICO, etc.
    entidad_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)

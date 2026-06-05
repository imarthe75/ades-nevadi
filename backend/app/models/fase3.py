"""
Modelos FASE 3 — Módulos especializados:
  - Aulas (espacios físicos)
  - Horarios (aSc TimeTables / manual)
  - DisponibilidadDocente (restricciones para aSc)
  - PersonalSalud
  - ExpedienteMedico
  - IncidenteMedico
  - ReporteConducta
  - ReporteAcademico
"""
from __future__ import annotations
import uuid
from datetime import date, datetime, time
from typing import Literal
from sqlalchemy import (
    Boolean, Date, DateTime, ForeignKey, Integer, JSON,
    SmallInteger, String, Text, Time, func,
)
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .base import AuditMixin, Base


class Aula(AuditMixin, Base):
    __tablename__ = "ades_aulas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_aula: Mapped[str] = mapped_column(String(50), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    tipo_aula: Mapped[str] = mapped_column(String(30), nullable=False, default="AULA")
    capacidad: Mapped[int | None] = mapped_column(Integer, default=35)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class Horario(AuditMixin, Base):
    __tablename__ = "ades_horarios"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    profesor_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"), nullable=False)
    aula_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_aulas.id"))
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    dia_semana: Mapped[int] = mapped_column(SmallInteger, nullable=False)  # 1=Lun … 5=Vie
    hora_inicio: Mapped[time] = mapped_column(Time, nullable=False)
    hora_fin: Mapped[time] = mapped_column(Time, nullable=False)
    origen: Mapped[str] = mapped_column(String(20), nullable=False, default="MANUAL")
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class DisponibilidadDocente(AuditMixin, Base):
    __tablename__ = "ades_disponibilidad_docente"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    profesor_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"), nullable=False)
    dia_semana: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    hora_inicio: Mapped[time] = mapped_column(Time, nullable=False)
    hora_fin: Mapped[time] = mapped_column(Time, nullable=False)
    disponible: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    motivo_no_disponible: Mapped[str | None] = mapped_column(String(200))
    ciclo_escolar_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"))
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class PersonalSalud(AuditMixin, Base):
    __tablename__ = "ades_personal_salud"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    cedula_profesional: Mapped[str | None] = mapped_column(String(50))
    especialidad: Mapped[str | None] = mapped_column(String(100))
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class ExpedienteMedico(AuditMixin, Base):
    __tablename__ = "ades_expedientes_medicos"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False, unique=True)
    tipo_sangre: Mapped[str | None] = mapped_column(String(5))
    alergias: Mapped[str | None] = mapped_column(Text)
    medicamentos_autorizados: Mapped[str | None] = mapped_column(Text)
    condiciones_cronicas: Mapped[str | None] = mapped_column(Text)
    observaciones_generales: Mapped[str | None] = mapped_column(Text)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    # Campos migración 012
    nss: Mapped[str | None] = mapped_column(String(11))
    discapacidad: Mapped[str | None] = mapped_column(Text)
    seguro_medico_tipo: Mapped[str | None] = mapped_column(String(30))
    seguro_medico_numero: Mapped[str | None] = mapped_column(String(50))
    vacunas_al_dia: Mapped[bool] = mapped_column(Boolean, default=True)
    padecimiento_cronico: Mapped[bool] = mapped_column(Boolean, default=False)
    requiere_medicacion: Mapped[bool] = mapped_column(Boolean, default=False)


class IncidenteMedico(AuditMixin, Base):
    __tablename__ = "ades_incidentes_medicos"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    personal_salud_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personal_salud.id"))
    fecha_incidente: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    descripcion: Mapped[str] = mapped_column(Text, nullable=False)
    tratamiento_aplicado: Mapped[str | None] = mapped_column(Text)
    requirio_traslado: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    notificado_tutor: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    fecha_notificacion_tutor: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class ReporteConducta(AuditMixin, Base):
    __tablename__ = "ades_reportes_conducta"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    reportado_por_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_profesores.id"), nullable=False)
    fecha_reporte: Mapped[date] = mapped_column(Date, nullable=False, server_default=func.current_date())
    tipo_falta: Mapped[str] = mapped_column(String(50), nullable=False)  # LEVE, GRAVE, MUY_GRAVE
    descripcion: Mapped[str] = mapped_column(Text, nullable=False)
    medida_aplicada: Mapped[str | None] = mapped_column(Text)
    compromiso_mejora: Mapped[str | None] = mapped_column(Text)
    requiere_seguimiento: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class ReporteAcademico(AuditMixin, Base):
    __tablename__ = "ades_reportes_academicos"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    periodo_evaluacion_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_periodos_evaluacion.id"))
    tipo_reporte: Mapped[str] = mapped_column(String(30), nullable=False, default="BOLETA")
    datos_reporte: Mapped[dict | None] = mapped_column(JSONB)
    fecha_generacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    generado_por_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id"))
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)

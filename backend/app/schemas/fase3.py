"""Schemas Pydantic para operaciones de Fase 3 de ADES.

Cubre entidades de infraestructura y bienestar escolar:
aulas (físicas y especializadas), horarios de grupo/docente,
disponibilidad docente, expediente médico e incidentes, y
reportes de conducta (faltas LEVE/GRAVE/MUY_GRAVE).
Utilizado principalmente por los módulos de horarios y salud del BFF.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime, time
from typing import Literal
from pydantic import Field
from .base import AdesSchema, AdesResponse


# ── Aulas ─────────────────────────────────────────────────────────────────────

TIPO_AULA = Literal["AULA", "LABORATORIO", "COMPUTO", "CANCHA", "TALLER"]


class AulaCreate(AdesSchema):
    """Body para registrar un aula o espacio físico en un plantel."""

    nombre_aula: str = Field(min_length=1, max_length=50)
    plantel_id: uuid.UUID
    tipo_aula: TIPO_AULA = "AULA"
    capacidad: int | None = 35


class AulaOut(AdesResponse):
    """Respuesta de un aula con su tipo y capacidad."""

    nombre_aula: str
    plantel_id: uuid.UUID
    tipo_aula: str
    capacidad: int | None


# ── Horarios ──────────────────────────────────────────────────────────────────

class HorarioCreate(AdesSchema):
    """Body para asignar un bloque horario a un grupo/materia/docente."""

    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    aula_id: uuid.UUID | None = None
    ciclo_escolar_id: uuid.UUID
    dia_semana: int = Field(ge=1, le=5, description="1=Lun … 5=Vie")
    hora_inicio: time
    hora_fin: time
    origen: Literal["ASC", "MANUAL"] = "MANUAL"


class HorarioOut(AdesResponse):
    """Respuesta de un bloque horario con campos enriquecidos de nombres."""

    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    aula_id: uuid.UUID | None
    ciclo_escolar_id: uuid.UUID
    dia_semana: int
    hora_inicio: time
    hora_fin: time
    origen: str
    # Campos enriquecidos (se añaden en el endpoint)
    nombre_materia: str | None = None
    nombre_grupo: str | None = None
    nombre_profesor: str | None = None
    nombre_aula: str | None = None


class HorarioSemanalGrupo(AdesSchema):
    """Vista semanal completa de un grupo — celdas ordenadas por día y hora."""
    grupo_id: uuid.UUID
    nombre_grupo: str
    ciclo_escolar_id: uuid.UUID
    entradas: list[HorarioOut]


class HorarioSemanalProfesor(AdesSchema):
    """Vista semanal de un docente."""
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    entradas: list[HorarioOut]


# ── Disponibilidad Docente ────────────────────────────────────────────────────

class DisponibilidadCreate(AdesSchema):
    """Body para registrar la disponibilidad horaria semanal de un docente."""

    profesor_id: uuid.UUID
    dia_semana: int = Field(ge=1, le=5)
    hora_inicio: time
    hora_fin: time
    disponible: bool = True
    motivo_no_disponible: str | None = None
    ciclo_escolar_id: uuid.UUID | None = None


class DisponibilidadOut(AdesResponse):
    """Respuesta del bloque de disponibilidad de un docente."""

    profesor_id: uuid.UUID
    dia_semana: int
    hora_inicio: time
    hora_fin: time
    disponible: bool
    motivo_no_disponible: str | None
    ciclo_escolar_id: uuid.UUID | None


# ── Expediente Médico ─────────────────────────────────────────────────────────

class ExpedienteCreate(AdesSchema):
    """Body para crear el expediente médico inicial de un estudiante."""

    estudiante_id: uuid.UUID
    tipo_sangre: str | None = None
    alergias: str | None = None
    medicamentos_autorizados: str | None = None
    condiciones_cronicas: str | None = None
    observaciones_generales: str | None = None


class ExpedienteUpdate(AdesSchema):
    """Campos actualizables del expediente médico (todos opcionales)."""

    tipo_sangre: str | None = None
    alergias: str | None = None
    medicamentos_autorizados: str | None = None
    condiciones_cronicas: str | None = None
    observaciones_generales: str | None = None


class ExpedienteOut(AdesResponse):
    """Respuesta del expediente médico de un estudiante (datos sensibles LFPDPPP)."""

    estudiante_id: uuid.UUID
    tipo_sangre: str | None
    alergias: str | None
    medicamentos_autorizados: str | None
    condiciones_cronicas: str | None
    observaciones_generales: str | None


# ── Incidentes Médicos ────────────────────────────────────────────────────────

class IncidenteCreate(AdesSchema):
    """Body para registrar un incidente médico ocurrido en el plantel."""

    estudiante_id: uuid.UUID
    personal_salud_id: uuid.UUID | None = None
    descripcion: str = Field(min_length=5)
    tratamiento_aplicado: str | None = None
    requirio_traslado: bool = False
    notificado_tutor: bool = False
    fecha_notificacion_tutor: datetime | None = None


class IncidenteOut(AdesResponse):
    """Respuesta de un incidente médico con fecha y datos de seguimiento."""

    estudiante_id: uuid.UUID
    personal_salud_id: uuid.UUID | None
    fecha_incidente: datetime
    descripcion: str
    tratamiento_aplicado: str | None
    requirio_traslado: bool
    notificado_tutor: bool
    fecha_notificacion_tutor: datetime | None


# ── Conducta ──────────────────────────────────────────────────────────────────

TIPO_FALTA = Literal["LEVE", "GRAVE", "MUY_GRAVE"]


class ConductaCreate(AdesSchema):
    """Body para registrar un reporte de conducta contra un estudiante."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    reportado_por_id: uuid.UUID
    tipo_falta: TIPO_FALTA
    descripcion: str = Field(min_length=10)
    medida_aplicada: str | None = None
    compromiso_mejora: str | None = None
    requiere_seguimiento: bool = False


class ConductaUpdate(AdesSchema):
    """Campos actualizables de un reporte de conducta (seguimiento y cierre)."""

    medida_aplicada: str | None = None
    compromiso_mejora: str | None = None
    requiere_seguimiento: bool | None = None
    estatus_id: uuid.UUID | None = None


class ConductaOut(AdesResponse):
    """Respuesta de un reporte de conducta con su estatus y compromisos."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    reportado_por_id: uuid.UUID
    fecha_reporte: date
    tipo_falta: str
    descripcion: str
    medida_aplicada: str | None
    compromiso_mejora: str | None
    requiere_seguimiento: bool
    estatus_id: uuid.UUID | None

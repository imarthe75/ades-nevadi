"""Schemas Pydantic para el dominio Académico de ADES.

Cubre la jerarquía institucional (plantel, nivel, grado, ciclo, grupo)
y los modelos de respuesta que el BFF Spring consume y retorna al frontend.
Todos los IDs son UUID v7; los campos de auditoría los gestiona AdesResponse.
"""
from __future__ import annotations
import uuid
from datetime import date
from pydantic import Field
from .base import AdesSchema, AdesResponse


# ── Plantel ───────────────────────────────────────────────────────────────────

class PlantelBase(AdesSchema):
    """Campos compartidos de un plantel educativo."""
    nombre_plantel: str = Field(min_length=2, max_length=100)
    clave_ct: str | None = None


class PlantelCreate(PlantelBase):
    """Body para crear un plantel; requiere la escuela padre."""

    escuela_id: uuid.UUID


class PlantelUpdate(AdesSchema):
    """Campos actualizables de un plantel (todos opcionales)."""
    nombre_plantel: str | None = Field(None, min_length=2, max_length=100)
    clave_ct: str | None = None


class PlantelOut(AdesResponse, PlantelBase):
    """Respuesta completa de un plantel con campos de auditoría."""

    escuela_id: uuid.UUID


# ── Nivel Educativo ───────────────────────────────────────────────────────────

class NivelOut(AdesResponse):
    """Nivel educativo (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX)."""
    nombre_nivel: str
    autoridad_educativa: str
    tipo_ciclo: str
    num_periodos_eval: int
    tiene_extraordinario: bool


# ── Grado ─────────────────────────────────────────────────────────────────────

class GradoOut(AdesResponse):
    """Grado escolar dentro de un nivel y plantel."""
    numero_grado: int
    nombre_grado: str
    nivel_educativo_id: uuid.UUID
    plantel_id: uuid.UUID
    plantel_nombre: str | None = None


# ── Ciclo Escolar ─────────────────────────────────────────────────────────────

class CicloOut(AdesResponse):
    """Ciclo escolar vigente o histórico para un nivel educativo."""
    nombre_ciclo: str
    nivel_educativo_id: uuid.UUID
    fecha_inicio: date
    fecha_fin: date
    tipo_ciclo: str
    es_vigente: bool
    nombre_nivel: str | None = None


# ── Grupo ─────────────────────────────────────────────────────────────────────

class GrupoBase(AdesSchema):
    """Campos compartidos de un grupo escolar."""
    nombre_grupo: str = Field(min_length=1, max_length=10)
    capacidad_maxima: int = Field(default=35, ge=1, le=60)
    turno: str = "MATUTINO"


class GrupoCreate(GrupoBase):
    """Body para crear un grupo; requiere grado y ciclo escolar."""

    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID


class GrupoOut(AdesResponse, GrupoBase):
    """Respuesta estándar de un grupo escolar."""

    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    profesor_titular_id: uuid.UUID | None


class GrupoDetalle(GrupoOut):
    """Grupo con info de grado, nivel y plantel expandida — para selectores en UI."""
    grado: GradoOut | None = None
    nombre_grado: str | None = None
    nombre_nivel: str | None = None
    numero_grado: int | None = None
    plantel_nombre: str | None = None  # populated from grado.plantel.nombre_plantel
    inscritos: int = 0

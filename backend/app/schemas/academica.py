from __future__ import annotations
import uuid
from datetime import date
from pydantic import Field
from .base import AdesSchema, AdesResponse


# ── Plantel ───────────────────────────────────────────────────────────────────

class PlantelBase(AdesSchema):
    nombre_plantel: str = Field(min_length=2, max_length=100)
    clave_ct: str | None = None


class PlantelCreate(PlantelBase):
    escuela_id: uuid.UUID


class PlantelUpdate(AdesSchema):
    nombre_plantel: str | None = Field(None, min_length=2, max_length=100)
    clave_ct: str | None = None


class PlantelOut(AdesResponse, PlantelBase):
    escuela_id: uuid.UUID


# ── Nivel Educativo ───────────────────────────────────────────────────────────

class NivelOut(AdesResponse):
    nombre_nivel: str
    autoridad_educativa: str
    tipo_ciclo: str
    num_periodos_eval: int
    tiene_extraordinario: bool


# ── Grado ─────────────────────────────────────────────────────────────────────

class GradoOut(AdesResponse):
    numero_grado: int
    nombre_grado: str
    nivel_educativo_id: uuid.UUID
    plantel_id: uuid.UUID


# ── Ciclo Escolar ─────────────────────────────────────────────────────────────

class CicloOut(AdesResponse):
    nombre_ciclo: str
    nivel_educativo_id: uuid.UUID
    fecha_inicio: date
    fecha_fin: date
    tipo_ciclo: str
    es_vigente: bool
    nombre_nivel: str | None = None


# ── Grupo ─────────────────────────────────────────────────────────────────────

class GrupoBase(AdesSchema):
    nombre_grupo: str = Field(min_length=1, max_length=10)
    capacidad_maxima: int = Field(default=35, ge=1, le=60)
    turno: str = "MATUTINO"


class GrupoCreate(GrupoBase):
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID


class GrupoOut(AdesResponse, GrupoBase):
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    profesor_titular_id: uuid.UUID | None


class GrupoDetalle(GrupoOut):
    """Grupo con info de grado y nivel expandida — para selectores en UI."""
    grado: GradoOut | None = None
    nombre_grado: str | None = None   # populated from grado.nombre_grado
    nombre_nivel: str | None = None   # populated from grado.nivel.nombre_nivel
    numero_grado: int | None = None   # for sorting
    inscritos: int = 0                # alumnos activos inscritos

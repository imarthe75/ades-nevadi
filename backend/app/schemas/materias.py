from __future__ import annotations
import uuid
from pydantic import Field
from .base import AdesSchema, AdesResponse


class MateriaOut(AdesResponse):
    nombre_materia: str
    clave_materia: str | None = None
    nivel_educativo_id: uuid.UUID
    horas_semana: float | None = None
    es_inglés: bool = False
    tipo_materia: str | None = None
    reporta_a_sep_uaemex: bool = True
    incluir_en_boleta: bool = True
    codigo_sep: str | None = None
    ponderacion_default: float | None = None


class MateriaCreate(AdesSchema):
    nombre_materia: str = Field(min_length=2, max_length=150)
    clave_materia: str | None = Field(None, max_length=20)
    nivel_educativo_id: uuid.UUID
    horas_semana: float | None = None
    es_inglés: bool = False
    tipo_materia: str | None = None
    reporta_a_sep_uaemex: bool = True
    incluir_en_boleta: bool = True
    codigo_sep: str | None = Field(None, max_length=30)
    ponderacion_default: float | None = None


class MateriaUpdate(AdesSchema):
    nombre_materia: str | None = Field(None, min_length=2, max_length=150)
    clave_materia: str | None = Field(None, max_length=20)
    nivel_educativo_id: uuid.UUID | None = None
    horas_semana: float | None = None
    es_inglés: bool | None = None
    tipo_materia: str | None = None
    reporta_a_sep_uaemex: bool | None = None
    incluir_en_boleta: bool | None = None
    codigo_sep: str | None = Field(None, max_length=30)
    ponderacion_default: float | None = None
    is_active: bool | None = None


class MateriaPlanOut(AdesResponse):
    materia_id: uuid.UUID
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    horas_semana: float | None = None
    es_obligatoria: bool = True
    orden: int | None = None
    is_active: bool = True
    materia: MateriaOut | None = None


class MateriaPlanCreate(AdesSchema):
    materia_id: uuid.UUID
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    horas_semana: float | None = None
    es_obligatoria: bool = True
    orden: int | None = None


class MateriaPlanUpdate(AdesSchema):
    horas_semana: float | None = None
    es_obligatoria: bool | None = None
    orden: int | None = None
    is_active: bool | None = None


class TemaOut(AdesResponse):
    materia_id: uuid.UUID
    grado_id: uuid.UUID | None = None
    ciclo_escolar_id: uuid.UUID | None = None
    nombre_tema: str
    descripcion: str | None = None
    orden: int = 1
    periodo_sugerido: int | None = None


class AsignacionDocenteOut(AdesResponse):
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID

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


class MateriaCreate(AdesSchema):
    nombre_materia: str = Field(min_length=2, max_length=150)
    clave_materia: str | None = Field(None, max_length=20)
    nivel_educativo_id: uuid.UUID
    horas_semana: float | None = None
    es_inglés: bool = False


class MateriaUpdate(AdesSchema):
    nombre_materia: str | None = Field(None, min_length=2, max_length=150)
    clave_materia: str | None = Field(None, max_length=20)
    nivel_educativo_id: uuid.UUID | None = None
    horas_semana: float | None = None
    es_inglés: bool | None = None
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
    materia_plan_id: uuid.UUID
    numero_tema: int
    nombre_tema: str
    descripcion: str | None
    horas_estimadas: float | None


class AsignacionDocenteOut(AdesResponse):
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID

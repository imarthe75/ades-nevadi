"""Schemas Pydantic para el dominio de Materias y Planes de Estudio de ADES.

Incluye asignaturas con campo_formativo NEM (4 campos formativos SEP),
su relación con planes de estudio por grado/ciclo (MateriaPlan), temas
curriculares y asignaciones docentes. Las materias UAEMEX dejan
campo_formativo en NULL.
"""
from __future__ import annotations
import uuid
from pydantic import Field
from .base import AdesSchema, AdesResponse


class MateriaOut(AdesResponse):
    """Respuesta completa de una asignatura con metadatos NEM y SEP."""
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
    """Body para crear una nueva asignatura en el catálogo."""

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
    """Campos actualizables de una asignatura (todos opcionales)."""

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
    """Respuesta de la asignación de una materia a un grado/ciclo en el plan de estudios."""

    materia_id: uuid.UUID
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    horas_semana: float | None = None
    es_obligatoria: bool = True
    orden: int | None = None
    is_active: bool = True
    materia: MateriaOut | None = None


class MateriaPlanCreate(AdesSchema):
    """Body para agregar una materia a un plan de estudios por grado y ciclo."""

    materia_id: uuid.UUID
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    horas_semana: float | None = None
    es_obligatoria: bool = True
    orden: int | None = None


class MateriaPlanUpdate(AdesSchema):
    """Campos actualizables de la relación materia-plan (horas, orden, obligatoriedad)."""

    horas_semana: float | None = None
    es_obligatoria: bool | None = None
    orden: int | None = None
    is_active: bool | None = None


class TemaOut(AdesResponse):
    """Respuesta de un tema curricular perteneciente a una materia."""

    materia_id: uuid.UUID
    grado_id: uuid.UUID | None = None
    ciclo_escolar_id: uuid.UUID | None = None
    nombre_tema: str
    descripcion: str | None = None
    orden: int = 1
    periodo_sugerido: int | None = None


class AsignacionDocenteOut(AdesResponse):
    """Respuesta de la asignación de un docente a un grupo/materia/ciclo."""

    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID

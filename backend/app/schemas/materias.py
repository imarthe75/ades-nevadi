from __future__ import annotations
import uuid
from pydantic import Field
from .base import AdesSchema, AdesResponse


class MateriaOut(AdesResponse):
    nombre_materia: str
    clave_materia: str | None
    nivel_educativo_id: uuid.UUID
    horas_semana: float | None
    es_inglés: bool


class MateriaPlanOut(AdesResponse):
    materia_id: uuid.UUID
    grado_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    horas_semana: float | None
    orden: int | None
    materia: MateriaOut | None = None


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

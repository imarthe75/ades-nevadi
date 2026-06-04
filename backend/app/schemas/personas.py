from __future__ import annotations
import uuid
from datetime import date
from pydantic import Field, field_validator
from .base import AdesSchema, AdesResponse


# ── Persona ───────────────────────────────────────────────────────────────────

class PersonaBase(AdesSchema):
    nombre: str = Field(min_length=1, max_length=100)
    apellido_paterno: str = Field(min_length=1, max_length=100)
    apellido_materno: str | None = None
    curp: str = Field(min_length=18, max_length=18)
    genero: str | None = Field(None, pattern="^[MF]$")
    fecha_nacimiento: date | None = None

    @field_validator("curp")
    @classmethod
    def curp_uppercase(cls, v: str) -> str:
        return v.upper()


class PersonaCreate(PersonaBase):
    pass


class PersonaOut(AdesResponse, PersonaBase):
    nombre_completo: str = ""

    @classmethod
    def from_orm_with_full_name(cls, obj) -> "PersonaOut":
        data = cls.model_validate(obj)
        data.nombre_completo = obj.nombre_completo
        return data


# ── Estudiante ────────────────────────────────────────────────────────────────

class EstudianteCreate(AdesSchema):
    persona: PersonaCreate
    plantel_id: uuid.UUID
    fecha_ingreso: date | None = None


class EstudianteOut(AdesResponse):
    matricula: str
    plantel_id: uuid.UUID
    fecha_ingreso: date | None
    persona: PersonaOut | None = None


# ── Inscripción ───────────────────────────────────────────────────────────────

class InscripcionCreate(AdesSchema):
    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    fecha_inscripcion: date | None = None


class InscripcionOut(AdesResponse):
    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    fecha_inscripcion: date | None


# ── Profesor ──────────────────────────────────────────────────────────────────

class ProfesorCreate(AdesSchema):
    persona: PersonaCreate
    plantel_id: uuid.UUID
    numero_empleado: str
    tipo_contrato: str = "BASE"


class ProfesorOut(AdesResponse):
    numero_empleado: str
    plantel_id: uuid.UUID
    tipo_contrato: str | None
    persona: PersonaOut | None = None


# ── Usuario ───────────────────────────────────────────────────────────────────

class UsuarioOut(AdesResponse):
    nombre_usuario: str
    email_institucional: str
    persona_id: uuid.UUID
    rol_id: uuid.UUID

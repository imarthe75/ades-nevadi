"""Schemas Pydantic para el dominio de Personas en ADES.

Contiene datos PII protegidos bajo LFPDPPP: alumnos, docentes, contactos
familiares y usuarios del sistema. Todos los campos de identificación
(CURP, RFC, NSS, CLABE) se validan en el backend antes de persistirse.
Los schemas siguen el patrón Base/Create/Update/Out para cada entidad.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime
from decimal import Decimal
from pydantic import Field, field_validator
from .base import AdesSchema, AdesResponse


# ── Persona ───────────────────────────────────────────────────────────────────

class PersonaBase(AdesSchema):
    """Datos personales comunes a alumnos, docentes y personal (PII — LFPDPPP)."""
    nombre: str = Field(min_length=1, max_length=100)
    apellido_paterno: str = Field(min_length=1, max_length=100)
    apellido_materno: str | None = None
    curp: str = Field(min_length=18, max_length=18)
    genero: str | None = Field(None, pattern="^[MF]$")
    fecha_nacimiento: date | None = None
    # Contacto y datos civiles (migración 011)
    telefono: str | None = Field(None, max_length=15)
    email_personal: str | None = Field(None, max_length=255)
    estado_civil: str | None = None
    municipio_nacimiento: str | None = None
    estado_nacimiento: str | None = None
    nacionalidad: str | None = "MEXICANA"

    @field_validator("curp")
    @classmethod
    def curp_uppercase(cls, v: str) -> str:
        return v.upper()

    @field_validator("fecha_nacimiento")
    @classmethod
    def validate_fecha_nacimiento(cls, v: date | None) -> date | None:
        if v is None:
            return v
        current_year = datetime.now().year
        min_year = 1900
        if not (min_year <= v.year <= current_year):
            raise ValueError(
                f"Fecha de nacimiento inválida: año {v.year} debe estar entre {min_year} y {current_year}"
            )
        return v


class PersonaCreate(PersonaBase):
    """Body para registrar una nueva persona en el sistema."""


class PersonaUpdate(AdesSchema):
    """Campos actualizables de una persona (CURP es inmutable tras creación)."""
    nombre: str | None = Field(None, min_length=1, max_length=100)
    apellido_paterno: str | None = Field(None, min_length=1, max_length=100)
    apellido_materno: str | None = None
    genero: str | None = Field(None, pattern="^[MF]$")
    fecha_nacimiento: date | None = None
    telefono: str | None = None
    email_personal: str | None = None
    estado_civil: str | None = None
    municipio_nacimiento: str | None = None
    estado_nacimiento: str | None = None
    nacionalidad: str | None = None


class PersonaOut(AdesResponse, PersonaBase):
    """Respuesta de una persona con nombre_completo calculado.

    CURP se relaja a opcional para no romper registros legacy o seeds con
    datos incompletos.
    """

    # En salida relajamos curp para no romper registros con datos legacy o seeds
    curp: str | None = Field(None, max_length=18)
    nombre_completo: str = ""

    @classmethod
    def from_orm_with_full_name(cls, obj) -> "PersonaOut":
        data = cls.model_validate(obj)
        data.nombre_completo = obj.nombre_completo
        return data


# ── Estudiante ────────────────────────────────────────────────────────────────

class EstudianteDatosComplementarios(AdesSchema):
    """Campos académicos/socioeconómicos del alumno (sin salud — ver ExpedienteMedico)."""
    nss: str | None = Field(None, max_length=11)
    discapacidad: str | None = None
    escuela_procedencia: str | None = None
    clave_ct_procedencia: str | None = None
    promedio_procedencia: Decimal | None = None
    beca_tipo: str | None = None
    beca_monto: Decimal | None = None
    nivel_socioeconomico: str | None = None
    etnia: str | None = None
    lengua_indigena: str | None = None


class ExpedienteMedicoOut(AdesSchema):
    """Resumen del expediente médico de un estudiante (datos sensibles LFPDPPP)."""

    estudiante_id: uuid.UUID
    tipo_sangre: str | None = None
    alergias: str | None = None
    medicamentos_autorizados: str | None = None
    condiciones_cronicas: str | None = None
    observaciones_generales: str | None = None
    nss: str | None = None
    discapacidad: str | None = None
    seguro_medico_tipo: str | None = None
    seguro_medico_numero: str | None = None
    vacunas_al_dia: bool = True
    padecimiento_cronico: bool = False
    requiere_medicacion: bool = False


class ExpedienteMedicoUpdate(AdesSchema):
    """Campos actualizables del expediente médico (todos opcionales)."""

    tipo_sangre: str | None = None
    alergias: str | None = None
    medicamentos_autorizados: str | None = None
    condiciones_cronicas: str | None = None
    observaciones_generales: str | None = None
    nss: str | None = Field(None, max_length=11)
    discapacidad: str | None = None
    seguro_medico_tipo: str | None = None
    seguro_medico_numero: str | None = None
    vacunas_al_dia: bool | None = None
    padecimiento_cronico: bool | None = None
    requiere_medicacion: bool | None = None


class EstudianteCreate(AdesSchema):
    """Body para dar de alta un nuevo estudiante con sus datos personales."""

    persona: PersonaCreate
    plantel_id: uuid.UUID
    fecha_ingreso: date | None = None


class EstudianteUpdate(AdesSchema):
    """Campos actualizables de un estudiante (persona, fecha de ingreso, complementarios)."""

    persona: PersonaUpdate | None = None
    fecha_ingreso: date | None = None
    complementarios: EstudianteDatosComplementarios | None = None


class EstudianteOut(AdesResponse):
    """Respuesta completa de un estudiante con datos personales y complementarios académicos."""

    matricula: str
    plantel_id: uuid.UUID
    fecha_ingreso: date | None
    persona: PersonaOut | None = None
    # Complementarios académicos (tipo_sangre/alergias → ExpedienteMedico)
    nss: str | None = None
    discapacidad: str | None = None
    escuela_procedencia: str | None = None
    clave_ct_procedencia: str | None = None
    promedio_procedencia: Decimal | None = None
    beca_tipo: str | None = None
    beca_monto: Decimal | None = None
    nivel_socioeconomico: str | None = None
    etnia: str | None = None
    lengua_indigena: str | None = None


# ── Inscripción ───────────────────────────────────────────────────────────────

class InscripcionCreate(AdesSchema):
    """Body para inscribir un estudiante a un grupo en un ciclo escolar."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    fecha_inscripcion: date | None = None


class InscripcionOut(AdesResponse):
    """Respuesta de la inscripción de un estudiante a un grupo/ciclo."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    fecha_inscripcion: date | None


# ── Profesor ──────────────────────────────────────────────────────────────────

class ProfesorDatosLaborales(AdesSchema):
    """Campos laborales y de nómina del profesor."""
    rfc: str | None = Field(None, max_length=13)
    nss: str | None = Field(None, max_length=11)
    cedula_profesional: str | None = None
    especialidad: str | None = None
    nivel_estudios: str | None = None
    fecha_ingreso_inst: date | None = None
    clabe: str | None = Field(None, max_length=18)
    banco: str | None = None
    turno: str | None = None
    tipo_contrato: str | None = None


class ProfesorCreate(AdesSchema):
    """Body para dar de alta un nuevo docente con sus datos personales y número de empleado."""

    persona: PersonaCreate
    plantel_id: uuid.UUID
    numero_empleado: str
    tipo_contrato: str = "BASE"


class ProfesorUpdate(AdesSchema):
    """Campos actualizables de un docente (persona y datos laborales)."""

    persona: PersonaUpdate | None = None
    laborales: ProfesorDatosLaborales | None = None


class ProfesorOut(AdesResponse):
    """Respuesta completa de un docente con datos personales y laborales."""

    numero_empleado: str
    plantel_id: uuid.UUID
    tipo_contrato: str | None
    persona: PersonaOut | None = None
    # Laborales
    rfc: str | None = None
    nss: str | None = None
    cedula_profesional: str | None = None
    especialidad: str | None = None
    nivel_estudios: str | None = None
    fecha_ingreso_inst: date | None = None
    clabe: str | None = None
    banco: str | None = None
    turno: str | None = None


# ── Contacto de Emergencia ─────────────────────────────────────────────────────

class ContactoCreate(AdesSchema):
    """Body para registrar un contacto de emergencia o tutor legal de un estudiante."""

    persona_id: uuid.UUID
    nombre_completo: str = Field(min_length=2, max_length=200)
    parentesco: str | None = None
    telefono: str | None = Field(None, max_length=15)
    telefono_alt: str | None = Field(None, max_length=15)
    email: str | None = None
    es_tutor_legal: bool = False
    es_contacto_prim: bool = False
    ocupacion: str | None = None
    nivel_estudios: str | None = None
    rfc: str | None = Field(None, max_length=13)


class ContactoUpdate(AdesSchema):
    """Campos actualizables de un contacto de emergencia (todos opcionales)."""

    nombre_completo: str | None = Field(None, min_length=2, max_length=200)
    parentesco: str | None = None
    telefono: str | None = None
    telefono_alt: str | None = None
    email: str | None = None
    es_tutor_legal: bool | None = None
    es_contacto_prim: bool | None = None
    ocupacion: str | None = None
    nivel_estudios: str | None = None
    rfc: str | None = None


class ContactoOut(AdesResponse):
    """Respuesta de un contacto de emergencia con parentesco y datos de comunicación."""

    persona_id: uuid.UUID
    nombre_completo: str
    parentesco: str | None
    telefono: str | None
    telefono_alt: str | None
    email: str | None
    es_tutor_legal: bool
    es_contacto_prim: bool
    ocupacion: str | None
    nivel_estudios: str | None
    rfc: str | None


# ── Usuario ───────────────────────────────────────────────────────────────────

class UsuarioOut(AdesResponse):
    """Respuesta de un usuario del sistema con su persona y rol asociados."""

    nombre_usuario: str
    email_institucional: str
    persona_id: uuid.UUID
    rol_id: uuid.UUID

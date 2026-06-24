"""Schemas Pydantic para las operaciones académicas del día a día en ADES.

Incluye los siguientes dominios operativos:
- Periodos de evaluación (trimestres NEM, parciales UAEMEX).
- Clases: programación, impartición y estados.
- Asistencias: registro masivo por clase, estatus y reportes.
- Calificaciones: captura por periodo, libreta grupal y boleta por alumno.
- Tareas: creación, entregas y calificación de entregas.
- Archivos: metadatos de archivos almacenados en MinIO/SeaweedFS con URLs prefirmadas.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime, time
from decimal import Decimal
from typing import Literal
from pydantic import Field, field_validator
from .base import AdesSchema, AdesResponse


# ── Periodos de Evaluación ────────────────────────────────────────────────────

class PeriodoOut(AdesResponse):
    """Periodo de evaluación (trimestre NEM o parcial UAEMEX) de un ciclo escolar."""
    nombre_periodo: str
    numero_periodo: int
    tipo_periodo: str
    ciclo_escolar_id: uuid.UUID
    fecha_inicio: date
    fecha_fin: date
    fecha_entrega_boletas: date | None


# ── Clases ────────────────────────────────────────────────────────────────────

class ClaseCreate(AdesSchema):
    """Body para registrar o programar una clase en el diario de un grupo."""

    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    fecha_clase: date
    hora_inicio: time
    hora_fin: time
    tema_visto: str | None = None
    observaciones: str | None = None


class ClaseUpdate(AdesSchema):
    """Campos actualizables de una clase (tema, observaciones, estatus)."""

    tema_visto: str | None = None
    observaciones: str | None = None
    estatus_clase: Literal["PROGRAMADA", "IMPARTIDA", "CANCELADA", "SUSPENDIDA"] | None = None


class ClaseOut(AdesResponse):
    """Respuesta de una clase con campos enriquecidos de nombres."""

    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    fecha_clase: date
    hora_inicio: time
    hora_fin: time
    tema_visto: str | None
    observaciones: str | None
    estatus_clase: str
    grupo_nombre: str | None = None
    materia_nombre: str | None = None


# ── Asistencias ───────────────────────────────────────────────────────────────

ESTATUS_ASISTENCIA = Literal["PRESENTE", "AUSENTE", "TARDE", "JUSTIFICADO"]


class AsistenciaItem(AdesSchema):
    """Un registro de asistencia para un alumno."""
    estudiante_id: uuid.UUID
    estatus_asistencia: ESTATUS_ASISTENCIA = "PRESENTE"
    observacion: str | None = None


class RegistrarAsistenciaIn(AdesSchema):
    """Body para marcar asistencia de toda la clase de una vez."""
    asistencias: list[AsistenciaItem]


class AsistenciaOut(AdesResponse):
    """Respuesta de un registro de asistencia individual."""

    clase_id: uuid.UUID
    estudiante_id: uuid.UUID
    estatus_asistencia: str
    observacion: str | None


class ReporteAsistenciaAlumno(AdesSchema):
    """Resumen de asistencia de un alumno para un grupo/periodo."""
    estudiante_id: uuid.UUID
    total_clases: int
    presentes: int
    ausentes: int
    tardes: int
    justificados: int
    porcentaje_asistencia: float


class ReporteAsistenciaGrupo(AdesSchema):
    """Reporte de asistencia consolidado de todos los alumnos de un grupo."""

    grupo_id: uuid.UUID
    total_clases: int
    alumnos: list[ReporteAsistenciaAlumno]


# ── Calificaciones ────────────────────────────────────────────────────────────

class CalificacionCreate(AdesSchema):
    """Body para capturar la calificación final de un alumno en un periodo."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    periodo_evaluacion_id: uuid.UUID
    calificacion_final: Decimal = Field(ge=0, le=100)
    observaciones: str | None = None

    @field_validator("calificacion_final")
    @classmethod
    def dos_decimales(cls, v: Decimal) -> Decimal:
        return round(v, 2)


class CalificacionUpdate(AdesSchema):
    """Body para corregir una calificación ya registrada."""

    calificacion_final: Decimal = Field(ge=0, le=100)
    observaciones: str | None = None


class CalificacionOut(AdesResponse):
    """Respuesta de la calificación de un alumno en un periodo con estado de acreditación."""

    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    periodo_evaluacion_id: uuid.UUID
    calificacion_final: Decimal
    es_acreditado: bool
    observaciones: str | None


class RegistroLibreta(AdesSchema):
    """Fila de la libreta: alumno + sus calificaciones por periodo."""
    estudiante_id: uuid.UUID
    matricula: str
    nombre_completo: str
    calificaciones: dict[str, Decimal | None]   # periodo_nombre → calificación
    promedio: Decimal | None


class PeriodoSimple(AdesSchema):
    """Referencia mínima a un periodo de evaluación (id + nombre)."""

    id: uuid.UUID
    nombre_periodo: str


class LibretaGrupo(AdesSchema):
    """Libreta completa de un grupo para una materia."""
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    periodos: list[str]
    periodos_detalle: list[PeriodoSimple]
    registros: list[RegistroLibreta]


class BolentaMateria(AdesSchema):
    """Calificaciones de una materia por periodo para la boleta del alumno."""

    materia_nombre: str
    calificaciones: dict[str, Decimal | None]   # periodo_nombre → calificación
    promedio: Decimal | None
    acreditado: bool


class BolentaAlumno(AdesSchema):
    """Boleta completa del alumno para un ciclo."""
    estudiante_id: uuid.UUID
    matricula: str
    nombre_completo: str
    ciclo_nombre: str
    materias: list[BolentaMateria]
    promedio_general: Decimal | None


# ── Tareas ────────────────────────────────────────────────────────────────────

class TareaCreate(AdesSchema):
    """Body para crear una tarea, proyecto o actividad evaluable asignada a un grupo."""

    titulo: str = Field(min_length=3, max_length=255)
    descripcion: str | None = None
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    tema_id: uuid.UUID | None = None
    periodo_evaluacion_id: uuid.UUID | None = None
    fecha_entrega: date
    puntaje_maximo: Decimal = Field(default=Decimal("10.0"), ge=0, le=100)
    permite_entrega_tarde: bool = False


class TareaUpdate(AdesSchema):
    """Campos actualizables de una tarea (todos opcionales)."""

    titulo: str | None = Field(None, min_length=3, max_length=255)
    descripcion: str | None = None
    fecha_entrega: date | None = None
    puntaje_maximo: Decimal | None = None
    permite_entrega_tarde: bool | None = None


class TareaOut(AdesResponse):
    """Respuesta completa de una tarea con fechas y configuración de entrega."""

    titulo: str
    descripcion: str | None
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    tema_id: uuid.UUID | None
    periodo_evaluacion_id: uuid.UUID | None
    fecha_asignacion: date
    fecha_entrega: date
    puntaje_maximo: Decimal
    permite_entrega_tarde: bool
    origen: str


# ── Entregas ──────────────────────────────────────────────────────────────────

class EntregaOut(AdesResponse):
    """Respuesta de la entrega de un alumno para una tarea."""

    tarea_id: uuid.UUID
    estudiante_id: uuid.UUID
    fecha_entrega: datetime | None
    es_tarde: bool
    comentario_alumno: str | None
    estatus_entrega: str


class CalificarEntregaIn(AdesSchema):
    """Body para que el docente califique la entrega de un alumno."""

    calificacion: Decimal = Field(ge=0, le=100)
    comentario_docente: str | None = None


class CalificacionEntregaOut(AdesResponse):
    """Respuesta de la calificación asignada a una entrega específica."""

    entrega_id: uuid.UUID
    calificacion: Decimal
    comentario_docente: str | None
    fecha_calificacion: datetime


class ArchivoOut(AdesResponse):
    """Metadatos de un archivo almacenado en MinIO/SeaweedFS, con URL prefirmada opcional."""

    nombre_original: str
    nombre_almacenado: str
    bucket: str
    mime_type: str | None
    tamanio_bytes: int | None
    entidad_tipo: str
    entidad_id: uuid.UUID
    url_descarga: str | None = None  # URL prefirmada de MinIO (se genera en endpoint)

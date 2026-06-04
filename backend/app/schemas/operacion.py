from __future__ import annotations
import uuid
from datetime import date, datetime, time
from decimal import Decimal
from typing import Literal
from pydantic import Field, field_validator
from .base import AdesSchema, AdesResponse


# ── Periodos de Evaluación ────────────────────────────────────────────────────

class PeriodoOut(AdesResponse):
    nombre_periodo: str
    numero_periodo: int
    tipo_periodo: str
    ciclo_escolar_id: uuid.UUID
    fecha_inicio: date
    fecha_fin: date
    fecha_entrega_boletas: date | None


# ── Clases ────────────────────────────────────────────────────────────────────

class ClaseCreate(AdesSchema):
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    fecha_clase: date
    hora_inicio: time
    hora_fin: time
    tema_visto: str | None = None
    observaciones: str | None = None


class ClaseUpdate(AdesSchema):
    tema_visto: str | None = None
    observaciones: str | None = None
    estatus_clase: Literal["PROGRAMADA", "IMPARTIDA", "CANCELADA", "SUSPENDIDA"] | None = None


class ClaseOut(AdesResponse):
    grupo_id: uuid.UUID
    materia_id: uuid.UUID
    profesor_id: uuid.UUID
    fecha_clase: date
    hora_inicio: time
    hora_fin: time
    tema_visto: str | None
    observaciones: str | None
    estatus_clase: str


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
    grupo_id: uuid.UUID
    total_clases: int
    alumnos: list[ReporteAsistenciaAlumno]


# ── Calificaciones ────────────────────────────────────────────────────────────

class CalificacionCreate(AdesSchema):
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
    calificacion_final: Decimal = Field(ge=0, le=100)
    observaciones: str | None = None


class CalificacionOut(AdesResponse):
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
    titulo: str | None = Field(None, min_length=3, max_length=255)
    descripcion: str | None = None
    fecha_entrega: date | None = None
    puntaje_maximo: Decimal | None = None
    permite_entrega_tarde: bool | None = None


class TareaOut(AdesResponse):
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
    tarea_id: uuid.UUID
    estudiante_id: uuid.UUID
    fecha_entrega: datetime | None
    es_tarde: bool
    comentario_alumno: str | None
    estatus_entrega: str


class CalificarEntregaIn(AdesSchema):
    calificacion: Decimal = Field(ge=0, le=100)
    comentario_docente: str | None = None


class CalificacionEntregaOut(AdesResponse):
    entrega_id: uuid.UUID
    calificacion: Decimal
    comentario_docente: str | None
    fecha_calificacion: datetime


class ArchivoOut(AdesResponse):
    nombre_original: str
    nombre_almacenado: str
    bucket: str
    mime_type: str | None
    tamanio_bytes: int | None
    entidad_tipo: str
    entidad_id: uuid.UUID
    url_descarga: str | None = None  # URL prefirmada de MinIO (se genera en endpoint)

"""
Modelos: Persona, Estudiante, Profesor, Inscripción, Usuario, Rol, Estatus.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime
from decimal import Decimal
from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, Numeric, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.core.database import Base
from .base import AuditMixin


class Estatus(AuditMixin, Base):
    __tablename__ = "ades_estatus"
    __table_args__ = (UniqueConstraint("entidad", "nombre_estatus"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    entidad: Mapped[str] = mapped_column(String(100), nullable=False)
    nombre_estatus: Mapped[str] = mapped_column(String(50), nullable=False)
    descripcion: Mapped[str | None] = mapped_column(String(255))


class Rol(AuditMixin, Base):
    __tablename__ = "ades_roles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_rol: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    descripcion: Mapped[str | None] = mapped_column(String(255))
    nivel_acceso: Mapped[int] = mapped_column(Integer, default=5)


class Persona(AuditMixin, Base):
    __tablename__ = "ades_personas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre: Mapped[str] = mapped_column(String(100), nullable=False)
    apellido_paterno: Mapped[str] = mapped_column(String(100), nullable=False)
    apellido_materno: Mapped[str | None] = mapped_column(String(100))
    curp: Mapped[str] = mapped_column(String(18), nullable=False, unique=True)
    genero: Mapped[str | None] = mapped_column(String(1))
    fecha_nacimiento: Mapped[date | None] = mapped_column(Date)
    # Datos de contacto y civiles (migración 011)
    telefono: Mapped[str | None] = mapped_column(String(15))
    email_personal: Mapped[str | None] = mapped_column(String(255))
    estado_civil: Mapped[str | None] = mapped_column(String(20))
    municipio_nacimiento: Mapped[str | None] = mapped_column(String(100))
    estado_nacimiento: Mapped[str | None] = mapped_column(String(100))
    nacionalidad: Mapped[str | None] = mapped_column(String(50), default="MEXICANA")
    foto_url: Mapped[str | None] = mapped_column(String(500))

    @property
    def nombre_completo(self) -> str:
        partes = [self.nombre, self.apellido_paterno, self.apellido_materno]
        return " ".join(p for p in partes if p)


class Profesor(AuditMixin, Base):
    __tablename__ = "ades_profesores"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    numero_empleado: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))
    tipo_contrato: Mapped[str | None] = mapped_column(String(20))
    # Datos laborales (migración 011)
    rfc: Mapped[str | None] = mapped_column(String(13))
    nss: Mapped[str | None] = mapped_column(String(11))
    cedula_profesional: Mapped[str | None] = mapped_column(String(20))
    especialidad: Mapped[str | None] = mapped_column(String(100))
    nivel_estudios: Mapped[str | None] = mapped_column(String(30))
    fecha_ingreso_inst: Mapped[date | None] = mapped_column(Date)
    clabe: Mapped[str | None] = mapped_column(String(18))
    banco: Mapped[str | None] = mapped_column(String(100))
    turno: Mapped[str | None] = mapped_column(String(20))

    persona: Mapped[Persona] = relationship()


class Estudiante(AuditMixin, Base):
    __tablename__ = "ades_estudiantes"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    matricula: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))
    fecha_ingreso: Mapped[date | None] = mapped_column(Date)
    # Datos académicos (migración 011; tipo_sangre/alergias movidos a expediente_medico en mig.012)
    nss: Mapped[str | None] = mapped_column(String(11))
    discapacidad: Mapped[str | None] = mapped_column(Text)
    escuela_procedencia: Mapped[str | None] = mapped_column(String(200))
    clave_ct_procedencia: Mapped[str | None] = mapped_column(String(20))
    promedio_procedencia: Mapped[Decimal | None] = mapped_column(Numeric(4, 2))
    beca_tipo: Mapped[str | None] = mapped_column(String(100))
    beca_monto: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    nivel_socioeconomico: Mapped[str | None] = mapped_column(String(20))
    etnia: Mapped[str | None] = mapped_column(String(100))
    lengua_indigena: Mapped[str | None] = mapped_column(String(100))

    persona: Mapped[Persona] = relationship()
    inscripciones: Mapped[list[Inscripcion]] = relationship(back_populates="estudiante")


class Inscripcion(AuditMixin, Base):
    __tablename__ = "ades_inscripciones"
    __table_args__ = (UniqueConstraint("estudiante_id", "grupo_id", "ciclo_escolar_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    grupo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    fecha_inscripcion: Mapped[date | None] = mapped_column(Date)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    estudiante: Mapped[Estudiante] = relationship(back_populates="inscripciones")
    grupo: Mapped["Grupo"] = relationship(back_populates="inscripciones")  # type: ignore[name-defined]


class Usuario(AuditMixin, Base):
    __tablename__ = "ades_usuarios"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_usuario: Mapped[str] = mapped_column(String(150), nullable=False, unique=True)
    email_institucional: Mapped[str] = mapped_column(String(255), nullable=False, unique=True)
    clave_hash: Mapped[str | None] = mapped_column(Text)
    oidc_sub: Mapped[str | None] = mapped_column(String(255))
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    rol_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_roles.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"))
    nivel_educativo_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"))
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    persona: Mapped[Persona] = relationship()
    rol: Mapped[Rol] = relationship()


class ContactoFamiliar(AuditMixin, Base):
    """Tutor legal o contacto de emergencia del alumno (1,980 registros seed)."""
    __tablename__ = "ades_contactos_familiares"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    persona_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"))
    parentesco: Mapped[str | None] = mapped_column(String(50))
    es_tutor_legal: Mapped[bool] = mapped_column(Boolean, default=False)
    es_contacto_emergencia: Mapped[bool] = mapped_column(Boolean, default=False)
    puede_recoger: Mapped[bool] = mapped_column(Boolean, default=False)
    # Datos de detalle (migración 012)
    nombre_completo: Mapped[str | None] = mapped_column(String(200))
    telefono_principal: Mapped[str | None] = mapped_column(String(15))
    telefono_trabajo: Mapped[str | None] = mapped_column(String(15))
    email: Mapped[str | None] = mapped_column(String(255))
    ocupacion: Mapped[str | None] = mapped_column(String(100))
    nivel_estudios: Mapped[str | None] = mapped_column(String(30))
    rfc: Mapped[str | None] = mapped_column(String(13))
    prioridad: Mapped[int] = mapped_column(Integer, default=1)

    persona: Mapped[Persona | None] = relationship()


class Baja(AuditMixin, Base):
    """Baja, traslado o cambio de estatus del alumno."""
    __tablename__ = "ades_bajas"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    inscripcion_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_inscripciones.id"))
    tipo_baja: Mapped[str] = mapped_column(String(30), nullable=False)
    motivo: Mapped[str | None] = mapped_column(Text)
    fecha_efectiva: Mapped[date] = mapped_column(Date, nullable=False)
    fecha_reingreso: Mapped[date | None] = mapped_column(Date)
    plantel_destino: Mapped[str | None] = mapped_column(String(200))
    clave_ct_destino: Mapped[str | None] = mapped_column(String(20))
    autorizado_por_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id"))
    observaciones: Mapped[str | None] = mapped_column(Text)


class Extraordinaria(AuditMixin, Base):
    """Examen extraordinario o de regularización."""
    __tablename__ = "ades_extraordinarias"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    materia_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_materias.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"), nullable=False)
    grupo_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_grupos.id"))
    tipo_examen: Mapped[str] = mapped_column(String(30), default="EXTRAORDINARIO")
    calificacion_previa: Mapped[Decimal | None] = mapped_column(Numeric(4, 2))
    fecha_examen: Mapped[date | None] = mapped_column(Date)
    calificacion: Mapped[Decimal | None] = mapped_column(Numeric(4, 2))
    acredita: Mapped[bool | None] = mapped_column(Boolean)
    aplicado_por_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id"))
    observaciones: Mapped[str | None] = mapped_column(Text)


class DocumentoTipo(AuditMixin, Base):
    """Catálogo de documentos requeridos para el expediente."""
    __tablename__ = "ades_documentos_tipo"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    nombre_documento: Mapped[str] = mapped_column(String(150), nullable=False)
    descripcion: Mapped[str | None] = mapped_column(Text)
    nivel_educativo_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_niveles_educativos.id"))
    obligatorio: Mapped[bool] = mapped_column(Boolean, default=True)
    aplica_inscripcion: Mapped[bool] = mapped_column(Boolean, default=True)
    aplica_egreso: Mapped[bool] = mapped_column(Boolean, default=False)
    orden: Mapped[int] = mapped_column(Integer, default=1)


class ExpedienteDoc(AuditMixin, Base):
    """Estado de un documento requerido por alumno."""
    __tablename__ = "ades_expediente_docs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    documento_tipo_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_documentos_tipo.id"), nullable=False)
    ciclo_escolar_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"))
    estatus: Mapped[str] = mapped_column(String(20), default="PENDIENTE")
    fecha_entrega: Mapped[date | None] = mapped_column(Date)
    fecha_vencimiento: Mapped[date | None] = mapped_column(Date)
    observaciones: Mapped[str | None] = mapped_column(String(500))
    verificado_por_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id"))

    documento_tipo: Mapped[DocumentoTipo] = relationship()


class Constancia(AuditMixin, Base):
    """Constancias y documentos oficiales emitidos."""
    __tablename__ = "ades_constancias"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    estudiante_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estudiantes.id"), nullable=False)
    tipo_constancia: Mapped[str] = mapped_column(String(50), nullable=False)
    folio: Mapped[str | None] = mapped_column(String(50), unique=True)
    ciclo_escolar_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_ciclos_escolares.id"))
    fecha_emision: Mapped[date] = mapped_column(Date, server_default=func.current_date())
    fecha_vencimiento: Mapped[date | None] = mapped_column(Date)
    solicitada_por: Mapped[str | None] = mapped_column(String(200))
    proposito: Mapped[str | None] = mapped_column(String(200))
    emitida_por_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_usuarios.id"))
    entregada: Mapped[bool] = mapped_column(Boolean, default=False)
    fecha_entrega: Mapped[date | None] = mapped_column(Date)
    observaciones: Mapped[str | None] = mapped_column(Text)


class AuditLog(Base):
    """Log de auditoría inmutable."""
    __tablename__ = "ades_audit_log"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    usuario_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    nombre_usuario: Mapped[str | None] = mapped_column(String(150))
    ip_origen: Mapped[str | None] = mapped_column(String(45))
    accion: Mapped[str] = mapped_column(String(10), nullable=False)
    entidad: Mapped[str] = mapped_column(String(100), nullable=False)
    entidad_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    endpoint: Mapped[str | None] = mapped_column(String(200))
    metodo_http: Mapped[str | None] = mapped_column(String(10))
    codigo_respuesta: Mapped[int | None] = mapped_column(Integer)
    duracion_ms: Mapped[int | None] = mapped_column(Integer)
    fccreacion: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())

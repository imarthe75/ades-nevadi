"""
Modelos: Persona, Estudiante, Profesor, Inscripción, Usuario, Rol, Estatus.
"""
from __future__ import annotations
import uuid
from datetime import date
from sqlalchemy import Boolean, Date, ForeignKey, Integer, String, Text, UniqueConstraint, func
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
    genero: Mapped[str | None] = mapped_column(String(1))  # M | F
    fecha_nacimiento: Mapped[date | None] = mapped_column(Date)

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

    persona: Mapped[Persona] = relationship()


class Estudiante(AuditMixin, Base):
    __tablename__ = "ades_estudiantes"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, server_default=func.uuidv7())
    matricula: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    plantel_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_planteles.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))
    fecha_ingreso: Mapped[date | None] = mapped_column(Date)

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
    clave_hash: Mapped[str | None] = mapped_column(Text)        # null para SSO
    oidc_sub: Mapped[str | None] = mapped_column(String(255))   # sub del JWT Authentik
    persona_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_personas.id"), nullable=False)
    rol_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_roles.id"), nullable=False)
    estatus_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ades_estatus.id"))

    persona: Mapped[Persona] = relationship()
    rol: Mapped[Rol] = relationship()

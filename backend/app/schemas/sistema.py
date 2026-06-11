"""
Schemas Pydantic para Variables del Sistema y Catálogos Dinámicos — FASE 26-A.
"""
from __future__ import annotations
import uuid
import json
from typing import Literal
from pydantic import BaseModel, field_validator, model_validator

TipoValor = Literal['TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD']

# ── Catálogo Items ─────────────────────────────────────────────────────────────

class CatalogoItemCreate(BaseModel):
    valor: str
    descripcion: str | None = None
    orden: int = 0


class CatalogoItemOut(BaseModel):
    id: uuid.UUID
    catalogo_id: uuid.UUID
    valor: str
    descripcion: str | None
    orden: int
    is_active: bool
    row_version: int
    model_config = {"from_attributes": True}


class CatalogoItemUpdate(BaseModel):
    valor: str | None = None
    descripcion: str | None = None
    orden: int | None = None
    is_active: bool | None = None
    row_version: int  # Obligatorio para optimistic locking


# ── Catálogos ──────────────────────────────────────────────────────────────────

class CatalogoCreate(BaseModel):
    codigo: str
    nombre: str
    descripcion: str | None = None


class CatalogoOut(BaseModel):
    id: uuid.UUID
    codigo: str
    nombre: str
    descripcion: str | None
    is_active: bool
    row_version: int
    items: list[CatalogoItemOut] = []
    model_config = {"from_attributes": True}


class CatalogoUpdate(BaseModel):
    nombre: str | None = None
    descripcion: str | None = None
    is_active: bool | None = None
    row_version: int  # Obligatorio para optimistic locking


# ── Variables del Sistema ──────────────────────────────────────────────────────

class VariableOut(BaseModel):
    id: uuid.UUID
    nombre: str
    tipo_valor: str
    valor: str | None
    descripcion: str | None
    encriptado: bool
    solo_lectura: bool
    grupo: str | None
    is_active: bool
    row_version: int
    model_config = {"from_attributes": True}

    @model_validator(mode='after')
    def enmascarar_secretos(self) -> "VariableOut":
        if self.encriptado or self.tipo_valor == 'PASSWORD':
            self.valor = None
        return self


class VariableCreate(BaseModel):
    nombre: str
    tipo_valor: TipoValor
    valor: str | None = None
    descripcion: str | None = None
    encriptado: bool = False
    solo_lectura: bool = False
    grupo: str | None = None


class VariableUpdate(BaseModel):
    valor: str | None = None
    descripcion: str | None = None
    grupo: str | None = None
    row_version: int  # Obligatorio para optimistic locking

    # La validación estricta de tipo de dato se hace en el endpoint
    # para tener acceso al tipo_valor de la variable existente.

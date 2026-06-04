from __future__ import annotations
import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict


class AdesSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class AdesResponse(AdesSchema):
    """Base para todas las respuestas — incluye campos de auditoría."""
    id: uuid.UUID
    ref: uuid.UUID
    is_active: bool
    fccreacion: datetime
    fcmodificacion: datetime


class Paginacion(AdesSchema):
    total: int
    pagina: int
    por_pagina: int
    paginas: int

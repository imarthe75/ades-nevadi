from __future__ import annotations
import uuid
from datetime import datetime
from typing import Generic, TypeVar
from pydantic import BaseModel, ConfigDict

T = TypeVar("T")


class AdesSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class AdesResponse(AdesSchema):
    """Base para todas las respuestas — incluye campos de auditoría."""
    id: uuid.UUID
    ref: uuid.UUID
    is_active: bool
    fecha_creacion: datetime
    fecha_modificacion: datetime


class Paginacion(AdesSchema):
    total: int
    pagina: int
    por_pagina: int
    paginas: int


class PagedResponse(BaseModel, Generic[T]):
    """Respuesta paginada genérica para cualquier entidad."""
    data: list[T]
    total: int
    pagina: int
    por_pagina: int
    paginas: int

    @classmethod
    def build(cls, data: list, total: int, pagina: int, por_pagina: int) -> "PagedResponse":
        import math
        return cls(
            data=data,
            total=total,
            pagina=pagina,
            por_pagina=por_pagina,
            paginas=max(1, math.ceil(total / por_pagina)),
        )

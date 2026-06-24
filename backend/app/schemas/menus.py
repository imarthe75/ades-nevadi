"""
Schemas Pydantic para Menús Dinámicos — FASE 26-B.
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel

class MenuOut(BaseModel):
    """Nodo de menú dinámico con soporte para árbol de hijos anidados.

    El árbol se construye en el BFF filtrando por rol y nivel_acceso del usuario.
    La PK id fue migrada a UUID en mig 087; esta clase se actualizará al reflejar
    ese cambio en el endpoint /mi-menu.
    """

    id: int
    label: str
    route: Optional[str]
    icon: Optional[str]
    parent_id: Optional[int]
    peso: int
    
    # Children for nested tree structure
    children: list["MenuOut"] = []

    model_config = {"from_attributes": True}

"""
Schemas Pydantic para Menús Dinámicos — FASE 26-B.
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel

class MenuOut(BaseModel):
    id: int
    label: str
    route: Optional[str]
    icon: Optional[str]
    parent_id: Optional[int]
    peso: int
    
    # Children for nested tree structure
    children: list["MenuOut"] = []

    model_config = {"from_attributes": True}

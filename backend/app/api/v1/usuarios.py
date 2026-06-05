"""
Endpoints de autenticación y usuarios:
  GET  /auth/me          — perfil del usuario autenticado (desde token JWT)
  GET  /usuarios         — listar usuarios (admin)
  GET  /usuarios/{id}    — detalle de usuario
  PATCH /usuarios/{id}/rol — cambiar rol (admin)
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.models.personas import Usuario, Persona, Rol
from app.models.academica import Plantel, NivelEducativo
from app.schemas.personas import UsuarioOut
from app.schemas.base import AdesSchema

router = APIRouter(tags=["usuarios & auth"])


# ── Me ─────────────────────────────────────────────────────────────────────────

class MeOut(AdesSchema):
    id: uuid.UUID
    nombre_usuario: str
    email_institucional: str
    persona_id: uuid.UUID
    nombre_completo: str
    rol: str
    nivel_acceso: int
    oidc_sub: str | None = None
    plantel_id: uuid.UUID | None = None
    nivel_educativo_id: uuid.UUID | None = None
    nombre_plantel: str | None = None
    nombre_nivel: str | None = None


@router.get("/auth/me", response_model=MeOut)
async def me(
    ades_user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    """Devuelve el perfil del usuario basándose en el sub del JWT de Authentik."""
    q = (
        select(Usuario)
        .options(selectinload(Usuario.persona))
        .where(Usuario.id == ades_user.id)
    )
    usuario = (await db.execute(q)).scalar_one_or_none()
    if not usuario:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Usuario no encontrado")

    # Resolver nombres de plantel/nivel para el badge en el frontend
    nombre_plantel: str | None = None
    nombre_nivel: str | None = None
    if ades_user.plantel_id:
        plantel = await db.get(Plantel, ades_user.plantel_id)
        nombre_plantel = plantel.nombre_plantel if plantel else None
    if ades_user.nivel_educativo_id:
        nivel = await db.get(NivelEducativo, ades_user.nivel_educativo_id)
        nombre_nivel = nivel.nombre_nivel if nivel else None

    return MeOut(
        id=usuario.id,
        nombre_usuario=usuario.nombre_usuario,
        email_institucional=usuario.email_institucional,
        persona_id=usuario.persona_id,
        nombre_completo=usuario.persona.nombre_completo if usuario.persona else "",
        rol=ades_user.rol,
        nivel_acceso=ades_user.nivel_acceso,
        oidc_sub=usuario.oidc_sub,
        plantel_id=ades_user.plantel_id,
        nivel_educativo_id=ades_user.nivel_educativo_id,
        nombre_plantel=nombre_plantel,
        nombre_nivel=nombre_nivel,
    )


# ── Listar usuarios (admin) ────────────────────────────────────────────────────

@router.get("/usuarios", response_model=list[UsuarioOut])
async def listar_usuarios(
    rol: str | None = Query(None, description="Filtrar por nombre_rol"),
    buscar: str | None = None,
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(30, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(Usuario)
        .join(Rol, Rol.id == Usuario.rol_id)
        .where(Usuario.is_active == True)
    )
    if rol:
        q = q.where(Rol.nombre_rol == rol.upper())
    if buscar:
        term = f"%{buscar}%"
        q = q.where(
            Usuario.nombre_usuario.ilike(term)
            | Usuario.email_institucional.ilike(term)
        )
    q = q.order_by(Usuario.nombre_usuario)
    q = q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/usuarios/{usuario_id}", response_model=UsuarioOut)
async def obtener_usuario(
    usuario_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Usuario, usuario_id)
    if not row:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return row


class CambiarRolIn(AdesSchema):
    rol_id: uuid.UUID


@router.patch("/usuarios/{usuario_id}/rol", response_model=UsuarioOut)
async def cambiar_rol(
    usuario_id: uuid.UUID,
    data: CambiarRolIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    usuario = await db.get(Usuario, usuario_id)
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    rol = await db.get(Rol, data.rol_id)
    if not rol:
        raise HTTPException(status_code=404, detail="Rol no encontrado")
    usuario.rol_id = data.rol_id
    await db.commit()
    await db.refresh(usuario)
    return usuario

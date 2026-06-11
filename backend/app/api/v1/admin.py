"""
FASE 12 — Módulo de Administración.
Endpoints protegidos (nivel_acceso <= 1): ADMIN_GLOBAL y ADMIN_PLANTEL.

  GET/POST/PATCH   /admin/ciclos
  GET/PATCH        /admin/usuarios
  PATCH            /admin/planteles/{id}
  GET/POST/PATCH   /admin/grupos
"""
from __future__ import annotations
import uuid
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from pydantic import Field
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.models.academica import CicloEscolar, NivelEducativo, Plantel, Grupo, Grado, IdentidadInstitucional
from app.models.personas import Usuario, Persona, Rol, Estatus
from app.schemas.academica import CicloOut, PlantelOut, GrupoOut, GrupoCreate, GrupoDetalle
from app.schemas.base import AdesSchema, AdesResponse

router = APIRouter(prefix="/admin", tags=["administración"])


def _require_admin(ades_user: AdesUser) -> None:
    if ades_user.nivel_acceso > 1:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Se requiere rol ADMIN_GLOBAL o ADMIN_PLANTEL",
        )


# ══════════════════════════════════════════════════════════════════════════════
# CICLOS ESCOLARES
# ══════════════════════════════════════════════════════════════════════════════

class CicloCreate(AdesSchema):
    nombre_ciclo: str = Field(min_length=3, max_length=20)
    nivel_educativo_id: uuid.UUID
    fecha_inicio: date
    fecha_fin: date
    tipo_ciclo: str = "ANUAL"
    es_vigente: bool = False


class CicloUpdate(AdesSchema):
    nombre_ciclo: str | None = Field(None, min_length=3, max_length=20)
    fecha_inicio: date | None = None
    fecha_fin: date | None = None
    tipo_ciclo: str | None = None
    es_vigente: bool | None = None


@router.get("/ciclos", response_model=list[CicloOut])
async def listar_ciclos_admin(
    nivel: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    q = select(CicloEscolar).join(NivelEducativo, NivelEducativo.id == CicloEscolar.nivel_educativo_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if ades_user.nivel_educativo_id:
        q = q.where(CicloEscolar.nivel_educativo_id == ades_user.nivel_educativo_id)
    q = q.order_by(CicloEscolar.fecha_inicio.desc())
    rows = await db.execute(q)
    return rows.scalars().all()


@router.post("/ciclos", response_model=CicloOut, status_code=201)
async def crear_ciclo(
    data: CicloCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    if data.fecha_fin <= data.fecha_inicio:
        raise HTTPException(status_code=422, detail="fecha_fin debe ser posterior a fecha_inicio")

    nivel = await db.get(NivelEducativo, data.nivel_educativo_id)
    if not nivel:
        raise HTTPException(status_code=404, detail="Nivel educativo no encontrado")

    ciclo = CicloEscolar(**data.model_dump())
    db.add(ciclo)
    await db.commit()
    await db.refresh(ciclo)
    return ciclo


@router.patch("/ciclos/{ciclo_id}", response_model=CicloOut)
async def actualizar_ciclo(
    ciclo_id: uuid.UUID,
    data: CicloUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    ciclo = await db.get(CicloEscolar, ciclo_id)
    if not ciclo:
        raise HTTPException(status_code=404, detail="Ciclo no encontrado")

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(ciclo, field, value)

    # Si se activa este ciclo, desactivar los otros del mismo nivel
    if data.es_vigente:
        q = select(CicloEscolar).where(
            CicloEscolar.nivel_educativo_id == ciclo.nivel_educativo_id,
            CicloEscolar.id != ciclo_id,
            CicloEscolar.es_vigente == True,
        )
        otros = (await db.execute(q)).scalars().all()
        for otro in otros:
            otro.es_vigente = False

    await db.commit()
    await db.refresh(ciclo)
    return ciclo


@router.delete("/ciclos/{ciclo_id}", status_code=204)
async def eliminar_ciclo(
    ciclo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    ciclo = await db.get(CicloEscolar, ciclo_id)
    if not ciclo:
        raise HTTPException(status_code=404, detail="Ciclo no encontrado")
    if ciclo.es_vigente:
        raise HTTPException(status_code=409, detail="No se puede eliminar el ciclo vigente")
    ciclo.is_active = False
    await db.commit()


# ══════════════════════════════════════════════════════════════════════════════
# USUARIOS
# ══════════════════════════════════════════════════════════════════════════════

class UsuarioAdminOut(AdesSchema):
    id: uuid.UUID
    nombre_usuario: str
    email_institucional: str
    nombre_completo: str
    rol: str
    nivel_acceso: int
    plantel_id: uuid.UUID | None = None
    nivel_educativo_id: uuid.UUID | None = None
    nombre_plantel: str | None = None
    nombre_nivel: str | None = None
    is_active: bool


class UsuarioAdminCreate(AdesSchema):
    nombre: str = Field(..., min_length=1, max_length=80)
    apellido_paterno: str = Field(..., min_length=1, max_length=80)
    apellido_materno: str | None = None
    curp: str = Field(..., min_length=18, max_length=18)
    genero: str | None = None
    fecha_nacimiento: date | None = None
    rol_id: uuid.UUID
    plantel_id: uuid.UUID | None = None
    nivel_educativo_id: uuid.UUID | None = None
    email_institucional: str | None = None


class UsuarioAdminUpdate(AdesSchema):
    rol_id: uuid.UUID | None = None
    plantel_id: uuid.UUID | None = Field(None, description="null = ADMIN_GLOBAL")
    nivel_educativo_id: uuid.UUID | None = Field(None, description="null = scope de plantel")
    is_active: bool | None = None


@router.get("/usuarios", response_model=list[UsuarioAdminOut])
async def listar_usuarios_admin(
    buscar: str | None = None,
    rol: str | None = None,
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(50, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    q = (
        select(Usuario)
        .options(
            selectinload(Usuario.persona),
            selectinload(Usuario.rol),
            selectinload(Usuario.plantel),
            selectinload(Usuario.nivel_educativo),
        )
        .join(Rol, Rol.id == Usuario.rol_id)
    )
    # ADMIN_PLANTEL solo ve usuarios de su plantel
    if ades_user.plantel_id:
        q = q.where(Usuario.plantel_id == ades_user.plantel_id)
    if rol:
        q = q.where(Rol.nombre_rol == rol.upper())
    if buscar:
        term = f"%{buscar}%"
        q = q.where(Usuario.nombre_usuario.ilike(term) | Usuario.email_institucional.ilike(term))
    q = q.order_by(Rol.nivel_acceso, Usuario.nombre_usuario)
    q = q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    rows = (await db.execute(q)).scalars().all()

    return [
        UsuarioAdminOut(
            id=u.id,
            nombre_usuario=u.nombre_usuario,
            email_institucional=u.email_institucional,
            nombre_completo=u.persona.nombre_completo if u.persona else "",
            rol=u.rol.nombre_rol if u.rol else "",
            nivel_acceso=u.rol.nivel_acceso if u.rol else 99,
            plantel_id=u.plantel_id,
            nivel_educativo_id=u.nivel_educativo_id,
            nombre_plantel=u.plantel.nombre_plantel if u.plantel else None,
            nombre_nivel=u.nivel_educativo.nombre_nivel if u.nivel_educativo else None,
            is_active=u.is_active,
        )
        for u in rows
    ]


@router.post("/usuarios", response_model=UsuarioAdminOut, status_code=201)
async def crear_usuario_admin(
    data: UsuarioAdminCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Crear usuario con su persona asociada desde el módulo de administración."""
    _require_admin(ades_user)

    rol = await db.get(Rol, data.rol_id)
    if not rol:
        raise HTTPException(status_code=404, detail="Rol no encontrado")
    if rol.nivel_acceso < ades_user.nivel_acceso:
        raise HTTPException(status_code=403, detail="No puede crear usuarios con mayor jerarquía")

    # Verificar CURP única
    from sqlalchemy import func as sqlfunc
    existe = (await db.execute(
        select(Persona).where(sqlfunc.upper(Persona.curp) == data.curp.upper())
    )).scalar_one_or_none()
    if existe:
        raise HTTPException(status_code=409, detail=f"Ya existe una persona con CURP {data.curp.upper()}")

    # Crear persona
    persona = Persona(
        nombre=data.nombre.strip(),
        apellido_paterno=data.apellido_paterno.strip(),
        apellido_materno=data.apellido_materno.strip() if data.apellido_materno else None,
        curp=data.curp.upper().strip(),
        genero=data.genero,
        fecha_nacimiento=data.fecha_nacimiento,
    )
    db.add(persona)
    await db.flush()

    # Generar nombre_usuario y email
    slug = f"{data.nombre[0].lower()}{data.apellido_paterno.lower()[:8]}".replace(" ", "")
    nombre_usuario = slug
    # Verificar unicidad
    counter = 1
    while (await db.execute(select(Usuario).where(Usuario.nombre_usuario == nombre_usuario))).scalar_one_or_none():
        nombre_usuario = f"{slug}{counter}"
        counter += 1

    email = data.email_institucional or f"{nombre_usuario}@nevadi.edu.mx"

    # ADMIN_PLANTEL solo puede crear en su propio plantel
    plantel_id = data.plantel_id
    if ades_user.plantel_id and plantel_id and plantel_id != ades_user.plantel_id:
        raise HTTPException(status_code=403, detail="No puede crear usuarios en otro plantel")

    usuario = Usuario(
        persona_id=persona.id,
        nombre_usuario=nombre_usuario,
        email_institucional=email,
        rol_id=data.rol_id,
        plantel_id=plantel_id,
        nivel_educativo_id=data.nivel_educativo_id,
        clave_hash="PENDIENTE_OIDC",
    )
    db.add(usuario)
    await db.commit()
    await db.refresh(usuario)

    nombre_plantel = None
    nombre_nivel = None
    if plantel_id:
        p = await db.get(Plantel, plantel_id)
        nombre_plantel = p.nombre_plantel if p else None
    if data.nivel_educativo_id:
        n = await db.get(NivelEducativo, data.nivel_educativo_id)
        nombre_nivel = n.nombre_nivel if n else None

    return UsuarioAdminOut(
        id=usuario.id,
        nombre_usuario=usuario.nombre_usuario,
        email_institucional=usuario.email_institucional,
        nombre_completo=persona.nombre_completo,
        rol=rol.nombre_rol,
        nivel_acceso=rol.nivel_acceso,
        plantel_id=plantel_id,
        nivel_educativo_id=data.nivel_educativo_id,
        nombre_plantel=nombre_plantel,
        nombre_nivel=nombre_nivel,
        is_active=True,
    )


@router.patch("/usuarios/{usuario_id}", response_model=UsuarioAdminOut)
async def actualizar_usuario_admin(
    usuario_id: uuid.UUID,
    data: UsuarioAdminUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)

    q = (
        select(Usuario)
        .options(selectinload(Usuario.persona), selectinload(Usuario.rol))
        .where(Usuario.id == usuario_id)
    )
    usuario = (await db.execute(q)).scalar_one_or_none()
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    # ADMIN_PLANTEL solo puede editar usuarios de su plantel
    if ades_user.plantel_id and usuario.plantel_id != ades_user.plantel_id:
        raise HTTPException(status_code=403, detail="No puede editar usuarios de otro plantel")

    if data.rol_id is not None:
        rol = await db.get(Rol, data.rol_id)
        if not rol:
            raise HTTPException(status_code=404, detail="Rol no encontrado")
        # ADMIN_PLANTEL no puede asignar ADMIN_GLOBAL
        if rol.nivel_acceso < ades_user.nivel_acceso:
            raise HTTPException(status_code=403, detail="No puede asignar un rol de mayor jerarquía")
        usuario.rol_id = data.rol_id

    if data.plantel_id is not None or 'plantel_id' in data.model_fields_set:
        usuario.plantel_id = data.plantel_id
    if data.nivel_educativo_id is not None or 'nivel_educativo_id' in data.model_fields_set:
        usuario.nivel_educativo_id = data.nivel_educativo_id
    if data.is_active is not None:
        usuario.is_active = data.is_active

    await db.commit()
    await db.refresh(usuario)

    nombre_plantel = None
    nombre_nivel   = None
    if usuario.plantel_id:
        p = await db.get(Plantel, usuario.plantel_id)
        nombre_plantel = p.nombre_plantel if p else None
    if usuario.nivel_educativo_id:
        n = await db.get(NivelEducativo, usuario.nivel_educativo_id)
        nombre_nivel = n.nombre_nivel if n else None

    # Recargar rol actualizado
    rol_obj = await db.get(Rol, usuario.rol_id)
    return UsuarioAdminOut(
        id=usuario.id,
        nombre_usuario=usuario.nombre_usuario,
        email_institucional=usuario.email_institucional,
        nombre_completo=usuario.persona.nombre_completo if usuario.persona else "",
        rol=rol_obj.nombre_rol if rol_obj else "",
        nivel_acceso=rol_obj.nivel_acceso if rol_obj else 99,
        plantel_id=usuario.plantel_id,
        nivel_educativo_id=usuario.nivel_educativo_id,
        nombre_plantel=nombre_plantel,
        nombre_nivel=nombre_nivel,
        is_active=usuario.is_active,
    )


# ══════════════════════════════════════════════════════════════════════════════
# MARCA / IDENTIDAD INSTITUCIONAL
# ══════════════════════════════════════════════════════════════════════════════



class MarcaItemOut(AdesSchema):
    tipo_elemento: str
    texto_elemento: str | None = None
    url_archivo: str | None = None
    color_hex: str | None = None
    descripcion: str | None = None


class MarcaItemUpdate(AdesSchema):
    tipo_elemento: str
    valor: str | None = None


@router.get("/marca", response_model=list[MarcaItemOut])
async def obtener_marca(
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Devuelve todos los elementos de identidad institucional."""
    _require_admin(ades_user)
    q = select(IdentidadInstitucional).where(
        IdentidadInstitucional.is_active == True,
        IdentidadInstitucional.plantel_id == None,
    ).order_by(IdentidadInstitucional.tipo_elemento)
    items = (await db.execute(q)).scalars().all()
    return [MarcaItemOut(
        tipo_elemento=i.tipo_elemento,
        texto_elemento=i.texto_elemento,
        url_archivo=i.url_archivo,
        color_hex=i.color_hex,
        descripcion=getattr(i, 'descripcion', None),
    ) for i in items]


@router.put("/marca")
async def actualizar_marca(
    items: list[MarcaItemUpdate],
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Actualiza los elementos de identidad institucional."""
    _require_admin(ades_user)
    if ades_user.nivel_acceso > 0:
        raise HTTPException(status_code=403, detail="Solo ADMIN_GLOBAL puede modificar la identidad")

    for item in items:
        q = select(IdentidadInstitucional).where(
            IdentidadInstitucional.tipo_elemento == item.tipo_elemento,
            IdentidadInstitucional.plantel_id == None,
            IdentidadInstitucional.is_active == True,
        )
        registro = (await db.execute(q)).scalar_one_or_none()

        if registro:
            valor = item.valor or ''
            # Decidir qué campo actualizar según el tipo
            if 'COLOR' in item.tipo_elemento:
                registro.color_hex = valor
            elif 'URL' in item.tipo_elemento or item.tipo_elemento in ('LOGO', 'FAVICON_URL', 'LOGO_URL'):
                registro.url_archivo = valor
            else:
                registro.texto_elemento = valor
        else:
            # Crear nuevo registro
            nuevo = IdentidadInstitucional(tipo_elemento=item.tipo_elemento)
            valor = item.valor or ''
            if 'COLOR' in item.tipo_elemento:
                nuevo.color_hex = valor
            elif 'URL' in item.tipo_elemento:
                nuevo.url_archivo = valor
            else:
                nuevo.texto_elemento = valor
            db.add(nuevo)

    await db.commit()
    return {"ok": True, "updated": len(items)}


# ══════════════════════════════════════════════════════════════════════════════
# PLANTELES
# ══════════════════════════════════════════════════════════════════════════════

class PlantelAdminUpdate(AdesSchema):
    nombre_plantel: str | None = Field(None, min_length=2, max_length=100)
    clave_ct: str | None = None


@router.patch("/planteles/{plantel_id}", response_model=PlantelOut)
async def actualizar_plantel(
    plantel_id: uuid.UUID,
    data: PlantelAdminUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    if ades_user.nivel_acceso > 0:
        raise HTTPException(status_code=403, detail="Solo ADMIN_GLOBAL puede editar planteles")

    plantel = await db.get(Plantel, plantel_id)
    if not plantel:
        raise HTTPException(status_code=404, detail="Plantel no encontrado")

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(plantel, field, value)
    await db.commit()
    await db.refresh(plantel)
    return plantel


# ══════════════════════════════════════════════════════════════════════════════
# GRUPOS (admin)
# ══════════════════════════════════════════════════════════════════════════════

class GrupoAdminUpdate(AdesSchema):
    nombre_grupo: str | None = Field(None, min_length=1, max_length=10)
    capacidad_maxima: int | None = Field(None, ge=1, le=60)
    turno: str | None = None
    profesor_titular_id: uuid.UUID | None = None
    is_active: bool | None = None


@router.get("/grupos", response_model=list[GrupoDetalle])
async def listar_grupos_admin(
    plantel_id: uuid.UUID | None = None,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    q = (
        select(Grupo)
        .options(selectinload(Grupo.grado).selectinload(Grado.nivel))
        .join(Grado, Grado.id == Grupo.grado_id)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
    )
    pid = ades_user.plantel_id or plantel_id
    if pid:
        q = q.where(Grado.plantel_id == pid)
    if ades_user.nivel_educativo_id:
        q = q.where(Grado.nivel_educativo_id == ades_user.nivel_educativo_id)
    if ciclo_id:
        q = q.where(Grupo.ciclo_escolar_id == ciclo_id)
    q = q.order_by(NivelEducativo.nombre_nivel, Grado.numero_grado, Grupo.nombre_grupo)
    grupos = (await db.execute(q)).scalars().all()

    result = []
    for g in grupos:
        out = GrupoDetalle.model_validate(g)
        if g.grado:
            out.nombre_grado = g.grado.nombre_grado
            out.numero_grado = g.grado.numero_grado
            if g.grado.nivel:
                out.nombre_nivel = g.grado.nivel.nombre_nivel
        result.append(out)
    return result


@router.post("/grupos", response_model=GrupoOut, status_code=201)
async def crear_grupo_admin(
    data: GrupoCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    grado = await db.get(Grado, data.grado_id)
    if not grado:
        raise HTTPException(status_code=404, detail="Grado no encontrado")
    ciclo = await db.get(CicloEscolar, data.ciclo_escolar_id)
    if not ciclo:
        raise HTTPException(status_code=404, detail="Ciclo escolar no encontrado")

    grupo = Grupo(**data.model_dump())
    db.add(grupo)
    await db.commit()
    await db.refresh(grupo)
    return grupo


@router.patch("/grupos/{grupo_id}", response_model=GrupoOut)
async def actualizar_grupo_admin(
    grupo_id: uuid.UUID,
    data: GrupoAdminUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    _require_admin(ades_user)
    grupo = await db.get(Grupo, grupo_id)
    if not grupo:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(grupo, field, value)
    await db.commit()
    await db.refresh(grupo)
    return grupo

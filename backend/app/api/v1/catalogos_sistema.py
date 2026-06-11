"""
FASE 26-A — CRUD de Catálogos Dinámicos y Variables del Sistema.
Solo accesible con nivel_acceso <= 1 (ADMIN_GLOBAL o ADMIN_PLANTEL).
"""
from __future__ import annotations
import uuid
import json
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models.sistema import Catalogo, CatalogoItem, VariableSistema
from app.schemas.sistema import (
    CatalogoCreate, CatalogoOut, CatalogoUpdate,
    CatalogoItemCreate, CatalogoItemOut, CatalogoItemUpdate,
    VariableCreate, VariableOut, VariableUpdate,
)
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.core.optimistic_locking import check_row_version, RowVersionConflict

router = APIRouter(tags=["catalogos-sistema"])


def _require_admin(user: AdesUser) -> AdesUser:
    if user.nivel_acceso > 1:
        raise HTTPException(status_code=403, detail="Se requiere rol de Administrador Global o de Plantel")
    return user


# ── CATÁLOGOS ─────────────────────────────────────────────────────────────────

@router.get("/catalogos", response_model=list[CatalogoOut])
async def listar_catalogos(
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Lista todos los catálogos activos con sus items. Accesible para todos los roles."""
    result = await db.execute(
        select(Catalogo).where(Catalogo.is_active == True).order_by(Catalogo.codigo)
    )
    return result.scalars().all()


@router.get("/catalogos/{catalogo_id}", response_model=CatalogoOut)
async def obtener_catalogo(
    catalogo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    return cat


@router.post("/catalogos", response_model=CatalogoOut, status_code=201)
async def crear_catalogo(
    data: CatalogoCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    cat = Catalogo(
        **data.model_dump(),
        usuario_creacion=str(user.id),
        usuario_modificacion=str(user.id),
    )
    db.add(cat)
    await db.commit()
    await db.refresh(cat)
    return cat


@router.patch("/catalogos/{catalogo_id}", response_model=CatalogoOut)
async def actualizar_catalogo(
    catalogo_id: uuid.UUID,
    data: CatalogoUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    try:
        check_row_version(cat, data.row_version)
    except RowVersionConflict as e:
        raise HTTPException(409, str(e))
    for field, value in data.model_dump(exclude={'row_version'}, exclude_unset=True).items():
        setattr(cat, field, value)
    cat.row_version += 1
    cat.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(cat)
    return cat


@router.delete("/catalogos/{catalogo_id}", status_code=204)
async def eliminar_catalogo(
    catalogo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    cat.is_active = False
    cat.usuario_modificacion = str(user.id)
    # También desactivar items
    for item in cat.items:
        item.is_active = False
        item.usuario_modificacion = str(user.id)
    await db.commit()
    return None


# ── ITEMS DE CATÁLOGO ─────────────────────────────────────────────────────────

@router.post("/catalogos/{catalogo_id}/items", response_model=CatalogoItemOut, status_code=201)
async def agregar_item(
    catalogo_id: uuid.UUID,
    data: CatalogoItemCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    item = CatalogoItem(
        **data.model_dump(),
        catalogo_id=catalogo_id,
        usuario_creacion=str(user.id),
        usuario_modificacion=str(user.id),
    )
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return item


@router.patch("/catalogos/items/{item_id}", response_model=CatalogoItemOut)
async def actualizar_item(
    item_id: uuid.UUID,
    data: CatalogoItemUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    item = await db.get(CatalogoItem, item_id)
    if not item:
        raise HTTPException(404, "Item no encontrado")
    try:
        check_row_version(item, data.row_version)
    except RowVersionConflict as e:
        raise HTTPException(409, str(e))
    for field, value in data.model_dump(exclude={'row_version'}, exclude_unset=True).items():
        setattr(item, field, value)
    item.row_version += 1
    item.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(item)
    return item


@router.delete("/catalogos/items/{item_id}", status_code=204)
async def eliminar_item(
    item_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    item = await db.get(CatalogoItem, item_id)
    if not item:
        raise HTTPException(404, "Item no encontrado")
    item.is_active = False
    item.usuario_modificacion = str(user.id)
    await db.commit()
    return None


@router.post("/catalogos/{catalogo_id}/items/reorder", status_code=200)
async def reordenar_items(
    catalogo_id: uuid.UUID,
    orden_data: list[dict], # [{"id": "uuid", "orden": 1}]
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    
    # Mapear ids a ordenes
    orden_map = {str(item.get("id")): item.get("orden") for item in orden_data if item.get("id") and item.get("orden") is not None}
    
    for item in cat.items:
        if str(item.id) in orden_map:
            item.orden = orden_map[str(item.id)]
            item.usuario_modificacion = str(user.id)
    
    await db.commit()
    return {"message": "Items reordenados correctamente"}


# ── VARIABLES DEL SISTEMA ─────────────────────────────────────────────────────

@router.get("/config/variables", response_model=list[VariableOut])
async def listar_variables(
    grupo: str | None = None,
    search: str | None = None,
    tipo_valor: str | None = None,
    solo_lectura: bool | None = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    q = select(VariableSistema).where(VariableSistema.is_active == True)
    if grupo:
        q = q.where(VariableSistema.grupo == grupo)
    if search:
        q = q.where(VariableSistema.nombre.ilike(f"%{search}%"))
    if tipo_valor:
        q = q.where(VariableSistema.tipo_valor == tipo_valor)
    if solo_lectura is not None:
        q = q.where(VariableSistema.solo_lectura == solo_lectura)
    q = q.order_by(VariableSistema.grupo, VariableSistema.nombre)
    result = await db.execute(q)
    vars_ = result.scalars().all()
    return [VariableOut.model_validate(v) for v in vars_]


@router.get("/config/public", response_model=list[VariableOut])
async def variables_publicas(db: AsyncSession = Depends(get_db)):
    """Variables no sensibles que el frontend necesita antes de login."""
    result = await db.execute(
        select(VariableSistema).where(
            VariableSistema.is_active == True,
            VariableSistema.tipo_valor != 'PASSWORD',
            VariableSistema.encriptado == False,
            VariableSistema.nombre.in_([
                'NOMBRE_INSTITUCION', 'NOMBRE_SISTEMA',
                'JSON_CONFIG_UI', 'JSON_MARCA', 'URL_PORTAL',
            ])
        )
    )
    return [VariableOut.model_validate(v) for v in result.scalars().all()]


@router.post("/config/variables", response_model=VariableOut, status_code=201)
async def crear_variable(
    data: VariableCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    existing = (await db.execute(
        select(VariableSistema).where(VariableSistema.nombre == data.nombre)
    )).scalar_one_or_none()
    if existing:
        raise HTTPException(409, f"Ya existe una variable con nombre '{data.nombre}'")
    
    # Validar tipo de valor
    _validar_valor_por_tipo(data.valor, data.tipo_valor)
    var = VariableSistema(
        **data.model_dump(),
        usuario_creacion=str(user.id),
        usuario_modificacion=str(user.id),
    )
    db.add(var)
    await db.commit()
    await db.refresh(var)
    return VariableOut.model_validate(var)


def _validar_valor_por_tipo(valor: str | None, tipo_valor: str):
    if not valor:
        return
    if tipo_valor == 'JSON':
        try:
            json.loads(valor)
        except Exception:
            raise HTTPException(422, "El valor proporcionado no es un JSON válido")
    elif tipo_valor == 'NUMERO':
        try:
            float(valor)
        except Exception:
            raise HTTPException(422, "El valor proporcionado no es un número válido")
    elif tipo_valor == 'FECHA':
        try:
            # Simple check or full isoformat check
            datetime.fromisoformat(valor.replace('Z', '+00:00'))
        except Exception:
            raise HTTPException(422, "El valor proporcionado no es una fecha válida (ISO 8601)")


@router.patch("/config/variables/{nombre}", response_model=VariableOut)
async def actualizar_variable(
    nombre: str,
    data: VariableUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    _require_admin(user)
    var = (await db.execute(
        select(VariableSistema).where(VariableSistema.nombre == nombre)
    )).scalar_one_or_none()
    if not var:
        raise HTTPException(404, f"Variable '{nombre}' no encontrada")
    if var.solo_lectura:
        raise HTTPException(403, f"La variable '{nombre}' es de solo lectura y no puede modificarse desde la UI")
    try:
        check_row_version(var, data.row_version)
    except RowVersionConflict as e:
        raise HTTPException(409, str(e))
    
    if data.valor is not None:
        # Validar tipo antes de actualizar
        _validar_valor_por_tipo(data.valor, var.tipo_valor)
        var.valor = data.valor
    if data.descripcion is not None:
        var.descripcion = data.descripcion
    if data.grupo is not None:
        var.grupo = data.grupo
    var.row_version += 1
    var.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(var)
    return VariableOut.model_validate(var)

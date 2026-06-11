"""
FASE 26-B — Endpoint que devuelve el árbol de menú filtrado según el rol del usuario.
El frontend Angular lo consume al iniciar sesión para renderizar la navegación,
recreando el comportamiento de Navigation Menus de Oracle APEX.
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models.menus import Menu, MenuRol
from app.schemas.menus import MenuOut
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser

router = APIRouter(tags=["menus"])


@router.get("/menus/mi-menu", response_model=list[MenuOut])
async def obtener_mi_menu(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    """
    Devuelve el árbol de menús que el usuario puede ver según su rol.
    Si nivel_acceso == 0 o 1 (ADMIN_GLOBAL o ADMIN_PLANTEL), devuelve todos los menús activos.
    """
    if user.nivel_acceso <= 1:
        # Admin ve todo
        result = await db.execute(
            select(Menu).where(Menu.is_active == True).order_by(Menu.peso)
        )
        menus = result.scalars().all()
    else:
        # Filtrar por rol principal del usuario
        result = await db.execute(
            select(Menu)
            .join(MenuRol, Menu.id == MenuRol.menu_id)
            .where(
                Menu.is_active == True,
                MenuRol.rol_id == user.rol_id
            )
            .order_by(Menu.peso)
        )
        menus = result.scalars().all()

    return _construir_arbol(menus)


def _construir_arbol(menus):
    """Convierte lista plana en árbol parent→children."""
    por_id = {m.id: {
        "id": m.id, "label": m.label, "route": m.route,
        "icon": m.icon, "parent_id": m.parent_id, "peso": m.peso, "children": []
    } for m in menus}

    raiz = []
    for m in menus:
        nodo = por_id[m.id]
        if m.parent_id and m.parent_id in por_id:
            por_id[m.parent_id]["children"].append(nodo)
        elif not m.parent_id:
            raiz.append(nodo)

    return sorted(raiz, key=lambda x: x["peso"])

"""
Endpoints geográficos sobre el catálogo SEPOMEX (schema sepomex.*).
"""
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db

router = APIRouter(prefix="/geo", tags=["geograficos"])


@router.get("/estados")
async def listar_estados(db: AsyncSession = Depends(get_db)):
    """Lista estados vigentes de México desde el catálogo SEPOMEX."""
    try:
        result = await db.execute(text("""
            SELECT id, clave, nombre
            FROM sepomex.ctestados
            WHERE vigente = TRUE
            ORDER BY nombre
        """))
        return [dict(r) for r in result.mappings()]
    except Exception:
        # Fallback if sepomex not installed
        return []


@router.get("/municipios")
async def listar_municipios(
    estado_id: int = Query(..., description="ID del estado"),
    db: AsyncSession = Depends(get_db)
):
    """Lista municipios de un estado."""
    try:
        result = await db.execute(text("""
            SELECT id, clave, nombre
            FROM sepomex.ctmunicipios
            WHERE estado_id = :estado_id AND vigente = TRUE
            ORDER BY nombre
        """), {"estado_id": estado_id})
        return [dict(r) for r in result.mappings()]
    except Exception:
        return []


@router.get("/colonias")
async def buscar_colonias(
    cp: str = Query(None, description="Código postal de 5 dígitos"),
    municipio_id: int = Query(None, description="ID de municipio"),
    db: AsyncSession = Depends(get_db)
):
    """Busca colonias/asentamientos por código postal o municipio."""
    try:
        if cp:
            result = await db.execute(text("""
                SELECT
                    a.id,
                    a.nombre AS colonia,
                    cp.codigo_postal,
                    m.nombre AS municipio,
                    e.nombre AS estado
                FROM sepomex.ctasentamientos a
                JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id
                JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id
                JOIN sepomex.ctestados e ON e.id = m.estado_id
                WHERE cp.codigo_postal = :cp
                  AND a.vigente = TRUE
                ORDER BY a.nombre
            """), {"cp": cp})
        elif municipio_id:
            result = await db.execute(text("""
                SELECT a.id, a.nombre AS colonia,
                       cp.codigo_postal
                FROM sepomex.ctasentamientos a
                JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id
                WHERE a.municipio_id = :municipio_id AND a.vigente = TRUE
                ORDER BY a.nombre
            """), {"municipio_id": municipio_id})
        else:
            return []

        return [dict(r) for r in result.mappings()]
    except Exception:
        return []


@router.get("/buscar-cp/{cp}")
async def buscar_por_cp(cp: str, db: AsyncSession = Depends(get_db)):
    """Devuelve municipio + estado + colonias para un CP (útil para autocompletar)."""
    try:
        result = await db.execute(text("""
            SELECT
                e.nombre AS estado,
                e.id AS estado_id,
                m.nombre AS municipio,
                m.id AS municipio_id,
                cp.codigo_postal AS cp,
                json_agg(json_build_object('id', a.id, 'colonia', a.nombre)
                         ORDER BY a.nombre) AS colonias
            FROM sepomex.ctasentamientos a
            JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id
            JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id
            JOIN sepomex.ctestados e ON e.id = m.estado_id
            WHERE cp.codigo_postal = :cp AND a.vigente = TRUE
            GROUP BY e.nombre, e.id, m.nombre, m.id, cp.codigo_postal
        """), {"cp": cp})
        row = result.mappings().first()
        return dict(row) if row else None
    except Exception:
        return None

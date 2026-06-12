from typing import Optional
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, field_validator
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.security import get_ades_user, AdesUser
from app.core.database import get_db

router = APIRouter(prefix="/condiciones-cronicas", tags=["condiciones crónicas"])

_TIPOS = {
    'EPILEPSIA', 'DIABETES', 'ASMA', 'ALERGIA', 'CARDIACA',
    'HIPERTENSION', 'DISCAPACIDAD_VISUAL', 'DISCAPACIDAD_AUDITIVA', 'OTRA',
}


class CondicionCreate(BaseModel):
    alumno_id: UUID
    tipo_condicion: str
    descripcion: str
    medicacion_nombre: Optional[str] = None
    dosis: Optional[str] = None
    frecuencia: Optional[str] = None
    alergias: Optional[str] = None
    medico_responsable: Optional[str] = None
    telefono_medico: Optional[str] = None

    @field_validator('tipo_condicion')
    @classmethod
    def validar_tipo(cls, v: str) -> str:
        v = v.upper()
        if v not in _TIPOS:
            raise ValueError(f'tipo_condicion debe ser uno de: {sorted(_TIPOS)}')
        return v


class CondicionPatch(BaseModel):
    descripcion: Optional[str] = None
    medicacion_nombre: Optional[str] = None
    dosis: Optional[str] = None
    frecuencia: Optional[str] = None
    alergias: Optional[str] = None
    medico_responsable: Optional[str] = None
    telefono_medico: Optional[str] = None
    activa: Optional[bool] = None


@router.get("")
async def listar_condiciones(
    alumno_id: Optional[UUID] = None,
    tipo_condicion: Optional[str] = None,
    solo_activas: bool = True,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    filtros = ["c.is_active = TRUE"]
    params: dict = {}
    if alumno_id:
        filtros.append("c.alumno_id = :alumno_id")
        params["alumno_id"] = str(alumno_id)
    if tipo_condicion:
        filtros.append("c.tipo_condicion = :tipo")
        params["tipo"] = tipo_condicion.upper()
    if solo_activas:
        filtros.append("c.activa = TRUE")

    where = " AND ".join(filtros)
    sql = text(f"""
        SELECT c.id, c.alumno_id,
               e.numero_control,
               p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
               c.tipo_condicion, c.descripcion,
               c.medicacion_nombre, c.dosis, c.frecuencia, c.alergias,
               c.medico_responsable, c.telefono_medico, c.activa,
               c.fecha_creacion
        FROM ades_condiciones_cronicas c
        JOIN ades_estudiantes e ON e.id = c.alumno_id
        JOIN ades_personas p ON p.id = e.persona_id
        WHERE {where}
        ORDER BY p.apellido_paterno, p.nombre, c.tipo_condicion
    """)
    result = await db.execute(sql, params)
    return [dict(r._mapping) for r in result.fetchall()]


@router.post("", status_code=201)
async def crear_condicion(
    body: CondicionCreate,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Sin permisos")
    sql = text("""
        INSERT INTO ades_condiciones_cronicas
            (alumno_id, tipo_condicion, descripcion, medicacion_nombre, dosis,
             frecuencia, alergias, medico_responsable, telefono_medico,
             usuario_creacion, usuario_modificacion)
        VALUES
            (:alumno_id, :tipo, :desc, :med_nombre, :dosis,
             :frecuencia, :alergias, :medico, :tel,
             :usr, :usr)
        RETURNING id
    """)
    result = await db.execute(sql, {
        "alumno_id": str(body.alumno_id),
        "tipo": body.tipo_condicion,
        "desc": body.descripcion,
        "med_nombre": body.medicacion_nombre,
        "dosis": body.dosis,
        "frecuencia": body.frecuencia,
        "alergias": body.alergias,
        "medico": body.medico_responsable,
        "tel": body.telefono_medico,
        "usr": user.id,
    })
    await db.commit()
    row = result.fetchone()
    return {"id": str(row[0])}


@router.get("/alumno/{alumno_id}/alerta")
async def alerta_emergencia(
    alumno_id: UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    """Condiciones activas del alumno para pantalla de emergencia (SB-007)."""
    sql = text("""
        SELECT c.tipo_condicion, c.descripcion, c.medicacion_nombre,
               c.dosis, c.frecuencia, c.alergias,
               c.medico_responsable, c.telefono_medico,
               p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
               e.numero_control,
               cf.nombre_completo AS contacto_emergencia,
               cf.telefono        AS tel_emergencia
        FROM ades_condiciones_cronicas c
        JOIN ades_estudiantes e ON e.id = c.alumno_id
        JOIN ades_personas p ON p.id = e.persona_id
        LEFT JOIN ades_contactos_familiares cf ON cf.alumno_id = c.alumno_id
            AND cf.es_contacto_emergencia = TRUE
            AND cf.is_active = TRUE
        WHERE c.alumno_id = :alumno_id AND c.activa = TRUE AND c.is_active = TRUE
        ORDER BY c.tipo_condicion
    """)
    result = await db.execute(sql, {"alumno_id": str(alumno_id)})
    return [dict(r._mapping) for r in result.fetchall()]


@router.get("/{condicion_id}")
async def obtener_condicion(
    condicion_id: UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        text("SELECT * FROM ades_condiciones_cronicas WHERE id = :id AND is_active = TRUE"),
        {"id": str(condicion_id)},
    )
    row = result.fetchone()
    if not row:
        raise HTTPException(404, "Condición no encontrada")
    return dict(row._mapping)


@router.patch("/{condicion_id}")
async def actualizar_condicion(
    condicion_id: UUID,
    body: CondicionPatch,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Sin permisos")
    sets, params = [], {"id": str(condicion_id), "usr": user.id}
    for field in ["descripcion", "medicacion_nombre", "dosis", "frecuencia",
                  "alergias", "medico_responsable", "telefono_medico", "activa"]:
        val = getattr(body, field)
        if val is not None:
            sets.append(f"{field} = :{field}")
            params[field] = val
    if not sets:
        raise HTTPException(400, "Sin campos a actualizar")
    sets.append("usuario_modificacion = :usr")
    await db.execute(
        text(f"UPDATE ades_condiciones_cronicas SET {', '.join(sets)} WHERE id = :id AND is_active = TRUE"),
        params,
    )
    await db.commit()
    return {"ok": True}


@router.delete("/{condicion_id}", status_code=204)
async def eliminar_condicion(
    condicion_id: UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > 2:
        raise HTTPException(403, "Sin permisos")
    await db.execute(
        text("UPDATE ades_condiciones_cronicas SET is_active = FALSE, usuario_modificacion = :usr WHERE id = :id"),
        {"id": str(condicion_id), "usr": user.id},
    )
    await db.commit()

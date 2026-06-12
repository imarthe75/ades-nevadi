from typing import Optional
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, field_validator
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.security import get_ades_user, AdesUser
from app.core.database import get_db

router = APIRouter(prefix="/justificaciones", tags=["justificaciones faltas"])

_TIPOS = {'MEDICA', 'FAMILIAR', 'DEPORTIVA', 'CULTURAL', 'ADMINISTRATIVA', 'OTRA'}


class JustificacionCreate(BaseModel):
    asistencia_id: UUID
    tipo_justificacion: str = 'MEDICA'
    motivo: str
    documento_url: Optional[str] = None

    @field_validator('tipo_justificacion')
    @classmethod
    def validar_tipo(cls, v: str) -> str:
        v = v.upper()
        if v not in _TIPOS:
            raise ValueError(f'tipo_justificacion debe ser uno de: {sorted(_TIPOS)}')
        return v


class ResolucionIn(BaseModel):
    accion: str
    motivo_rechazo: Optional[str] = None

    @field_validator('accion')
    @classmethod
    def validar_accion(cls, v: str) -> str:
        v = v.upper()
        if v not in ('APROBAR', 'RECHAZAR'):
            raise ValueError('accion debe ser APROBAR o RECHAZAR')
        return v


@router.get("")
async def listar_justificaciones(
    estudiante_id: Optional[UUID] = None,
    estado: Optional[str] = None,
    grupo_id: Optional[UUID] = None,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    filtros = ["j.is_active = TRUE"]
    params: dict = {}
    if estudiante_id:
        filtros.append("a.estudiante_id = :est_id")
        params["est_id"] = str(estudiante_id)
    if estado:
        filtros.append("j.estado = :estado")
        params["estado"] = estado.upper()
    if grupo_id:
        filtros.append("cl.grupo_id = :grupo_id")
        params["grupo_id"] = str(grupo_id)

    where = " AND ".join(filtros)
    sql = text(f"""
        SELECT j.id, j.asistencia_id, j.tipo_justificacion, j.motivo,
               j.documento_url, j.estado, j.motivo_rechazo, j.fecha_resolucion,
               j.fecha_creacion,
               a.estudiante_id,
               p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
               a.estatus_asistencia,
               cl.fecha_clase
        FROM ades_justificaciones_falta j
        JOIN ades_asistencias a  ON a.id = j.asistencia_id
        JOIN ades_estudiantes e  ON e.id = a.estudiante_id
        JOIN ades_personas p     ON p.id = e.persona_id
        LEFT JOIN ades_clases cl ON cl.id = a.clase_id
        WHERE {where}
        ORDER BY j.fecha_creacion DESC
    """)
    result = await db.execute(sql, params)
    return [dict(r._mapping) for r in result.fetchall()]


@router.post("", status_code=201)
async def crear_justificacion(
    body: JustificacionCreate,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    asist = await db.execute(
        text("SELECT id, estatus_asistencia FROM ades_asistencias WHERE id = :id AND is_active = TRUE"),
        {"id": str(body.asistencia_id)},
    )
    asist_row = asist.fetchone()
    if not asist_row:
        raise HTTPException(404, "Asistencia no encontrada")
    if asist_row.estatus_asistencia == "PRESENTE":
        raise HTTPException(400, "No se puede justificar una asistencia PRESENTE")

    existing = await db.execute(
        text("SELECT id FROM ades_justificaciones_falta WHERE asistencia_id = :id AND is_active = TRUE"),
        {"id": str(body.asistencia_id)},
    )
    if existing.fetchone():
        raise HTTPException(409, "Ya existe una justificación para esta asistencia")

    result = await db.execute(
        text("""
        INSERT INTO ades_justificaciones_falta
            (asistencia_id, tipo_justificacion, motivo, documento_url,
             usuario_creacion, usuario_modificacion)
        VALUES (:asist_id, :tipo, :motivo, :doc_url, :usr, :usr)
        RETURNING id
        """),
        {
            "asist_id": str(body.asistencia_id),
            "tipo": body.tipo_justificacion,
            "motivo": body.motivo,
            "doc_url": body.documento_url,
            "usr": user.id,
        },
    )
    new_id = str(result.fetchone()[0])
    await db.execute(
        text("UPDATE ades_asistencias SET justificacion_id = :jid WHERE id = :aid"),
        {"jid": new_id, "aid": str(body.asistencia_id)},
    )
    await db.commit()
    return {"id": new_id}


@router.post("/{justificacion_id}/resolver")
async def resolver_justificacion(
    justificacion_id: UUID,
    body: ResolucionIn,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Sin permisos para resolver justificaciones")
    result = await db.execute(
        text("SELECT id, estado FROM ades_justificaciones_falta WHERE id = :id AND is_active = TRUE"),
        {"id": str(justificacion_id)},
    )
    just = result.fetchone()
    if not just:
        raise HTTPException(404, "Justificación no encontrada")
    if just.estado != "PENDIENTE":
        raise HTTPException(400, f"La justificación ya fue resuelta: {just.estado}")
    if body.accion == "RECHAZAR" and not body.motivo_rechazo:
        raise HTTPException(400, "motivo_rechazo es obligatorio al rechazar")

    nuevo_estado = "APROBADA" if body.accion == "APROBAR" else "RECHAZADA"
    await db.execute(
        text("""
        UPDATE ades_justificaciones_falta
        SET estado = :estado, aprobada_por = :usr,
            fecha_resolucion = now(), motivo_rechazo = :motivo_rec,
            usuario_modificacion = :usr
        WHERE id = :id
        """),
        {
            "estado": nuevo_estado,
            "usr": user.id,
            "motivo_rec": body.motivo_rechazo,
            "id": str(justificacion_id),
        },
    )
    await db.commit()
    return {"estado": nuevo_estado}


@router.get("/{justificacion_id}")
async def obtener_justificacion(
    justificacion_id: UUID,
    user: AdesUser = Depends(get_ades_user),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        text("SELECT * FROM ades_justificaciones_falta WHERE id = :id AND is_active = TRUE"),
        {"id": str(justificacion_id)},
    )
    row = result.fetchone()
    if not row:
        raise HTTPException(404, "Justificación no encontrada")
    return dict(row._mapping)

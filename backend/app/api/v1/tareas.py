"""
/tareas — Gestión de tareas, entregas y calificación.
Integración con MinIO para archivos de entrega.
"""
from __future__ import annotations
import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from app.core.config import settings
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.operacion import Tarea, TareaEntrega, CalificacionEntrega, Archivo
from app.schemas.operacion import (
    TareaCreate, TareaUpdate, TareaOut,
    EntregaOut, CalificarEntregaIn, CalificacionEntregaOut, ArchivoOut,
)

router = APIRouter(prefix="/tareas", tags=["tareas"])


def _minio_client():
    """Cliente MinIO lazy-initialized."""
    from minio import Minio
    return Minio(
        endpoint=settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        secure=settings.MINIO_SECURE,
    )


# ── CRUD Tareas ───────────────────────────────────────────────────────────────

@router.get("", response_model=list[TareaOut])
async def listar_tareas(
    grupo_id: uuid.UUID | None = None,
    materia_id: uuid.UUID | None = None,
    origen: str | None = Query(None, description="MANUAL | AUTO"),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Tarea).where(Tarea.is_active == True)
    if grupo_id:
        q = q.where(Tarea.grupo_id == grupo_id)
    if materia_id:
        q = q.where(Tarea.materia_id == materia_id)
    if origen:
        q = q.where(Tarea.origen == origen.upper())
    q = q.order_by(Tarea.fecha_entrega)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/{tarea_id}", response_model=TareaOut)
async def obtener_tarea(
    tarea_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Tarea, tarea_id)
    if not row:
        raise HTTPException(status_code=404, detail="Tarea no encontrada")
    return row


@router.post("", response_model=TareaOut, status_code=status.HTTP_201_CREATED)
async def crear_tarea(
    data: TareaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    tarea = Tarea(**data.model_dump(), origen="MANUAL")
    db.add(tarea)
    await db.commit()
    await db.refresh(tarea)
    return tarea


@router.patch("/{tarea_id}", response_model=TareaOut)
async def actualizar_tarea(
    tarea_id: uuid.UUID,
    data: TareaUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    tarea = await db.get(Tarea, tarea_id)
    if not tarea:
        raise HTTPException(status_code=404, detail="Tarea no encontrada")
    for field, value in data.model_dump(exclude_none=True).items():
        setattr(tarea, field, value)
    await db.commit()
    await db.refresh(tarea)
    return tarea


@router.delete("/{tarea_id}", status_code=status.HTTP_204_NO_CONTENT)
async def eliminar_tarea(
    tarea_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    tarea = await db.get(Tarea, tarea_id)
    if not tarea:
        raise HTTPException(status_code=404, detail="Tarea no encontrada")
    tarea.is_active = False
    await db.commit()


# ── Entregas ──────────────────────────────────────────────────────────────────

@router.get("/{tarea_id}/entregas", response_model=list[EntregaOut])
async def listar_entregas(
    tarea_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    rows = await db.execute(
        select(TareaEntrega)
        .where(TareaEntrega.tarea_id == tarea_id, TareaEntrega.is_active == True)
        .order_by(TareaEntrega.fccreacion)
    )
    return rows.scalars().all()


@router.post("/{tarea_id}/entregas/{estudiante_id}/archivo",
             response_model=ArchivoOut, status_code=status.HTTP_201_CREATED)
async def subir_entrega(
    tarea_id: uuid.UUID,
    estudiante_id: uuid.UUID,
    archivo: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Sube el archivo de entrega de un alumno a MinIO y registra/actualiza el registro.
    """
    tarea = await db.get(Tarea, tarea_id)
    if not tarea:
        raise HTTPException(status_code=404, detail="Tarea no encontrada")

    # Upsert registro de entrega
    entrega = (await db.execute(
        select(TareaEntrega).where(
            TareaEntrega.tarea_id == tarea_id,
            TareaEntrega.estudiante_id == estudiante_id,
        )
    )).scalar_one_or_none()

    now = datetime.now(timezone.utc)
    es_tarde = tarea.fecha_entrega < now.date()

    if not entrega:
        entrega = TareaEntrega(
            tarea_id=tarea_id,
            estudiante_id=estudiante_id,
            fecha_entrega=now,
            es_tarde=es_tarde,
            estatus_entrega="TARDE" if (es_tarde and not tarea.permite_entrega_tarde) else "ENTREGADO",
        )
        db.add(entrega)
        await db.flush()
    else:
        entrega.fecha_entrega = now
        entrega.es_tarde = es_tarde
        if entrega.estatus_entrega == "PENDIENTE":
            entrega.estatus_entrega = "TARDE" if (es_tarde and not tarea.permite_entrega_tarde) else "ENTREGADO"

    # Subir a MinIO
    object_name = f"entregas/{tarea_id}/{estudiante_id}/{archivo.filename}"
    contenido = await archivo.read()

    try:
        import io
        client = _minio_client()
        if not client.bucket_exists(settings.MINIO_BUCKET):
            client.make_bucket(settings.MINIO_BUCKET)
        client.put_object(
            bucket_name=settings.MINIO_BUCKET,
            object_name=object_name,
            data=io.BytesIO(contenido),
            length=len(contenido),
            content_type=archivo.content_type or "application/octet-stream",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error al subir archivo a MinIO: {e}")

    # Registrar en ades_archivos
    arch = Archivo(
        nombre_original=archivo.filename or "archivo",
        nombre_almacenado=object_name,
        bucket=settings.MINIO_BUCKET,
        mime_type=archivo.content_type,
        tamanio_bytes=len(contenido),
        entidad_tipo="TAREA_ENTREGA",
        entidad_id=entrega.id,
    )
    db.add(arch)
    await db.commit()
    await db.refresh(arch)

    return ArchivoOut(
        **{k: getattr(arch, k) for k in ArchivoOut.model_fields if hasattr(arch, k)},
    )


@router.get("/{tarea_id}/entregas/{estudiante_id}/archivos", response_model=list[ArchivoOut])
async def archivos_entrega(
    tarea_id: uuid.UUID,
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    entrega = (await db.execute(
        select(TareaEntrega).where(
            TareaEntrega.tarea_id == tarea_id,
            TareaEntrega.estudiante_id == estudiante_id,
        )
    )).scalar_one_or_none()
    if not entrega:
        raise HTTPException(status_code=404, detail="No hay entrega registrada")

    archivos = (await db.execute(
        select(Archivo).where(
            Archivo.entidad_tipo == "TAREA_ENTREGA",
            Archivo.entidad_id == entrega.id,
            Archivo.is_active == True,
        )
    )).scalars().all()

    # Generar URLs prefirmadas para descarga
    resultados = []
    try:
        from datetime import timedelta
        client = _minio_client()
        for a in archivos:
            url = client.presigned_get_object(a.bucket, a.nombre_almacenado, expires=timedelta(hours=1))
            item = ArchivoOut.model_validate(a)
            item.url_descarga = url
            resultados.append(item)
    except Exception:
        resultados = [ArchivoOut.model_validate(a) for a in archivos]

    return resultados


# ── Calificar entrega ─────────────────────────────────────────────────────────

@router.post("/{tarea_id}/entregas/{estudiante_id}/calificar",
             response_model=CalificacionEntregaOut, status_code=status.HTTP_201_CREATED)
async def calificar_entrega(
    tarea_id: uuid.UUID,
    estudiante_id: uuid.UUID,
    data: CalificarEntregaIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    entrega = (await db.execute(
        select(TareaEntrega).where(
            TareaEntrega.tarea_id == tarea_id,
            TareaEntrega.estudiante_id == estudiante_id,
        )
    )).scalar_one_or_none()
    if not entrega:
        raise HTTPException(status_code=404, detail="Entrega no encontrada")

    # Upsert calificación de entrega
    existing = (await db.execute(
        select(CalificacionEntrega).where(CalificacionEntrega.entrega_id == entrega.id)
    )).scalar_one_or_none()

    if existing:
        existing.calificacion = data.calificacion
        existing.comentarios_docente = data.comentario_docente
        existing.fecha_calificacion = datetime.now(timezone.utc)
        cal = existing
    else:
        cal = CalificacionEntrega(
            tarea_entrega_id=entrega.id,
            calificacion=data.calificacion,
            comentarios_docente=data.comentario_docente,
        )
        db.add(cal)

    entrega.estatus_entrega = "CALIFICADO"
    await db.commit()
    await db.refresh(cal)
    return cal

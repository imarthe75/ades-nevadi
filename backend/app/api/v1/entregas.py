"""
Entregas de alumnos — subida de archivos a MinIO y calificación individual.
"""
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user
from typing import Optional
from pydantic import BaseModel
from minio import Minio
from minio.error import S3Error
import os
import uuid
import io

router = APIRouter(prefix="/entregas", tags=["Gradebook – Entregas"])

# ── MinIO client ──────────────────────────────────────────────
def _minio_client() -> Minio:
    return Minio(
        os.getenv("MINIO_ENDPOINT", "ades-minio:9000").replace("http://", "").replace("https://", ""),
        access_key=os.getenv("MINIO_ACCESS_KEY", "ades_minio"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "ades_minio_secret"),
        secure=os.getenv("MINIO_SECURE", "false").lower() == "true",
    )

BUCKET = "tareas-entregas"


def _ensure_bucket():
    try:
        client = _minio_client()
        if not client.bucket_exists(BUCKET):
            client.make_bucket(BUCKET)
    except Exception:
        pass


class CalificarIn(BaseModel):
    calificacion: float
    comentario: Optional[str] = None


# ── GET /entregas/alumno/{alumno_id} ─────────────────────────
@router.get("/alumno/{alumno_id}")
async def entregas_del_alumno(
    alumno_id: str,
    periodo_id: Optional[str] = None,
    materia_id: Optional[str] = None,
    solo_pendientes: bool = False,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    filters = "WHERE te.estudiante_id = :eid AND te.is_active = TRUE"
    params: dict = {"eid": alumno_id}
    if periodo_id:
        filters += " AND t.periodo_evaluacion_id = :pid"
        params["pid"] = periodo_id
    if materia_id:
        filters += " AND t.materia_id = :mid"
        params["mid"] = materia_id
    if solo_pendientes:
        filters += " AND te.estatus_entrega = 'PENDIENTE'"

    rows = await db.execute(
        text(f"""
            SELECT te.id, te.tarea_id, te.estatus_entrega,
                   te.fecha_entrega, te.es_tarde,
                   te.calificacion_obtenida, te.comentario_profesor,
                   te.archivo_url,
                   te.fecha_calificacion_docente,
                   t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite,
                   t.puntaje_maximo,
                   m.nombre_materia,
                   pe.nombre_periodo,
                   (t.fecha_entrega < CURRENT_DATE AND te.estatus_entrega = 'PENDIENTE') AS vencida
              FROM ades_tareas_entregas te
              JOIN ades_tareas t ON t.id = te.tarea_id
              JOIN ades_materias m ON m.id = t.materia_id
              LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
             {filters}
             ORDER BY t.fecha_entrega DESC
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── GET /entregas/pendientes/grupo/{grupo_id} ─────────────────
@router.get("/pendientes/grupo/{grupo_id}")
async def pendientes_del_grupo(
    grupo_id: str,
    materia_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Vista del profesor: entregas entregadas pero sin calificar."""
    filters = "WHERE t.grupo_id = :gid AND te.estatus_entrega = 'ENTREGADA' AND te.is_active = TRUE"
    params: dict = {"gid": grupo_id}
    if materia_id:
        filters += " AND t.materia_id = :mid"
        params["mid"] = materia_id

    rows = await db.execute(
        text(f"""
            SELECT te.id, te.estudiante_id, te.estatus_entrega,
                   te.fecha_entrega, te.archivo_url, te.comentario_alumno,
                   t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite,
                   t.id AS actividad_id,
                   m.nombre_materia,
                   p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                   est.numero_matricula
              FROM ades_tareas_entregas te
              JOIN ades_tareas t ON t.id = te.tarea_id
              JOIN ades_materias m ON m.id = t.materia_id
              JOIN ades_estudiantes est ON est.id = te.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
             {filters}
             ORDER BY t.fecha_entrega, p.apellido_paterno
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── POST /entregas — alumno sube tarea ───────────────────────
@router.post("", status_code=201)
async def subir_entrega(
    tarea_id: str = Form(...),
    alumno_id: str = Form(...),
    comentario: Optional[str] = Form(None),
    archivo: Optional[UploadFile] = File(None),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    archivo_url: Optional[str] = None

    if archivo:
        _ensure_bucket()
        ext = archivo.filename.rsplit(".", 1)[-1] if "." in (archivo.filename or "") else "bin"
        key = f"{tarea_id}/{alumno_id}/{uuid.uuid4()}.{ext}"
        try:
            content = await archivo.read()
            client = _minio_client()
            client.put_object(BUCKET, key, io.BytesIO(content), len(content),
                              content_type=archivo.content_type or "application/octet-stream")
            archivo_url = f"minio://{BUCKET}/{key}"
        except S3Error as e:
            raise HTTPException(500, f"Error subiendo archivo: {e}")

    # Upsert del slot de entrega
    await db.execute(
        text("""
            INSERT INTO ades_tareas_entregas
                   (tarea_id, estudiante_id, fecha_entrega, comentario_alumno,
                    archivo_url, estatus_entrega)
            VALUES (:tid, :eid, now(), :com, :url, 'ENTREGADA')
            ON CONFLICT (tarea_id, estudiante_id) DO UPDATE
               SET fecha_entrega    = now(),
                   comentario_alumno = :com,
                   archivo_url      = COALESCE(:url, ades_tareas_entregas.archivo_url),
                   estatus_entrega  = 'ENTREGADA',
                   fecha_modificacion   = now(),
                   row_version      = ades_tareas_entregas.row_version + 1
        """),
        {"tid": tarea_id, "eid": alumno_id, "com": comentario, "url": archivo_url},
    )
    await db.commit()
    return {"message": "Entrega registrada", "archivo_url": archivo_url}


# ── PATCH /entregas/{id}/calificar ───────────────────────────
@router.patch("/{entrega_id}/calificar")
async def calificar_entrega(
    entrega_id: str,
    body: CalificarIn,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    sub = current_user.get("sub")
    user_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :s"), {"s": sub}
    )
    user_id = user_row.scalar()

    r = await db.execute(
        text("""
            UPDATE ades_tareas_entregas
               SET calificacion_obtenida = :cal,
                   comentario_profesor = :com,
                   calificado_por = :uid,
                   fecha_calificacion_docente = now(),
                   estatus_entrega = 'CALIFICADA',
                   fecha_modificacion = now(),
                   row_version = row_version + 1
             WHERE id = :id
        """),
        {"cal": body.calificacion, "com": body.comentario, "uid": user_id, "id": entrega_id},
    )
    if r.rowcount == 0:
        raise HTTPException(404, "Entrega no encontrada")
    await db.commit()
    return {"message": "Calificación registrada"}


# ── POST /entregas/{id}/excusa ────────────────────────────────
@router.post("/{entrega_id}/excusa")
async def registrar_excusa(
    entrega_id: str,
    motivo: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    await db.execute(
        text("""
            UPDATE ades_tareas_entregas
               SET estatus_entrega = 'EXCUSA',
                   comentario_profesor = :motivo,
                   fecha_modificacion = now()
             WHERE id = :id
        """),
        {"motivo": motivo, "id": entrega_id},
    )
    await db.commit()
    return {"message": "Excusa registrada"}

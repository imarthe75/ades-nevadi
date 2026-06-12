"""
/learning-paths — Rutas de refuerzo adaptativas para alumnos en riesgo.

  GET  /learning-paths                    — listado de rutas disponibles
  POST /learning-paths                    — crear nueva ruta (admin/coord)
  GET  /learning-paths/{path_id}          — detalle + recursos
  GET  /learning-paths/{path_id}/recursos — recursos del path
  POST /learning-paths/{path_id}/recursos — agregar recurso

  GET  /learning-paths/asignaciones              — asignaciones activas (con filtros)
  POST /learning-paths/{path_id}/asignar         — asignar a alumno
  POST /learning-paths/asignaciones/{asig_id}/progreso/{recurso_id} — marcar recurso completado
  GET  /learning-paths/asignaciones/{asig_id}    — detalle de asignación con progreso
  POST /learning-paths/asignar-automatico/{grupo_id} — asignación automática por alertas
"""
from __future__ import annotations
import uuid
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/learning-paths", tags=["learning-paths"])


# ── Schemas ───────────────────────────────────────────────────────────────────

class RecursoIn(BaseModel):
    orden: int = 1
    tipo: Literal["VIDEO", "PDF", "EJERCICIO", "ENLACE", "QUIZ"]
    titulo: str = Field(min_length=1, max_length=200)
    descripcion: str | None = None
    url_recurso: str | None = None
    duracion_min: int | None = None
    obligatorio: bool = True


class RecursoOut(BaseModel):
    id: uuid.UUID
    orden: int
    tipo: str
    titulo: str
    descripcion: str | None
    url_recurso: str | None
    duracion_min: int | None
    obligatorio: bool
    is_active: bool

    class Config:
        from_attributes = True


class PathIn(BaseModel):
    nombre: str = Field(min_length=1, max_length=200)
    descripcion: str | None = None
    nivel_educativo_id: uuid.UUID | None = None
    materia_id: uuid.UUID | None = None
    criterio_activacion: Literal["MANUAL", "REPROBACION", "AUSENTISMO", "RIESGO_ALTO"] = "MANUAL"
    umbral_activacion: float | None = None


class PathOut(BaseModel):
    id: uuid.UUID
    nombre: str
    descripcion: str | None
    criterio_activacion: str
    umbral_activacion: float | None
    is_active: bool
    total_recursos: int = 0

    class Config:
        from_attributes = True


class PathDetalle(PathOut):
    recursos: list[RecursoOut] = []


class AsignacionIn(BaseModel):
    estudiante_id: uuid.UUID
    motivo: Literal["MANUAL", "AUTO_REPROBACION", "AUTO_AUSENTISMO", "AUTO_RIESGO"] = "MANUAL"


class ProgresoIn(BaseModel):
    tiempo_min: int | None = None
    calificacion: float | None = None


class AsignacionOut(BaseModel):
    id: uuid.UUID
    path_id: uuid.UUID
    path_nombre: str
    estudiante_id: uuid.UUID
    motivo: str
    estatus: str
    pct_completado: float
    fecha_creacion: datetime

    class Config:
        from_attributes = True


class AsignacionDetalle(AsignacionOut):
    recursos_completados: int = 0
    total_recursos: int = 0
    progreso: list[dict] = []


# ── Helper DB ─────────────────────────────────────────────────────────────────

async def _get_path_or_404(path_id: uuid.UUID, db: AsyncSession):
    from sqlalchemy import text
    row = (await db.execute(
        text("SELECT id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active FROM ades_learning_paths WHERE id = :id"),
        {"id": path_id},
    )).fetchone()
    if not row:
        raise HTTPException(404, "Learning path no encontrado")
    return row


# ── Learning Paths ────────────────────────────────────────────────────────────

@router.get("", response_model=list[PathOut])
async def listar_paths(
    activos: bool = True,
    criterio: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    sql = """
        SELECT lp.id, lp.nombre, lp.descripcion, lp.criterio_activacion,
               lp.umbral_activacion, lp.is_active,
               COUNT(r.id) AS total_recursos
          FROM ades_learning_paths lp
          LEFT JOIN ades_lp_recursos r ON r.path_id = lp.id AND r.is_active = TRUE
         WHERE (:activos IS NULL OR lp.is_active = :activos)
           AND (:criterio IS NULL OR lp.criterio_activacion = :criterio)
         GROUP BY lp.id
         ORDER BY lp.nombre
    """
    rows = (await db.execute(text(sql), {"activos": activos, "criterio": criterio})).fetchall()
    return [PathOut(
        id=r.id, nombre=r.nombre, descripcion=r.descripcion,
        criterio_activacion=r.criterio_activacion, umbral_activacion=r.umbral_activacion,
        is_active=r.is_active, total_recursos=r.total_recursos,
    ) for r in rows]


@router.post("", response_model=PathOut, status_code=201)
async def crear_path(
    data: PathIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    row = (await db.execute(
        text("""
            INSERT INTO ades_learning_paths
                (nombre, descripcion, nivel_educativo_id, materia_id,
                 criterio_activacion, umbral_activacion, is_active)
            VALUES (:nombre, :desc, :nivel_id, :mat_id, :criterio, :umbral, TRUE)
            RETURNING id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active
        """),
        {
            "nombre": data.nombre, "desc": data.descripcion,
            "nivel_id": data.nivel_educativo_id, "mat_id": data.materia_id,
            "criterio": data.criterio_activacion, "umbral": data.umbral_activacion,
        },
    )).fetchone()
    await db.commit()
    return PathOut(id=row.id, nombre=row.nombre, descripcion=row.descripcion,
                   criterio_activacion=row.criterio_activacion, umbral_activacion=row.umbral_activacion,
                   is_active=row.is_active, total_recursos=0)


@router.get("/{path_id}", response_model=PathDetalle)
async def detalle_path(
    path_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    path = await _get_path_or_404(path_id, db)
    recursos = (await db.execute(
        text("""
            SELECT id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active
              FROM ades_lp_recursos WHERE path_id = :pid AND is_active = TRUE ORDER BY orden
        """),
        {"pid": path_id},
    )).fetchall()
    return PathDetalle(
        id=path.id, nombre=path.nombre, descripcion=path.descripcion,
        criterio_activacion=path.criterio_activacion, umbral_activacion=path.umbral_activacion,
        is_active=path.is_active,
        total_recursos=len(recursos),
        recursos=[RecursoOut(id=r.id, orden=r.orden, tipo=r.tipo, titulo=r.titulo,
                             descripcion=r.descripcion, url_recurso=r.url_recurso,
                             duracion_min=r.duracion_min, obligatorio=r.obligatorio,
                             is_active=r.is_active) for r in recursos],
    )


@router.post("/{path_id}/recursos", response_model=RecursoOut, status_code=201)
async def agregar_recurso(
    path_id: uuid.UUID,
    data: RecursoIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    await _get_path_or_404(path_id, db)
    row = (await db.execute(
        text("""
            INSERT INTO ades_lp_recursos
                (path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio)
            VALUES (:pid, :orden, :tipo, :titulo, :desc, :url, :dur, :oblig)
            RETURNING id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active
        """),
        {
            "pid": path_id, "orden": data.orden, "tipo": data.tipo,
            "titulo": data.titulo, "desc": data.descripcion,
            "url": data.url_recurso, "dur": data.duracion_min, "oblig": data.obligatorio,
        },
    )).fetchone()
    await db.commit()
    return RecursoOut(id=row.id, orden=row.orden, tipo=row.tipo, titulo=row.titulo,
                      descripcion=row.descripcion, url_recurso=row.url_recurso,
                      duracion_min=row.duracion_min, obligatorio=row.obligatorio,
                      is_active=row.is_active)


# ── Asignaciones ──────────────────────────────────────────────────────────────

@router.get("/asignaciones", response_model=list[AsignacionOut])
async def listar_asignaciones(
    estudiante_id: uuid.UUID | None = None,
    estatus: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    sql = """
        SELECT a.id, a.path_id, lp.nombre AS path_nombre,
               a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion
          FROM ades_lp_asignaciones a
          JOIN ades_learning_paths lp ON lp.id = a.path_id
         WHERE (:est_id IS NULL OR a.estudiante_id = :est_id)
           AND (:estatus IS NULL OR a.estatus = :estatus)
         ORDER BY a.fecha_creacion DESC
    """
    rows = (await db.execute(text(sql), {"est_id": estudiante_id, "estatus": estatus})).fetchall()
    return [AsignacionOut(
        id=r.id, path_id=r.path_id, path_nombre=r.path_nombre,
        estudiante_id=r.estudiante_id, motivo=r.motivo,
        estatus=r.estatus, pct_completado=float(r.pct_completado),
        fecha_creacion=r.fecha_creacion,
    ) for r in rows]


@router.post("/{path_id}/asignar", response_model=AsignacionOut, status_code=201)
async def asignar_path(
    path_id: uuid.UUID,
    data: AsignacionIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    await _get_path_or_404(path_id, db)
    asignado_por = _user.get("sub")
    row = (await db.execute(
        text("""
            INSERT INTO ades_lp_asignaciones
                (path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
            VALUES (:pid, :est, :asig::uuid, :motivo, 'PENDIENTE', 0, NOW())
            ON CONFLICT (path_id, estudiante_id) DO UPDATE
                SET estatus = EXCLUDED.estatus, fecha_modificacion = NOW()
            RETURNING id, path_id, estudiante_id, motivo, estatus, pct_completado, fecha_creacion
        """),
        {"pid": path_id, "est": data.estudiante_id, "asig": asignado_por, "motivo": data.motivo},
    )).fetchone()
    await db.commit()

    path = await _get_path_or_404(path_id, db)
    return AsignacionOut(
        id=row.id, path_id=row.path_id, path_nombre=path.nombre,
        estudiante_id=row.estudiante_id, motivo=row.motivo,
        estatus=row.estatus, pct_completado=float(row.pct_completado),
        fecha_creacion=row.fecha_creacion,
    )


@router.get("/asignaciones/{asig_id}", response_model=AsignacionDetalle)
async def detalle_asignacion(
    asig_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    row = (await db.execute(
        text("""
            SELECT a.id, a.path_id, lp.nombre AS path_nombre,
                   a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion
              FROM ades_lp_asignaciones a
              JOIN ades_learning_paths lp ON lp.id = a.path_id
             WHERE a.id = :id
        """),
        {"id": asig_id},
    )).fetchone()
    if not row:
        raise HTTPException(404, "Asignación no encontrada")

    progreso_rows = (await db.execute(
        text("""
            SELECT p.recurso_id, r.titulo, r.tipo, r.orden,
                   p.completado, p.tiempo_min, p.calificacion, p.fccompletado
              FROM ades_lp_progreso p
              JOIN ades_lp_recursos r ON r.id = p.recurso_id
             WHERE p.asignacion_id = :aid
             ORDER BY r.orden
        """),
        {"aid": asig_id},
    )).fetchall()

    total_rows = (await db.execute(
        text("SELECT COUNT(*) FROM ades_lp_recursos WHERE path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = :aid) AND is_active = TRUE"),
        {"aid": asig_id},
    )).scalar()

    completados = sum(1 for p in progreso_rows if p.completado)
    return AsignacionDetalle(
        id=row.id, path_id=row.path_id, path_nombre=row.path_nombre,
        estudiante_id=row.estudiante_id, motivo=row.motivo,
        estatus=row.estatus, pct_completado=float(row.pct_completado),
        fecha_creacion=row.fecha_creacion,
        recursos_completados=completados,
        total_recursos=total_rows or 0,
        progreso=[{
            "recurso_id": str(p.recurso_id), "titulo": p.titulo, "tipo": p.tipo,
            "orden": p.orden, "completado": p.completado,
            "tiempo_min": p.tiempo_min, "calificacion": p.calificacion,
            "fccompletado": p.fccompletado.isoformat() if p.fccompletado else None,
        } for p in progreso_rows],
    )


@router.post("/asignaciones/{asig_id}/progreso/{recurso_id}", status_code=200)
async def registrar_progreso(
    asig_id: uuid.UUID,
    recurso_id: uuid.UUID,
    data: ProgresoIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Marca un recurso como completado y recalcula el % de progreso de la asignación."""
    from sqlalchemy import text
    await db.execute(
        text("""
            INSERT INTO ades_lp_progreso
                (asignacion_id, recurso_id, completado, tiempo_min, calificacion, fccompletado)
            VALUES (:asig, :rec, TRUE, :t, :cal, NOW())
            ON CONFLICT (asignacion_id, recurso_id) DO UPDATE
                SET completado = TRUE, tiempo_min = COALESCE(EXCLUDED.tiempo_min, ades_lp_progreso.tiempo_min),
                    calificacion = COALESCE(EXCLUDED.calificacion, ades_lp_progreso.calificacion),
                    fccompletado = NOW()
        """),
        {"asig": asig_id, "rec": recurso_id, "t": data.tiempo_min, "cal": data.calificacion},
    )

    # Recalcular pct_completado en la asignación
    await db.execute(
        text("""
            UPDATE ades_lp_asignaciones
               SET pct_completado = (
                       SELECT ROUND(
                           100.0 * COUNT(CASE WHEN pr.completado THEN 1 END)
                           / NULLIF((SELECT COUNT(*) FROM ades_lp_recursos r2
                                      WHERE r2.path_id = a.path_id AND r2.is_active = TRUE), 0)
                       , 1)
                         FROM ades_lp_progreso pr
                         JOIN ades_lp_asignaciones a ON a.id = pr.asignacion_id
                        WHERE pr.asignacion_id = :asig
                   ),
                   estatus = CASE
                       WHEN (
                           SELECT COUNT(CASE WHEN pr2.completado THEN 1 END)
                             FROM ades_lp_progreso pr2
                            WHERE pr2.asignacion_id = :asig
                       ) >= (
                           SELECT COUNT(*) FROM ades_lp_recursos r3
                            WHERE r3.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = :asig)
                              AND r3.is_active = TRUE AND r3.obligatorio = TRUE
                       ) THEN 'COMPLETADO'
                       ELSE 'EN_PROGRESO'
                   END,
                   fccompletado = CASE
                       WHEN estatus = 'COMPLETADO' THEN NOW() ELSE fccompletado END,
                   fecha_modificacion = NOW()
             WHERE id = :asig
        """),
        {"asig": asig_id},
    )
    await db.commit()
    return {"mensaje": "Progreso registrado"}


@router.post("/asignar-automatico/{grupo_id}", status_code=200)
async def asignar_automatico(
    grupo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Asigna automáticamente learning paths a alumnos del grupo según alertas activas:
      - REPROBACION / AUSENTISMO_CRITICO → path con criterio correspondiente
    """
    from sqlalchemy import text

    alertas = (await db.execute(
        text("""
            SELECT a.estudiante_id, a.tipo_alerta
              FROM ades_alertas_academicas a
             WHERE a.grupo_id = :gid AND a.atendida = FALSE
        """),
        {"gid": grupo_id},
    )).fetchall()

    if not alertas:
        return {"asignadas": 0, "mensaje": "Sin alertas activas en el grupo"}

    # Obtener paths automáticos disponibles
    paths = (await db.execute(
        text("""
            SELECT id, criterio_activacion FROM ades_learning_paths
             WHERE criterio_activacion IN ('REPROBACION', 'AUSENTISMO', 'RIESGO_ALTO')
               AND is_active = TRUE
        """)
    )).fetchall()

    tipo_a_criterio = {
        "RIESGO_REPROBACION": "REPROBACION",
        "AUSENTISMO_CRITICO": "AUSENTISMO",
    }
    path_por_criterio = {p.criterio_activacion: p.id for p in paths}

    asignadas = 0
    asig_by = _user.get("sub")
    for alerta in alertas:
        criterio = tipo_a_criterio.get(alerta.tipo_alerta)
        if not criterio:
            continue
        path_id = path_por_criterio.get(criterio)
        if not path_id:
            continue
        motivo = f"AUTO_{alerta.tipo_alerta}"
        await db.execute(
            text("""
                INSERT INTO ades_lp_asignaciones
                    (path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                VALUES (:pid, :est, :asig::uuid, :motivo, 'PENDIENTE', 0, NOW())
                ON CONFLICT (path_id, estudiante_id) DO NOTHING
            """),
            {"pid": path_id, "est": alerta.estudiante_id, "asig": asig_by, "motivo": motivo},
        )
        asignadas += 1

    await db.commit()
    return {"asignadas": asignadas, "grupo_id": str(grupo_id)}


# ── Recomendación IA ──────────────────────────────────────────────────────────

@router.post("/asignaciones/{asig_id}/recomendar-ia", status_code=200)
async def recomendar_ia(
    asig_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Genera análisis personalizado con Claude para una asignación de learning path.
    Analiza el historial académico del alumno y devuelve recomendaciones específicas.
    Guarda el resultado en ades_lp_asignaciones.ia_recomendacion.
    """
    import os, json
    from sqlalchemy import text

    row = (await db.execute(text("""
        SELECT
            asig.id, asig.estudiante_id, asig.path_id, asig.motivo,
            lp.nombre AS path_nombre, lp.criterio_activacion,
            CONCAT(p.nombre, ' ', p.apellido_paterno,
                   COALESCE(' ' || p.apellido_materno, '')) AS nombre_alumno,
            p.curp,
            (SELECT pl.nombre_plantel FROM ades_planteles pl
              WHERE pl.id = e.plantel_id) AS plantel
        FROM ades_lp_asignaciones asig
        JOIN ades_learning_paths lp ON lp.id = asig.path_id
        JOIN ades_estudiantes e ON e.id = asig.estudiante_id
        JOIN ades_personas p ON p.id = e.persona_id
        WHERE asig.id = CAST(:id AS uuid)
    """), {"id": str(asig_id)})).mappings().first()

    if not row:
        raise HTTPException(status_code=404, detail="Asignación no encontrada")

    est_id = str(row["estudiante_id"])

    # Historial de calificaciones
    cals = (await db.execute(text("""
        SELECT m.nombre_materia, cp.calificacion_final, cp.es_acreditado,
               pe.nombre_periodo
          FROM ades_calificaciones_periodo cp
          JOIN ades_materias m ON m.id = cp.materia_id
          JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
         WHERE cp.estudiante_id = CAST(:est AS uuid)
           AND cp.is_active = TRUE
         ORDER BY pe.fecha_inicio DESC
         LIMIT 20
    """), {"est": est_id})).mappings().all()

    # Estadísticas de asistencia (últimos 60 días)
    asis = (await db.execute(text("""
        SELECT
            COUNT(*) FILTER (WHERE a.estatus_asistencia = 'PRESENTE') AS presentes,
            COUNT(*) FILTER (WHERE a.estatus_asistencia = 'FALTA') AS faltas,
            COUNT(*) AS total
          FROM ades_asistencias a
          JOIN ades_clases cl ON cl.id = a.clase_id
         WHERE a.estudiante_id = CAST(:est AS uuid)
           AND cl.fecha_clase >= CURRENT_DATE - INTERVAL '60 days'
    """), {"est": est_id})).mappings().first()

    # Recursos del learning path
    recursos = (await db.execute(text("""
        SELECT titulo, tipo, duracion_min, obligatorio
          FROM ades_lp_recursos
         WHERE path_id = CAST(:pid AS uuid) AND is_active = TRUE
         ORDER BY orden
    """), {"pid": str(row["path_id"])})).mappings().all()

    # Construir prompt para Claude
    cal_texto = "\n".join(
        f"  - {c['nombre_materia']}: {c['calificacion_final']} "
        f"({'Acreditada' if c['es_acreditado'] else 'No acreditada'}) — {c['nombre_periodo']}"
        for c in cals
    ) or "  Sin calificaciones registradas."

    pct_asistencia = (
        round(100 * int(asis["presentes"]) / int(asis["total"]), 1)
        if asis and asis["total"] else "N/D"
    )

    recursos_texto = "\n".join(
        f"  {i+1}. [{r['tipo']}] {r['titulo']} ({r['duracion_min'] or 0} min)"
        for i, r in enumerate(recursos)
    )

    prompt = f"""Eres un orientador educativo experto del sistema escolar mexicano SEP/UAEMEX.
Analiza el perfil académico de este alumno y genera recomendaciones personalizadas.

ALUMNO: {row['nombre_alumno']}
PLANTEL: {row['plantel']}
RUTA DE REFUERZO ASIGNADA: {row['path_nombre']}
MOTIVO DE ASIGNACIÓN: {row['motivo']}

HISTORIAL DE CALIFICACIONES (últimos periodos):
{cal_texto}

ASISTENCIA (últimos 60 días):
  - Presencias: {asis['presentes'] if asis else 0} / {asis['total'] if asis else 0}
  - Porcentaje: {pct_asistencia}%

RECURSOS DISPONIBLES EN LA RUTA:
{recursos_texto}

Proporciona un análisis estructurado en JSON con exactamente estos campos:
{{
  "resumen": "2-3 oraciones sobre la situación académica del alumno",
  "fortalezas": ["máximo 3 fortalezas identificadas"],
  "areas_mejora": ["máximo 3 áreas prioritarias de mejora"],
  "estrategias": ["3-4 estrategias concretas y realizables para este alumno específico"],
  "recursos_priorizados": ["títulos de los 3 recursos más importantes para comenzar"],
  "mensaje_motivacional": "una frase motivacional personalizada para el alumno (15-25 palabras)"
}}

Responde SOLO con el JSON, sin texto adicional."""

    from app.core.config import settings
    api_key = settings.OPENAI_API_KEY
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY no configurada. Configure la variable de entorno o en Vault."
        )

    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key, base_url=settings.OPENAI_BASE_URL)
        mensaje = client.chat.completions.create(
            model=settings.OPENAI_MODEL,
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )
        contenido = mensaje.choices[0].message.content.strip()
        # Extraer JSON aunque venga con bloques markdown
        if "```" in contenido:
            contenido = contenido.split("```")[1]
            if contenido.startswith("json"):
                contenido = contenido[4:]
        ia_data = json.loads(contenido)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=502, detail=f"Respuesta IA no es JSON válido: {exc}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Error al llamar NVIDIA NIM API: {exc}")

    # Guardar análisis en BD
    await db.execute(text("""
        UPDATE ades_lp_asignaciones
           SET ia_recomendacion = CAST(:ia AS jsonb),
               fecha_modificacion = NOW(),
               row_version = row_version + 1
         WHERE id = CAST(:id AS uuid)
    """), {"ia": json.dumps(ia_data, ensure_ascii=False), "id": str(asig_id)})
    await db.commit()

    return {
        "asignacion_id": str(asig_id),
        "alumno": row["nombre_alumno"],
        "path": row["path_nombre"],
        "ia_recomendacion": ia_data,
    }

"""
Endpoints FastAPI — Generación de boletas PDF.

  GET  /boletas/{estudiante_id}               → Boleta NEM (primaria/secundaria SEP)
  POST /boletas/grupo/{grupo_id}/batch         → Encola tarea Celery (ZIP de grupo)
  GET  /boletas/tarea/{task_id}               → Estado de tarea Celery
  GET  /boletas/uaemex/{estudiante_id}        → Constancia de calificaciones UAEMEX (prepa)

Solo lectura — no muta datos académicos.  Requiere JWT válido (ades_token).
"""
from __future__ import annotations

import uuid
import asyncio
from datetime import date
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import Response as FastResponse
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app.core.security import get_ades_user
from app.core.config import settings

router = APIRouter(prefix="/boletas", tags=["boletas"])
TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "boletas"


def _sync_db_session() -> Session:
    """Crea una sesión de base de datos síncrona mediante el motor SQLAlchemy.

    Se utiliza para tareas que se ejecutan en subprocesos independientes o tareas de Celery.

    Returns:
        Session: Sesión síncrona activa de SQLAlchemy.
    """
    engine = create_engine(settings.DATABASE_URL_SYNC, pool_pre_ping=True)
    return Session(engine)


# ── NEM individual ─────────────────────────────────────────────────────────

@router.get("/{estudiante_id}")
async def boleta_nem(
    estudiante_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    _user=Depends(get_ades_user),
):
    """Genera la boleta oficial de evaluación NEM para primaria o secundaria.

    Busca los datos de calificaciones del ciclo especificado, renderiza la plantilla
    HTML correspondiente y exporta el contenido en formato PDF.

    Args:
        estudiante_id: Identificador único del estudiante.
        ciclo_id: Identificador único del ciclo escolar (opcional).
        _user: Usuario actual autenticado.

    Returns:
        FastResponse: Respuesta HTTP que contiene el archivo PDF.

    Raises:
        HTTPException: Si el alumno no es encontrado o carece de inscripciones activas.
    """
    from app.worker.tasks.boletas import _generar_pdf_alumno

    def _gen():
        with _sync_db_session() as session:
            return _generar_pdf_alumno(session, estudiante_id, ciclo_id)

    pdf = await asyncio.to_thread(_gen)
    if not pdf:
        raise HTTPException(status_code=404, detail="Alumno no encontrado o sin inscripción en el ciclo indicado")
    return FastResponse(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=boleta_{estudiante_id}.pdf"},
    )


# ── UAEMEX constancia ───────────────────────────────────────────────────────

@router.get("/uaemex/{estudiante_id}")
async def boleta_uaemex(
    estudiante_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    _user=Depends(get_ades_user),
):
    """Genera la constancia oficial de calificaciones con el formato UAEMEX para nivel preparatoria.

    Args:
        estudiante_id: Identificador único del estudiante.
        ciclo_id: Identificador único del ciclo escolar (opcional).
        _user: Usuario actual autenticado.

    Returns:
        FastResponse: Respuesta HTTP que contiene el archivo PDF de la constancia.

    Raises:
        HTTPException: Si el alumno no cuenta con un historial académico (kardex) en el ciclo.
    """
    def _gen():
        return _generar_pdf_uaemex(estudiante_id, ciclo_id)

    pdf = await asyncio.to_thread(_gen)
    if not pdf:
        raise HTTPException(status_code=404, detail="Alumno no encontrado o sin kardex UAEMEX en el ciclo indicado")
    return FastResponse(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=constancia_uaemex_{estudiante_id}.pdf"},
    )


def _generar_pdf_uaemex(estudiante_id: uuid.UUID, ciclo_id: uuid.UUID | None) -> bytes | None:
    """Genera la constancia de calificaciones UAEMEX en formato PDF leyendo la base de datos síncronamente.

    Args:
        estudiante_id: Identificador del estudiante.
        ciclo_id: Identificador del ciclo escolar (opcional).

    Returns:
        bytes: Datos binarios del archivo PDF generado, o None si no se encuentra información.
    """
    MIN_APROBATORIA = 6.0

    PLANTEL_INFO = {
        "Metepec":           {"cct": "15EMH0001A", "dir": "Prol. Heriberto Enríquez 1001", "tel": "722-297-1441"},
        "Tenancingo":        {"cct": "15EMH0002B", "dir": "Carretera Tenancingo-Tenería S/N", "tel": "714-142-4323"},
        "Ixtapan de la Sal": {"cct": "15EMH0003C", "dir": "Independencia Pte. 5", "tel": "721-143-3015"},
    }

    with _sync_db_session() as session:
        # ── Cabecera del alumno ──────────────────────────────────────────────
        header = session.execute(text("""
            SELECT
              trim(p.nombre||' '||p.apellido_paterno||' '||COALESCE(p.apellido_materno,'')) AS nombre_completo,
              p.curp, e.matricula,
              gr.nombre_grado   AS semestre,
              g.nombre_grupo    AS grupo,
              pl.nombre_plantel AS plantel,
              c.nombre_ciclo    AS ciclo,
              c.id              AS ciclo_id
            FROM ades_estudiantes e
            JOIN ades_personas p          ON p.id  = e.persona_id
            JOIN ades_inscripciones i     ON i.estudiante_id = e.id AND i.is_active = true
            JOIN ades_ciclos_escolares c  ON c.id  = i.ciclo_escolar_id
            JOIN ades_grupos g            ON g.id  = i.grupo_id
            JOIN ades_grados gr           ON gr.id = g.grado_id
            JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
            LEFT JOIN ades_planteles pl   ON pl.id = gr.plantel_id
            WHERE e.id = CAST(:est AS uuid)
              AND n.autoridad_educativa = 'UAEMEX'
              AND (:ciclo IS NULL AND c.es_vigente = true OR c.id = CAST(:ciclo AS uuid))
            LIMIT 1
        """), {
            "est": str(estudiante_id),
            "ciclo": str(ciclo_id) if ciclo_id else None,
        }).mappings().first()

        if not header:
            return None

        real_ciclo_id = header["ciclo_id"]

        # ── Materias con calificaciones ──────────────────────────────────────
        materias_rows = session.execute(text("""
            SELECT
              m.nombre_materia AS materia,
              m.clave_materia  AS clave,
              MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'FINAL')          AS ordinario,
              MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'EXTRAORDINARIO') AS extraordinario,
              COALESCE(SUM(cp.inasistencias) FILTER (WHERE pe.tipo_periodo <> 'EXTRAORDINARIO'), 0) AS inasistencias
            FROM ades_calificaciones_periodo cp
            JOIN ades_materias m             ON m.id  = cp.materia_id
            JOIN ades_periodos_evaluacion pe  ON pe.id = cp.periodo_evaluacion_id
            JOIN ades_inscripciones i         ON i.estudiante_id = cp.estudiante_id
                                              AND i.grupo_id  = cp.grupo_id AND i.is_active = true
            JOIN ades_ciclos_escolares c      ON c.id  = i.ciclo_escolar_id
            JOIN ades_grupos g               ON g.id  = i.grupo_id
            JOIN ades_grados gr              ON gr.id = g.grado_id
            JOIN ades_niveles_educativos n   ON n.id  = gr.nivel_educativo_id
            WHERE cp.estudiante_id = CAST(:est AS uuid)
              AND n.autoridad_educativa = 'UAEMEX'
              AND c.id = CAST(:ciclo AS uuid)
            GROUP BY m.nombre_materia, m.clave_materia
            ORDER BY m.nombre_materia
        """), {"est": str(estudiante_id), "ciclo": str(real_ciclo_id)}).mappings().all()

        materias = []
        promedios = []
        for row in materias_rows:
            ord_val  = float(row["ordinario"])      if row["ordinario"]      is not None else None
            ext_val  = float(row["extraordinario"]) if row["extraordinario"] is not None else None
            if ord_val is not None and ord_val >= MIN_APROBATORIA:
                def_val = ord_val
            elif ext_val is not None:
                def_val = ext_val
            else:
                def_val = ord_val
            acreditada = def_val is not None and def_val >= MIN_APROBATORIA
            if def_val is not None:
                promedios.append(def_val)
            materias.append({
                "materia":        row["materia"],
                "clave":          row["clave"] or "—",
                "ordinario":      f"{ord_val:.1f}" if ord_val is not None else "—",
                "extraordinario": f"{ext_val:.1f}" if ext_val is not None else "—",
                "definitiva":     f"{def_val:.1f}" if def_val is not None else "—",
                "acreditada":     acreditada,
                "inasistencias":  int(row["inasistencias"] or 0),
            })

        promedio_general = round(sum(promedios) / len(promedios), 1) if promedios else None
        total = len(materias)
        acreditadas = sum(1 for m in materias if m["acreditada"])
        acredito_grado = total > 0 and all(m["acreditada"] for m in materias if m["definitiva"] != "—")

        plantel_nombre = header["plantel"] or "—"
        info = PLANTEL_INFO.get(plantel_nombre, {"cct": "—", "dir": "", "tel": ""})

        ctx = {
            "plantel_nombre":   plantel_nombre,
            "cct":              info["cct"],
            "plantel_dir":      info["dir"],
            "plantel_tel":      info["tel"],
            "ciclo_nombre":     header["ciclo"],
            "fecha_generacion": date.today().strftime("%d/%m/%Y"),
            "nombre_completo":  header["nombre_completo"],
            "curp":             header["curp"] or "—",
            "matricula":        header["matricula"] or "—",
            "semestre":         header["semestre"],
            "grupo":            f"Grupo {header['grupo']}",
            "materias":         materias,
            "promedio_general": f"{promedio_general:.1f}" if promedio_general else "—",
            "materias_acreditadas": acreditadas,
            "total_materias":   total,
            "acredito_grado":   acredito_grado,
            "escala":           "Escala 0 – 10 | Mínima aprobatoria: 6.0 (RGEMS UAEMEX)",
        }

        from jinja2 import Environment, FileSystemLoader
        jinja_env = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
        html_str = jinja_env.get_template("boleta_uaemex.html").render(**ctx)

        from weasyprint import HTML
        return HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()


# ── Celery batch ────────────────────────────────────────────────────────────

@router.post("/grupo/{grupo_id}/batch")
async def encolar_boletas_grupo(
    grupo_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    _user=Depends(get_ades_user),
):
    """Encola una tarea de Celery para generar un archivo comprimido ZIP con las boletas de todo un grupo.

    Args:
        grupo_id: Identificador del grupo escolar.
        ciclo_id: Identificador del ciclo escolar (opcional).
        _user: Usuario actual autenticado que solicita el reporte.

    Returns:
        dict: Contiene el identificador de la tarea Celery (task_id) y el estado inicial (encolado).
    """
    from app.worker.tasks.boletas import generar_boletas_grupo
    task = generar_boletas_grupo.delay(
        str(grupo_id),
        str(ciclo_id) if ciclo_id else None,
        solicitado_por=str(_user.sub),
    )
    return {"task_id": task.id, "estado": "encolado"}


@router.get("/tarea/{task_id}")
async def estado_tarea(
    task_id: str,
    _user=Depends(get_ades_user),
):
    """Obtiene el estado actual y detalles de ejecución de una tarea de Celery específica.

    Se utiliza para realizar sondeo (polling) desde el cliente sobre el avance
    de generación de boletas por lote.

    Args:
        task_id: Identificador de la tarea en Celery.
        _user: Usuario actual autenticado.

    Returns:
        dict: Datos del estado, identificador de la tarea y detalles/información del resultado.
    """
    from celery.result import AsyncResult
    result = AsyncResult(task_id)
    return {
        "task_id": task_id,
        "estado":  result.state,
        "info":    result.info if isinstance(result.info, dict) else str(result.info),
    }

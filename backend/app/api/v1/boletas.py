"""
/boletas — Generación de boletas de calificaciones en PDF.

  GET  /boletas/{estudiante_id}           — boleta individual (StreamingResponse PDF)
  POST /boletas/grupo/{grupo_id}/batch    — encola tarea Celery → devuelve task_id
  GET  /boletas/tarea/{task_id}           — estado de la tarea batch (polling)

Usa WeasyPrint + Jinja2 para renderizar el template HTML → PDF.
"""
from __future__ import annotations
import uuid
from datetime import date, datetime
from io import BytesIO
from pathlib import Path
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from jinja2 import Environment, FileSystemLoader

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.academica import CicloEscolar, Grupo, Grado, PlantelNivel, Plantel, NivelEducativo
from app.models.operacion import PeriodoEvaluacion, CalificacionPeriodo
from app.models.materias import MateriaPlan, Materia

router = APIRouter(prefix="/boletas", tags=["boletas"])


class BatchBoletasOut(BaseModel):
    task_id: str
    estado: str
    mensaje: str


class TareaEstadoOut(BaseModel):
    task_id: str
    estado: str         # PENDING | PROGRESS | SUCCESS | FAILURE
    progreso: dict | None = None
    resultado: dict | None = None
    error: str | None = None

TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "boletas"

_jinja_env: Environment | None = None


def _get_jinja() -> Environment:
    global _jinja_env
    if _jinja_env is None:
        _jinja_env = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    return _jinja_env


# ── Datos de contacto por plantel ─────────────────────────────────────────────
PLANTEL_INFO = {
    "Metepec":     {"direccion": "Prol. Heriberto Enríquez 1001", "tel": "722-297-1441"},
    "Tenancingo":  {"direccion": "Carretera Tenancingo-Tenería S/N", "tel": "714-142-4323"},
    "Ixtapan de la Sal": {"direccion": "Independencia Pte. 5", "tel": "721-143-3015"},
}


@router.get("/{estudiante_id}")
async def generar_boleta(
    estudiante_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Genera la boleta de calificaciones del alumno como PDF."""

    # 1. Alumno
    estudiante = (await db.execute(
        select(Estudiante).options(selectinload(Estudiante.persona))
        .where(Estudiante.id == estudiante_id)
    )).scalar_one_or_none()
    if not estudiante:
        raise HTTPException(status_code=404, detail="Alumno no encontrado")

    # 2. Inscripción activa
    inscr_q = (
        select(Inscripcion)
        .join(CicloEscolar, CicloEscolar.id == Inscripcion.ciclo_escolar_id)
        .where(Inscripcion.estudiante_id == estudiante_id, Inscripcion.is_active == True)
    )
    if ciclo_id:
        inscr_q = inscr_q.where(Inscripcion.ciclo_escolar_id == ciclo_id)
    else:
        inscr_q = inscr_q.where(CicloEscolar.es_vigente == True)
    inscripcion = (await db.execute(inscr_q)).scalar_one_or_none()
    if not inscripcion:
        raise HTTPException(status_code=404, detail="Sin inscripción activa")

    ciclo = await db.get(CicloEscolar, inscripcion.ciclo_escolar_id)
    grupo = await db.get(Grupo, inscripcion.grupo_id)
    grado = await db.get(Grado, grupo.grado_id) if grupo else None
    plantel = await db.get(Plantel, estudiante.plantel_id)

    # Nivel educativo del grado
    nivel_nombre = "—"
    if grado:
        pn = (await db.execute(
            select(PlantelNivel).where(PlantelNivel.plantel_id == plantel.id).limit(1)
        )).scalar_one_or_none()
        if pn:
            nivel = await db.get(NivelEducativo, pn.nivel_educativo_id)
            nivel_nombre = nivel.nombre_nivel if nivel else "—"

    # 3. Periodos del ciclo
    periodos = (await db.execute(
        select(PeriodoEvaluacion)
        .where(
            PeriodoEvaluacion.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            PeriodoEvaluacion.is_active == True,
        )
        .order_by(PeriodoEvaluacion.numero_periodo)
    )).scalars().all()

    periodo_nombres = [p.nombre_periodo for p in periodos]

    # 4. Plan de estudios del grado
    planes = (await db.execute(
        select(MateriaPlan).options(selectinload(MateriaPlan.materia))
        .where(
            MateriaPlan.grado_id == grado.id if grado else False,
            MateriaPlan.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            MateriaPlan.is_active == True,
        )
        .order_by(MateriaPlan.orden)
    )).scalars().all()

    # 5. Calificaciones del alumno
    cals = (await db.execute(
        select(CalificacionPeriodo)
        .where(
            CalificacionPeriodo.estudiante_id == estudiante_id,
            CalificacionPeriodo.grupo_id == inscripcion.grupo_id,
        )
    )).scalars().all()

    periodo_id_map = {p.id: p.nombre_periodo for p in periodos}
    cal_map = {
        (str(c.materia_id), str(c.periodo_evaluacion_id)): float(c.calificacion_final)
        for c in cals
    }

    # 6. Construir datos de materias para el template
    materias_data = []
    promedios_mat = []
    for plan in planes:
        mat_cals: dict[str, float | None] = {}
        suma = 0.0
        conteo = 0
        for p in periodos:
            val = cal_map.get((str(plan.materia_id), str(p.id)))
            mat_cals[p.nombre_periodo] = val
            if val is not None:
                suma += val
                conteo += 1
        promedio = round(suma / conteo, 2) if conteo else None
        if promedio is not None:
            promedios_mat.append(promedio)
        materias_data.append({
            "materia_nombre": plan.materia.nombre_materia if plan.materia else "—",
            "calificaciones": mat_cals,
            "promedio": promedio,
            "acreditado": (promedio >= 6.0) if promedio is not None else False,
        })

    promedio_general = round(sum(promedios_mat) / len(promedios_mat), 2) if promedios_mat else None

    # 7. Renderizar template HTML
    plantel_nombre = plantel.nombre_plantel if plantel else "—"
    info = PLANTEL_INFO.get(plantel_nombre, {"direccion": "", "tel": ""})
    persona = estudiante.persona
    nombre_completo = f"{persona.apellido_paterno} {persona.apellido_materno or ''} {persona.nombre}".strip() if persona else "—"
    grado_grupo_str = f"{grado.nombre_grado} — Grupo {grupo.nombre_grupo[-1]}" if grado and grupo else "—"

    ctx = {
        "plantel_nombre": plantel_nombre,
        "cct": getattr(plantel, "clave_ct", "") or "—",
        "ciclo_nombre": ciclo.nombre_ciclo if ciclo else "—",
        "fecha_generacion": date.today().strftime("%d/%m/%Y"),
        "nombre_completo": nombre_completo,
        "matricula": estudiante.matricula or "—",
        "grado_grupo": grado_grupo_str,
        "nivel_educativo": nivel_nombre,
        "periodos": periodo_nombres,
        "materias": materias_data,
        "promedio_general": promedio_general,
        "plantel_direccion": info["direccion"],
        "plantel_telefono": info["tel"],
    }

    template = _get_jinja().get_template("boleta.html")
    html_str = template.render(**ctx)

    # 8. WeasyPrint HTML → PDF
    try:
        from weasyprint import HTML
        pdf_bytes = HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()
    except ImportError:
        raise HTTPException(status_code=503, detail="WeasyPrint no disponible. Instala dependencias del sistema.")

    filename = f"boleta_{estudiante.matricula}_{ciclo.nombre_ciclo if ciclo else 'ciclo'}.pdf"
    return StreamingResponse(
        BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


# ── Batch async ───────────────────────────────────────────────────────────────

@router.post("/grupo/{grupo_id}/batch", response_model=BatchBoletasOut)
async def encolar_boletas_grupo(
    grupo_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    _user: dict = Depends(get_current_user),
):
    """Encola la generación batch de boletas para todo el grupo. Devuelve task_id para polling."""
    try:
        from app.worker.tasks.boletas import generar_boletas_grupo
    except ImportError:
        raise HTTPException(status_code=503, detail="Celery worker no disponible")

    task = generar_boletas_grupo.delay(
        grupo_id=str(grupo_id),
        ciclo_id=str(ciclo_id) if ciclo_id else None,
        solicitado_por=str(_user.get("sub", "api")),
    )
    return BatchBoletasOut(
        task_id=task.id,
        estado="encolado",
        mensaje="Generación de boletas iniciada. Use GET /boletas/tarea/{task_id} para consultar el estado.",
    )


@router.get("/tarea/{task_id}", response_model=TareaEstadoOut)
async def estado_tarea(
    task_id: str,
    _user: dict = Depends(get_current_user),
):
    """Consulta el estado de una tarea Celery de generación batch."""
    try:
        from celery.result import AsyncResult
        from app.worker.celery_app import celery_app
    except ImportError:
        raise HTTPException(status_code=503, detail="Celery no disponible")

    result = AsyncResult(task_id, app=celery_app)
    estado = result.state

    if estado == "PENDING":
        return TareaEstadoOut(task_id=task_id, estado="PENDIENTE")
    if estado == "PROGRESS":
        return TareaEstadoOut(task_id=task_id, estado="EN_PROGRESO", progreso=result.info)
    if estado == "SUCCESS":
        return TareaEstadoOut(task_id=task_id, estado="COMPLETADO", resultado=result.result)
    if estado == "FAILURE":
        return TareaEstadoOut(task_id=task_id, estado="ERROR", error=str(result.result))
    return TareaEstadoOut(task_id=task_id, estado=estado)

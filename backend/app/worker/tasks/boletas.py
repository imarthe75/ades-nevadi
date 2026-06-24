"""
Tarea Celery: generación batch de boletas PDF para un grupo completo.

Flujo:
  1. Obtener todos los alumnos activos del grupo.
  2. Para cada alumno, llamar al mismo código que el endpoint /boletas/{id}.
  3. Comprimir todos los PDFs en un ZIP en memoria.
  4. Subir el ZIP a MinIO → bucket "ades-archivos", key "boletas/<grupo_id>/<ciclo>/<timestamp>.zip".
  5. Generar URL firmada (presigned) válida 24 h.
  6. Persistir resultado en ades_tareas_celery (id, grupo_id, estado, url, error).

La tarea es idempotente: si se ejecuta dos veces con los mismos parámetros,
genera un nuevo archivo (timestamp diferente) sin borrar el anterior.
"""
from __future__ import annotations

import io
import uuid
import zipfile
import logging
from datetime import date, timedelta
from pathlib import Path

from celery import shared_task
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, selectinload

log = logging.getLogger(__name__)

TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "boletas"


def _get_db_engine():
    """Crea un engine SQLAlchemy síncrono usando DATABASE_URL_SYNC de settings.

    Se instancia dentro de la tarea (no en el módulo) para evitar compartir
    conexiones entre procesos worker de Celery.
    """
    from app.core.config import settings
    return create_engine(settings.DATABASE_URL_SYNC, pool_pre_ping=True)


def _get_minio_client():
    """Crea un cliente Minio apuntando al endpoint configurado en settings.

    Utiliza MINIO_ENDPOINT, MINIO_ACCESS_KEY y MINIO_SECRET_KEY del entorno.
    El bucket de destino es ``MINIO_BUCKET`` (por defecto ``ades-archivos``).
    """
    from app.core.config import settings
    from minio import Minio
    return Minio(
        settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        secure=settings.MINIO_SECURE,
    )


def _generar_pdf_alumno(session: Session, estudiante_id: uuid.UUID, ciclo_id: uuid.UUID | None) -> bytes | None:
    """Reutiliza la lógica síncrona del endpoint de boletas individuales."""
    from app.models.personas import Estudiante, Persona, Inscripcion
    from app.models.academica import CicloEscolar, Grupo, Grado, PlantelNivel, Plantel, NivelEducativo
    from app.models.operacion import PeriodoEvaluacion, CalificacionPeriodo
    from app.models.materias import MateriaPlan

    PLANTEL_INFO = {
        "Metepec":           {"direccion": "Prol. Heriberto Enríquez 1001", "tel": "722-297-1441"},
        "Tenancingo":        {"direccion": "Carretera Tenancingo-Tenería S/N", "tel": "714-142-4323"},
        "Ixtapan de la Sal": {"direccion": "Independencia Pte. 5", "tel": "721-143-3015"},
    }

    estudiante = session.get(Estudiante, estudiante_id, options=[selectinload(Estudiante.persona)])
    if not estudiante:
        return None

    inscr_q = (
        select(Inscripcion)
        .join(CicloEscolar, CicloEscolar.id == Inscripcion.ciclo_escolar_id)
        .where(Inscripcion.estudiante_id == estudiante_id, Inscripcion.is_active == True)
    )
    if ciclo_id:
        inscr_q = inscr_q.where(Inscripcion.ciclo_escolar_id == ciclo_id)
    else:
        inscr_q = inscr_q.where(CicloEscolar.es_vigente == True)
    inscripcion = session.execute(inscr_q).scalar_one_or_none()
    if not inscripcion:
        return None

    ciclo = session.get(CicloEscolar, inscripcion.ciclo_escolar_id)
    grupo = session.get(Grupo, inscripcion.grupo_id)
    grado = session.get(Grado, grupo.grado_id) if grupo else None
    plantel = session.get(Plantel, estudiante.plantel_id)

    nivel_nombre = "—"
    if grado and plantel:
        pn = session.execute(
            select(PlantelNivel).where(PlantelNivel.plantel_id == plantel.id).limit(1)
        ).scalar_one_or_none()
        if pn:
            nivel = session.get(NivelEducativo, pn.nivel_educativo_id)
            nivel_nombre = nivel.nombre_nivel if nivel else "—"

    periodos = session.execute(
        select(PeriodoEvaluacion)
        .where(
            PeriodoEvaluacion.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            PeriodoEvaluacion.is_active == True,
        )
        .order_by(PeriodoEvaluacion.numero_periodo)
    ).scalars().all()

    planes = session.execute(
        select(MateriaPlan).options(selectinload(MateriaPlan.materia))
        .where(
            MateriaPlan.grado_id == grado.id if grado else False,
            MateriaPlan.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            MateriaPlan.is_active == True,
        )
        .order_by(MateriaPlan.orden)
    ).scalars().all()

    cals = session.execute(
        select(CalificacionPeriodo)
        .where(
            CalificacionPeriodo.estudiante_id == estudiante_id,
            CalificacionPeriodo.grupo_id == inscripcion.grupo_id,
        )
    ).scalars().all()

    cal_map = {
        (str(c.materia_id), str(c.periodo_evaluacion_id)): float(c.calificacion_final)
        for c in cals
    }

    # nivel_logro — columna agregada en mig 089; consultar aparte para no tocar el modelo ORM
    from sqlalchemy import text as _text
    import json as _json
    logro_rows = session.execute(_text(
        "SELECT materia_id::text AS m, periodo_evaluacion_id::text AS p, nivel_logro AS n "
        "FROM ades_calificaciones_periodo "
        "WHERE estudiante_id = :est AND grupo_id = :g AND nivel_logro IS NOT NULL"
    ), {"est": str(estudiante_id), "g": str(inscripcion.grupo_id)}).mappings().all()
    logro_map = {(r["m"], r["p"]): r["n"] for r in logro_rows}

    materias_data, promedios_mat = [], []
    for plan in planes:
        mat_cals: dict[str, float | None] = {}
        mat_logros: dict[str, str | None] = {}
        suma = 0.0
        conteo = 0
        for p in periodos:
            val = cal_map.get((str(plan.materia_id), str(p.id)))
            logro = logro_map.get((str(plan.materia_id), str(p.id)))
            mat_cals[p.nombre_periodo] = val
            mat_logros[p.nombre_periodo] = logro
            if val is not None:
                suma += val
                conteo += 1
        promedio = round(suma / conteo, 2) if conteo else None
        if promedio is not None:
            promedios_mat.append(promedio)
        materias_data.append({
            "materia_nombre": plan.materia.nombre_materia if plan.materia else "—",
            "campo_formativo": getattr(plan.materia, "campo_formativo", None) if plan.materia else None,
            "calificaciones": mat_cals,
            "logros": mat_logros,
            "promedio": promedio,
            "acreditado": (promedio >= 6.0) if promedio is not None else False,
        })

    promedio_general = round(sum(promedios_mat) / len(promedios_mat), 2) if promedios_mat else None
    # NEM: acredita el grado si tiene promedio y toda materia evaluada es ≥ 6.
    acredito_grado = (
        promedio_general is not None
        and all(m["acreditado"] for m in materias_data if m["promedio"] is not None)
    )

    # ── Agrupación por Campo Formativo (NEM, educación básica) ──────────────
    CAMPOS_NEM = [
        ("LENGUAJES",                      "Lenguajes"),
        ("SABERES_PENSAMIENTO_CIENTIFICO", "Saberes y Pensamiento Científico"),
        ("ETICA_NATURALEZA_SOCIEDADES",    "Ética, Naturaleza y Sociedades"),
        ("HUMANO_COMUNITARIO",             "De lo Humano y lo Comunitario"),
    ]
    es_nem = any(m["campo_formativo"] for m in materias_data)
    campos = []
    if es_nem:
        for code, label in CAMPOS_NEM:
            mats = [m for m in materias_data if m["campo_formativo"] == code]
            if mats:
                campos.append({"campo": label, "materias": mats})
        sin_campo = [m for m in materias_data if not m["campo_formativo"]]
        if sin_campo:
            campos.append({"campo": "Otras asignaturas", "materias": sin_campo})

    # ── Evaluación cualitativa NEM (1°-2° primaria) ────────────────────────
    numero_grado_val = grado.numero_grado if grado else 0
    es_primaria = "PRIMARIA" in nivel_nombre.upper()

    # Cargar config y escala desde BD
    grados_cualit = [1, 2]
    mostrar_equiv_num = True
    cual_descriptores: list[dict] = []

    if es_nem and es_primaria:
        cfg_rows = session.execute(_text(
            "SELECT clave, valor::text AS v FROM ades_config WHERE grupo = 'evaluacion_cualitativa'"
        )).mappings().all()
        for c in cfg_rows:
            parsed = _json.loads(c["v"])
            if c["clave"] == "EVAL_CUAL_GRADOS_PRIMARIA" and isinstance(parsed, list):
                grados_cualit = [int(g) for g in parsed]
            elif c["clave"] == "EVAL_CUAL_MOSTRAR_EQUIVALENCIA":
                mostrar_equiv_num = bool(parsed)

        escala_json = session.execute(_text(
            "SELECT valores_json::text AS v FROM ades_escalas_evaluacion "
            "WHERE nivel_educativo = 'PRIMARIA' AND is_active = true "
            "ORDER BY fecha_creacion DESC LIMIT 1"
        )).scalar_one_or_none()
        if escala_json:
            cual_descriptores = _json.loads(escala_json)

    es_cualitativa = es_nem and es_primaria and numero_grado_val in grados_cualit

    # ── Asistencias y observaciones del ciclo ──────────────────────────────
    from sqlalchemy import text
    asis = session.execute(text(
        """
        SELECT COUNT(*) FILTER (WHERE a.estatus_asistencia = 'AUSENTE') AS faltas,
               COUNT(*) FILTER (WHERE a.estatus_asistencia = 'AUSENTE'
                                AND a.justificacion_id IS NOT NULL) AS justificadas
        FROM ades_asistencias a
        JOIN ades_clases cl ON cl.id = a.clase_id
        WHERE a.estudiante_id = :est AND cl.grupo_id = :grupo
        """
    ), {"est": estudiante_id, "grupo": inscripcion.grupo_id}).mappings().first()
    faltas = (asis["faltas"] if asis else 0) or 0
    faltas_justificadas = (asis["justificadas"] if asis else 0) or 0

    obs_rows = session.execute(text(
        """
        SELECT observacion FROM ades_observaciones_pedagogicas
        WHERE alumno_id = :est
        ORDER BY fecha_creacion DESC LIMIT 3
        """
    ), {"est": estudiante_id}).scalars().all()
    observaciones = "  •  ".join(o for o in obs_rows if o) or "Sin observaciones."

    plantel_nombre = plantel.nombre_plantel if plantel else "—"
    info = PLANTEL_INFO.get(plantel_nombre, {"direccion": "", "tel": ""})
    persona = estudiante.persona
    nombre_completo = (
        f"{persona.apellido_paterno} {persona.apellido_materno or ''} {persona.nombre}".strip()
        if persona else "—"
    )
    grado_grupo_str = f"{grado.nombre_grado} — Grupo {grupo.nombre_grupo[-1]}" if grado and grupo else "—"
    periodo_nombres = [p.nombre_periodo for p in periodos]

    ctx = {
        "plantel_nombre": plantel_nombre,
        "cct": getattr(plantel, "clave_ct", "") or "—",
        "ciclo_nombre": ciclo.nombre_ciclo if ciclo else "—",
        "fecha_generacion": date.today().strftime("%d/%m/%Y"),
        "nombre_completo": nombre_completo,
        "curp": getattr(persona, "curp", None) or "—",
        "matricula": estudiante.matricula or "—",
        "grado_grupo": grado_grupo_str,
        "nivel_educativo": nivel_nombre,
        "periodos": periodo_nombres,
        "materias": materias_data,
        "es_nem": es_nem,
        "campos": campos,
        "promedio_general": promedio_general,
        "acredito_grado": acredito_grado,
        "faltas": faltas,
        "faltas_justificadas": faltas_justificadas,
        "observaciones": observaciones,
        "plantel_direccion": info["direccion"],
        "plantel_telefono": info["tel"],
        "es_cualitativa": es_cualitativa,
        "cual_descriptores": cual_descriptores,
        "mostrar_equiv_num": mostrar_equiv_num,
    }

    from jinja2 import Environment, FileSystemLoader
    jinja_env = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jinja_env.get_template("boleta.html").render(**ctx)

    from weasyprint import HTML
    return HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()


@shared_task(bind=True, name="app.worker.tasks.boletas.generar_boletas_grupo")
def generar_boletas_grupo(
    self,
    grupo_id: str,
    ciclo_id: str | None = None,
    solicitado_por: str = "sistema",
) -> dict:
    """
    Genera boletas PDF para todos los alumnos del grupo y sube el ZIP a MinIO.

    Returns:
        {"estado": "ok", "url": "<presigned URL>", "total": N, "errores": M}
    """
    from app.models.academica import Grupo
    from app.models.personas import Inscripcion, Estudiante
    from app.core.config import settings

    self.update_state(state="PROGRESS", meta={"mensaje": "Consultando alumnos del grupo"})

    engine = _get_db_engine()
    minio = _get_minio_client()
    bucket = settings.MINIO_BUCKET

    # Asegurar bucket existe
    if not minio.bucket_exists(bucket):
        minio.make_bucket(bucket)

    grupo_uuid = uuid.UUID(grupo_id)
    ciclo_uuid = uuid.UUID(ciclo_id) if ciclo_id else None

    with Session(engine) as session:
        grupo = session.get(Grupo, grupo_uuid)
        if not grupo:
            raise ValueError(f"Grupo {grupo_id} no encontrado")

        # Alumnos activos del grupo en el ciclo indicado (o vigente)
        from app.models.academica import CicloEscolar
        inscr_q = (
            select(Inscripcion)
            .join(CicloEscolar, CicloEscolar.id == Inscripcion.ciclo_escolar_id)
            .where(Inscripcion.grupo_id == grupo_uuid, Inscripcion.is_active == True)
        )
        if ciclo_uuid:
            inscr_q = inscr_q.where(Inscripcion.ciclo_escolar_id == ciclo_uuid)
        else:
            inscr_q = inscr_q.where(CicloEscolar.es_vigente == True)

        inscripciones = session.execute(inscr_q).scalars().all()
        total = len(inscripciones)
        if total == 0:
            return {"estado": "sin_alumnos", "url": None, "total": 0, "errores": 0}

        self.update_state(state="PROGRESS", meta={"mensaje": f"Generando {total} boletas", "total": total, "procesados": 0})

        zip_buffer = io.BytesIO()
        errores = 0
        with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zf:
            for i, inscr in enumerate(inscripciones):
                try:
                    pdf = _generar_pdf_alumno(session, inscr.estudiante_id, ciclo_uuid)
                    if pdf:
                        estudiante = session.get(Estudiante, inscr.estudiante_id)
                        matricula = estudiante.matricula if estudiante else str(inscr.estudiante_id)[:8]
                        zf.writestr(f"boleta_{matricula}.pdf", pdf)
                    else:
                        errores += 1
                except Exception as exc:
                    log.warning("boleta_error", estudiante_id=str(inscr.estudiante_id), error=str(exc))
                    errores += 1
                self.update_state(state="PROGRESS", meta={"total": total, "procesados": i + 1})

    zip_buffer.seek(0)
    zip_size = len(zip_buffer.getvalue())
    timestamp = date.today().strftime("%Y%m%d")
    object_name = f"boletas/{grupo_id}/{timestamp}_{self.request.id}.zip"

    minio.put_object(
        bucket,
        object_name,
        zip_buffer,
        length=zip_size,
        content_type="application/zip",
    )

    url = minio.presigned_get_object(bucket, object_name, expires=timedelta(hours=24))

    log.info("boletas_grupo_ok", grupo_id=grupo_id, total=total, errores=errores, url=url)
    return {"estado": "ok", "url": url, "total": total, "errores": errores}

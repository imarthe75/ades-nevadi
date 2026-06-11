from fastapi import APIRouter
from .auth_callback import router as auth_callback_router
from .imports import router as imports_router
from .admin import router as admin_router
from .contactos import router as contactos_router
from .expediente import router as expediente_router
from .health import router as health_router
from .catalogs import router as catalogs_router
from .planteles import router as planteles_router
from .grupos import router as grupos_router
from .materias import router as materias_router
from .alumnos import router as alumnos_router
from .profesores import router as profesores_router
from .usuarios import router as usuarios_router
from .stats import router as stats_router
# FASE 2
from .clases import router as clases_router
from .asistencias import router as asistencias_router
from .calificaciones import router as calificaciones_router
from .tareas import router as tareas_router
# FASE 3
from .horarios import router as horarios_router
from .medico import router as medico_router
from .conducta import router as conducta_router
from .boletas import router as boletas_router
from .eval_docente import router as eval_docente_router
# FASE 4
from .ai_assistant import router as ai_router
from .learning_paths import router as learning_paths_router
# FASE 5
from .comunicados import router as comunicados_router
from .notificaciones import router as notificaciones_router
from .grade_analytics import router as grade_analytics_router
# FASE 6
from .evaluaciones import router as evaluaciones_router
from .planeacion import router as planeacion_router
from .rubricas import router as rubricas_router
from .certificados import router as certificados_router
# FASE 7
from .encuestas import router as encuestas_router
# FASE 8
from .badges import router as badges_router
# FASE 9
from .portal import router as portal_router
# FASE 10 — Gradebook Curricular
from .esquemas_ponderacion import router as esquemas_ponderacion_router
from .actividades import router as actividades_router
from .entregas import router as entregas_router
from .gradebook import router as gradebook_router

api_router = APIRouter()

# ── Auth callback proxy (OIDC token exchange sin CORS) ────────────────────────
api_router.include_router(auth_callback_router)

# ── Importación masiva CSV/Excel ───────────────────────────────────────────────
api_router.include_router(imports_router)

# ── FASE 12 — Módulo de Administración ────────────────────────────────────────
api_router.include_router(admin_router)

# ── Portal de Padres de Familia ───────────────────────────────────────────────
from .padres import router as padres_router
api_router.include_router(padres_router)

# ── Contactos familiares + expediente médico ───────────────────────────────────
api_router.include_router(contactos_router)

# ── Expediente académico (bajas, extraordinarios, constancias) ─────────────────
api_router.include_router(expediente_router)

# ── FASE 1 ────────────────────────────────────────────────────────────────────
api_router.include_router(health_router)
api_router.include_router(catalogs_router)
api_router.include_router(planteles_router)
api_router.include_router(grupos_router)
api_router.include_router(materias_router)
api_router.include_router(alumnos_router)
api_router.include_router(profesores_router)
api_router.include_router(usuarios_router)
api_router.include_router(stats_router)

# ── FASE 2 ────────────────────────────────────────────────────────────────────
api_router.include_router(clases_router)
api_router.include_router(asistencias_router)
api_router.include_router(calificaciones_router)
api_router.include_router(tareas_router)

# ── FASE 3 ────────────────────────────────────────────────────────────────────
api_router.include_router(horarios_router)
api_router.include_router(medico_router)
api_router.include_router(conducta_router)
api_router.include_router(boletas_router)
api_router.include_router(eval_docente_router)

# ── FASE 4 ────────────────────────────────────────────────────────────────────
api_router.include_router(ai_router)
api_router.include_router(learning_paths_router)

# ── FASE 5 ────────────────────────────────────────────────────────────────────
api_router.include_router(comunicados_router)
api_router.include_router(notificaciones_router)
api_router.include_router(grade_analytics_router)

# ── FASE 6 ────────────────────────────────────────────────────────────────────
api_router.include_router(evaluaciones_router)
api_router.include_router(planeacion_router)
api_router.include_router(rubricas_router)
api_router.include_router(certificados_router)

# ── FASE 7 ────────────────────────────────────────────────────────────────────
api_router.include_router(encuestas_router)

# ── FASE 8 ────────────────────────────────────────────────────────────────────
api_router.include_router(badges_router)

# ── FASE 9 ────────────────────────────────────────────────────────────────────
api_router.include_router(portal_router)

# ── FASE 10 — Gradebook Curricular ────────────────────────────────────────────
api_router.include_router(esquemas_ponderacion_router)
api_router.include_router(actividades_router)
api_router.include_router(entregas_router)
api_router.include_router(gradebook_router)

# ── FASE 15 — Auditoría ───────────────────────────────────────────────────────
from .auditoria import router as auditoria_router  # noqa: E402
api_router.include_router(auditoria_router)

# ── FASE 16 — Superset embedded dashboards ────────────────────────────────────
from .superset import router as superset_router  # noqa: E402
api_router.include_router(superset_router)

# ── FASE 17 — AI Chatbot (Flowise + Vanna NL→SQL) ────────────────────────────
from .chatbot import router as chatbot_router  # noqa: E402
api_router.include_router(chatbot_router)

# ── FASE 18 — Carbone generador de reportes ───────────────────────────────────
from .carbone import router as carbone_router  # noqa: E402
api_router.include_router(carbone_router)

# ── FASE 20 — ntfy Push Notifications ────────────────────────────────────────
from .push import router as push_router  # noqa: E402
api_router.include_router(push_router)

# ── FASE 21 — Stirling-PDF herramientas PDF ───────────────────────────────────
from .pdf_tools import router as pdf_tools_router  # noqa: E402
api_router.include_router(pdf_tools_router)

# ── FASE 23 — n8n automatización de flujos ───────────────────────────────────
from .automations import router as automations_router  # noqa: E402
api_router.include_router(automations_router)

# ── FASE 26-A — Variables del Sistema + Catálogos Dinámicos ──────────────────
from .catalogos_sistema import router as catalogos_sistema_router  # noqa: E402
api_router.include_router(catalogos_sistema_router)

# ── FASE 26-B — Menús Dinámicos ───────────────────────────────────────────────
from .menus import router as menus_router  # noqa: E402
api_router.include_router(menus_router)

# ── FASE 26-E — SEPOMEX Geográfico ──────────────────────────────────────────────
from .geo import router as geo_router  # noqa: E402
api_router.include_router(geo_router)

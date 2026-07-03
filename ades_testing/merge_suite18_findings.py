#!/usr/bin/env python3
"""
Fusiona los hallazgos deterministas de la Suite Playwright TS 18 (topbar/sidebar,
frontend/e2e/tests/18-topbar-sidebar.spec.ts) dentro de analysis/inconsistencies_report.json
para que 03_report_generator.py los incluya en el HTML/CSV/matriz finales junto con
los hallazgos cognitivos de 02_claude_qa_analyzer.py.

Estos hallazgos NO vienen de un LLM — son verificaciones deterministas (status HTTP,
DOM, sessionStorage) confirmadas manualmente tras descartar falsos positivos de
timing en el propio test (ver commit del spec 18 para detalle).

Ejecución: python3 merge_suite18_findings.py
"""
import json
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent.absolute()
REPORT_PATH = SCRIPT_DIR / "analysis" / "inconsistencies_report.json"

SUITE18_FINDINGS = [
    {
        "severity": "Crítico",
        "module_id": "estadistica_911",
        "type": "API Error",
        "description": "GET /api/v1/reportes/911 retorna HTTP 500 al navegar al módulo Formato 911 SEP desde el sidenav.",
        "location": "/estadistica-911 → GET /api/v1/reportes/911",
        "evidence": "Suite Playwright 18-topbar-sidebar.spec.ts NAV-08: 500 https://ades.setag.mx/api/v1/reportes/911",
        "impact": "El reporte oficial SEP 911 (matriz edad×grado×sexo×ingreso) no puede generarse — bloquea entrega de reporte obligatorio SEP.",
        "suggestion": "Revisar logs del BFF Spring en el endpoint /api/v1/reportes/911 — ya documentado como pendiente en .agent/STATE.md.",
    },
    {
        "severity": "Alto",
        "module_id": "planes_estudio",
        "type": "API Error",
        "description": "GET /api/v1/planes-estudio?ciclo_id=... retorna HTTP 500 al navegar al módulo Planes de Estudio.",
        "location": "/planes-estudio → GET /api/v1/planes-estudio?ciclo_id=019e8f74-d148-7c7c-94de-f1500e73faed",
        "evidence": "Suite Playwright 18-topbar-sidebar.spec.ts NAV-08: 500 con ciclo_id del contexto activo del topbar",
        "impact": "El listado de planes de estudio no carga para el ciclo escolar vigente — afecta configuración curricular NEM/CBU.",
        "suggestion": "Revisar el query param ciclo_id en el controller/QueryService de planes-estudio; probable NPE o casteo fallido con el UUID del ciclo vigente.",
    },
    {
        "severity": "Medio",
        "module_id": "videoconferencias",
        "type": "API Error",
        "description": "GET /api/v1/bbb/info retorna HTTP 503 al navegar a Videoconferencias.",
        "location": "/videoconferencias → GET /api/v1/bbb/info",
        "evidence": "Suite Playwright 18-topbar-sidebar.spec.ts NAV-08: 503 https://ades.setag.mx/api/v1/bbb/info",
        "impact": "El módulo de videoconferencias BBB no puede mostrar el estado del servidor — usuario no puede iniciar/unirse a sesiones.",
        "suggestion": "Verificar que BBB_SERVER_URL/BBB_SECRET estén configurados y que el contenedor BBB responda — puede ser degradación esperada si BBB está apagado en dev.",
    },
    {
        "severity": "Alto",
        "module_id": "monitor_sistema",
        "type": "API Error",
        "description": "GET /api/v1/superset/dashboards retorna HTTP 500 desde Monitor del Sistema y Administración, con TypeError 'Failed to fetch' visible en consola.",
        "location": "/monitor y /admin → GET /api/v1/superset/dashboards",
        "evidence": "Suite Playwright 18-topbar-sidebar.spec.ts NAV-08: 500 en ambos módulos + console error 'runRequest.catchError TypeError: Failed to fetch'",
        "impact": "Los dashboards embebidos de Superset no cargan en Monitor del Sistema ni en Administración — coincide con OIDC de Superset pendiente de configurar (ver .agent/STATE.md).",
        "suggestion": "Completar configuración OIDC de Superset (SUPERSET_OIDC_CLIENT_SECRET pendiente) — ya identificado como deuda conocida.",
    },
    {
        "severity": "Crítico",
        "module_id": "eval_docente",
        "type": "Error Hidden",
        "description": (
            "TypeError 'this.profesores(...).map is not a function' se dispara repetidamente "
            "(7 veces en una sola carga) en un computed() de Angular — el signal profesores() no "
            "devuelve un array (probablemente un objeto paginado {content: [...]} sin desenvolver)."
        ),
        "location": "/eval-docente → chunk-EIBD3XTG.js:92 (computed signal de la lista de profesores)",
        "evidence": "01_ades_explorer_v4_complete.py captures/captures_summary.json → eval_docente: 7x console error idéntico",
        "impact": "El selector/lista de profesores en Evaluación Docente 360° probablemente no renderiza — bloquea crear o ver evaluaciones.",
        "suggestion": "Revisar el computed() que consume profesores() en el componente eval-docente; desenvolver `.content` si la API de profesores pagina la respuesta.",
    },
    {
        "severity": "Medio",
        "module_id": "reportes",
        "type": "Error Hidden",
        "description": "TypeError \"Cannot read properties of null (reading 'writeValue')\" en el módulo de Reportes — un ControlValueAccessor de Angular recibe una referencia nula.",
        "location": "/reportes → chunk-3VDUTEHQ.js:1 (form control)",
        "evidence": "01_ades_explorer_v4_complete.py captures/captures_summary.json → reportes: console error 'writeValue'",
        "impact": "Un campo de formulario en el generador de reportes puede no inicializarse correctamente (valor no se refleja en el control).",
        "suggestion": "Revisar el ciclo de vida del FormControl/ControlValueAccessor afectado — probable acceso antes de que Angular complete la inicialización del componente.",
    },
    {
        "severity": "Medio",
        "module_id": "global_push_notifications",
        "type": "API Error",
        "description": "GET /api/v1/push/suscripcion retorna error en los 52/52 módulos probados — es una llamada global del shell (PushNotificationService.init(), ver ShellComponent.ngOnInit), no específica de un módulo.",
        "location": "ShellComponent (global) → GET /api/v1/push/suscripcion",
        "evidence": "01_ades_explorer_v4_complete.py captures_summary.json: 52/52 capturas registran este error de red",
        "impact": "Las notificaciones push (ntfy) probablemente no se activan para ningún usuario — el código ya captura el error como 'modo degradado' así que no rompe la UI, pero la feature está silenciosamente inactiva.",
        "suggestion": "Verificar el endpoint /api/v1/push/suscripcion en el BFF y la config de ntfy — confirmar si es un problema de suscripción por usuario o una regresión del endpoint completo.",
    },
    {
        "severity": "Bajo",
        "module_id": "topbar_cascada",
        "type": "Validation Missing",
        "description": (
            "No se pudo confirmar con certeza que Nivel/Grado/Grupo se recalculen correctamente al cambiar "
            "Plantel/Nivel/Grado a un valor ESPECÍFICO (no al comodín '— Todos —') — el test automatizado "
            "seleccionó el valor comodín por ser la primera opción distinta, lo cual no es una prueba concluyente."
        ),
        "location": "Topbar → ContextCatalogService (onPlantelChange/onNivelChange/onGradoChange)",
        "evidence": "Suite Playwright 18-topbar-sidebar.spec.ts NAV-02/03/05 — requiere verificación manual con un plantel/nivel/grado concreto",
        "impact": "Riesgo bajo/desconocido — no confirmado como bug real, solo señalado como zona a verificar manualmente.",
        "suggestion": "QA manual: seleccionar un plantel concreto (no 'Todo el Instituto') y confirmar que Nivel/Ciclo/Grado/Grupo se recargan con datos de ese plantel específico.",
    },
]


def main():
    if REPORT_PATH.exists():
        with open(REPORT_PATH, encoding="utf-8") as f:
            report = json.load(f)
    else:
        report = {"model_used": "n/a", "total_inconsistencies": 0, "by_severity": {}, "by_type": {}, "inconsistencies": []}

    existing_evidence = {i.get("evidence") for i in report.get("inconsistencies", [])}
    added = 0
    for finding in SUITE18_FINDINGS:
        if finding["evidence"] in existing_evidence:
            continue
        report["inconsistencies"].append(finding)
        added += 1

    severity_order = {"Crítico": 0, "Alto": 1, "Medio": 2, "Bajo": 3}
    report["inconsistencies"].sort(key=lambda x: severity_order.get(x.get("severity", "Bajo"), 4))

    by_severity = {"critico": 0, "alto": 0, "medio": 0, "bajo": 0}
    for i in report["inconsistencies"]:
        s = i.get("severity", "").lower()
        key = {"crítico": "critico", "alto": "alto", "medio": "medio", "bajo": "bajo"}.get(s, "bajo")
        by_severity[key] += 1

    by_type = {}
    for i in report["inconsistencies"]:
        t = i.get("type", "Unknown")
        by_type[t] = by_type.get(t, 0) + 1

    report["by_severity"] = by_severity
    report["by_type"] = by_type
    report["total_inconsistencies"] = len(report["inconsistencies"])

    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"✓ {added} hallazgo(s) de Suite 18 fusionados en {REPORT_PATH}")
    print(f"  Total inconsistencias combinadas: {report['total_inconsistencies']}")
    print(f"  Por severidad: {by_severity}")


if __name__ == "__main__":
    main()

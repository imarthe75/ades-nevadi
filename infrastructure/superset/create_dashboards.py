"""
Script de inicialización de dashboards ADES en Apache Superset 6.x.
Crea datasets, charts y dashboards a partir de las vistas materializadas en ades_bi.

Ejecutar dentro del contenedor:
  docker compose exec superset python3 /app/pythonpath/create_dashboards.py
"""
import os, sys, json, uuid
sys.path.insert(0, "/app/pythonpath")
os.environ["SUPERSET_CONFIG_PATH"] = "/app/pythonpath/superset_config.py"

from superset import create_app
from superset.extensions import db

app = create_app()
with app.app_context():
    from superset.models.core import Database
    from superset.models.dashboard import Dashboard
    from superset.connectors.sqla.models import SqlaTable
    from superset.models.slice import Slice

    # ─── Datasource ──────────────────────────────────────────────────────────
    ades_db = db.session.query(Database).filter_by(database_name="ADES BI").first()
    if not ades_db:
        print("ERROR: Datasource 'ADES BI' no encontrado. Ejecuta docker-init.sh primero.")
        sys.exit(1)
    print(f"✅ Datasource: {ades_db.database_name} (id={ades_db.id})")

    # ─── Datasets (vistas materializadas) ────────────────────────────────────
    VIEWS = [
        ("mv_resumen_plantel",      "ades_bi", "Resumen por Plantel"),
        ("mv_calificaciones_grupo", "ades_bi", "Calificaciones por Grupo"),
        ("mv_riesgo_academico",     "ades_bi", "Riesgo Académico"),
        ("mv_asistencia_diaria",    "ades_bi", "Asistencia Diaria"),
    ]

    datasets: dict[str, SqlaTable] = {}
    for table_name, schema, label in VIEWS:
        existing = db.session.query(SqlaTable).filter_by(
            table_name=table_name, schema=schema, database_id=ades_db.id
        ).first()
        if not existing:
            t = SqlaTable(
                table_name=table_name,
                schema=schema,
                database_id=ades_db.id,
                database=ades_db,
                is_sqllab_view=False,
            )
            db.session.add(t)
            db.session.flush()
            print(f"  ➕ Dataset creado: {schema}.{table_name}")
        else:
            t = existing
            print(f"  ✔  Dataset existente: {schema}.{table_name}")
        datasets[table_name] = t

    db.session.commit()

    # ─── Charts básicos ───────────────────────────────────────────────────────
    def make_chart(name, viz_type, datasource, params: dict) -> Slice:
        existing = db.session.query(Slice).filter_by(slice_name=name).first()
        if existing:
            print(f"  ✔  Chart existente: {name}")
            return existing
        s = Slice(
            slice_name=name,
            viz_type=viz_type,
            datasource_type="table",
            datasource_id=datasource.id,
            params=json.dumps(params),
        )
        db.session.add(s)
        db.session.flush()
        print(f"  ➕ Chart creado: {name}")
        return s

    # Charts para el dashboard Instituto
    chart_alumnos = make_chart(
        "Total Alumnos por Plantel",
        "bar",
        datasets["mv_resumen_plantel"],
        {
            "metrics": [{"aggregate": "SUM", "column": {"column_name": "total_alumnos"}, "expressionType": "SIMPLE", "label": "Total Alumnos"}],
            "groupby": ["nombre_plantel"],
            "viz_type": "bar",
            "time_range": "No filter",
        },
    )
    chart_asistencia_kpi = make_chart(
        "% Asistencia Media por Plantel",
        "big_number",
        datasets["mv_resumen_plantel"],
        {
            "metric": {"aggregate": "AVG", "column": {"column_name": "pct_asistencia_media"}, "expressionType": "SIMPLE", "label": "% Asistencia"},
            "viz_type": "big_number",
            "time_range": "No filter",
        },
    )
    chart_riesgo_pie = make_chart(
        "Distribución Riesgo Académico",
        "pie",
        datasets["mv_riesgo_academico"],
        {
            "metrics": [{"aggregate": "COUNT", "column": {"column_name": "alumno_id"}, "expressionType": "SIMPLE", "label": "Alumnos"}],
            "groupby": ["nivel_riesgo"],
            "viz_type": "pie",
            "time_range": "No filter",
        },
    )

    # Charts para el dashboard Plantel
    chart_cal_grupo = make_chart(
        "Calificación Promedio por Materia",
        "bar",
        datasets["mv_calificaciones_grupo"],
        {
            "metrics": [{"aggregate": "AVG", "column": {"column_name": "promedio_grupo"}, "expressionType": "SIMPLE", "label": "Promedio"}],
            "groupby": ["nombre_materia"],
            "viz_type": "bar",
            "time_range": "No filter",
        },
    )
    chart_asistencia_trend = make_chart(
        "Tendencia Asistencia Diaria",
        "line",
        datasets["mv_asistencia_diaria"],
        {
            "metrics": [{"aggregate": "AVG", "column": {"column_name": "pct_asistencia"}, "expressionType": "SIMPLE", "label": "% Asistencia"}],
            "groupby": [],
            "granularity_sqla": "fecha",
            "viz_type": "line",
            "time_range": "No filter",
        },
    )

    # Charts para dashboard Docente
    chart_cal_docente = make_chart(
        "Calificaciones por Grupo (Docente)",
        "table",
        datasets["mv_calificaciones_grupo"],
        {
            "metrics": [{"aggregate": "AVG", "column": {"column_name": "promedio_grupo"}, "expressionType": "SIMPLE", "label": "Promedio"}],
            "groupby": ["nombre_grupo", "nombre_materia", "nombre_periodo"],
            "viz_type": "table",
            "time_range": "No filter",
        },
    )

    # Charts para dashboard Alumno
    chart_riesgo_alumno = make_chart(
        "Alumnos en Riesgo Alto",
        "table",
        datasets["mv_riesgo_academico"],
        {
            "metrics": [{"aggregate": "COUNT", "column": {"column_name": "alumno_id"}, "expressionType": "SIMPLE", "label": "Alumnos"}],
            "groupby": ["nombre_alumno", "nivel_riesgo", "nombre_plantel"],
            "viz_type": "table",
            "adhoc_filters": [{"clause": "WHERE", "expressionType": "SIMPLE", "subject": "nivel_riesgo", "comparator": "alto", "operator": "=="}],
            "time_range": "No filter",
        },
    )

    db.session.commit()

    # ─── Dashboards ───────────────────────────────────────────────────────────
    DASHBOARDS = [
        {
            "title": "ADES — Instituto Nevadi",
            "env_key": "SUPERSET_DASHBOARD_INSTITUTO",
            "charts": [chart_alumnos, chart_asistencia_kpi, chart_riesgo_pie],
        },
        {
            "title": "ADES — Mi Plantel",
            "env_key": "SUPERSET_DASHBOARD_PLANTEL",
            "charts": [chart_cal_grupo, chart_asistencia_trend, chart_riesgo_pie],
        },
        {
            "title": "ADES — Vista Docente",
            "env_key": "SUPERSET_DASHBOARD_DOCENTE",
            "charts": [chart_cal_docente, chart_asistencia_trend],
        },
        {
            "title": "ADES — Progreso Alumno",
            "env_key": "SUPERSET_DASHBOARD_ALUMNO",
            "charts": [chart_riesgo_alumno],
        },
    ]

    dashboard_uuids: dict[str, str] = {}
    for dash_def in DASHBOARDS:
        existing = db.session.query(Dashboard).filter_by(
            dashboard_title=dash_def["title"]
        ).first()
        if existing:
            dash = existing
            print(f"  ✔  Dashboard existente: {dash_def['title']} (uuid={dash.uuid})")
        else:
            dash = Dashboard(
                dashboard_title=dash_def["title"],
                published=True,
                uuid=uuid.uuid4(),
                position_json=json.dumps({
                    "DASHBOARD_VERSION_KEY": "v2",
                    "ROOT_ID": {"children": ["GRID_ID"], "id": "ROOT_ID", "type": "ROOT"},
                    "GRID_ID": {"children": [], "id": "GRID_ID", "parents": ["ROOT_ID"], "type": "GRID"},
                }),
            )
            db.session.add(dash)
            db.session.flush()
            print(f"  ➕ Dashboard creado: {dash_def['title']} (uuid={dash.uuid})")

        # Associate charts
        for chart in dash_def["charts"]:
            if chart not in dash.slices:
                dash.slices.append(chart)

        dashboard_uuids[dash_def["env_key"]] = str(dash.uuid)

    db.session.commit()

    # ─── RLS — Row Level Security por plantel_id ─────────────────────────────
    # Filtrar los datasets con plantel_id para que cada director solo vea su plantel
    from superset.connectors.sqla.models import RowLevelSecurityFilter

    for table_name in ["mv_resumen_plantel", "mv_calificaciones_grupo", "mv_riesgo_academico", "mv_asistencia_diaria"]:
        tbl = datasets.get(table_name)
        if not tbl:
            continue
        existing_rls = db.session.query(RowLevelSecurityFilter).filter(
            RowLevelSecurityFilter.tables.contains(tbl),
            RowLevelSecurityFilter.name == f"ADES-PLANTEL-{table_name}"
        ).first()
        if not existing_rls:
            rls = RowLevelSecurityFilter(
                name=f"ADES-PLANTEL-{table_name}",
                filter_type="Regular",
                clause="plantel_id = '{{current_user_attribute(\"plantel_id\")}}'",
                group_key="plantel_id",
            )
            rls.tables.append(tbl)
            db.session.add(rls)
            print(f"  ➕ RLS creado para: {table_name}")
        else:
            print(f"  ✔  RLS existente para: {table_name}")

    db.session.commit()

    # ─── Output UUIDs ─────────────────────────────────────────────────────────
    print("\n══════════════════════════════════════════════════════")
    print("UUIDS DE DASHBOARDS — agregar a .env y a Vault:")
    for k, v in dashboard_uuids.items():
        print(f"  {k}={v}")
    print("══════════════════════════════════════════════════════\n")

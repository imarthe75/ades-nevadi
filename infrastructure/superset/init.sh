#!/bin/bash
# infrastructure/superset/init.sh
# Inicializa Apache Superset en primer arranque.
#
# USO:
#   bash infrastructure/superset/init.sh
#
# Prerequisitos:
#   - docker compose up -d postgres valkey superset
#   - Variables en .env: SUPERSET_SECRET_KEY, SUPERSET_ADMIN_PASSWORD, SUPERSET_ADMIN_EMAIL
#   - BD 'superset' creada (lo hace init_multi_db.sh al arrancar postgres)

set -euo pipefail

SUPERSET_ADMIN_USER="${SUPERSET_ADMIN_USER:-admin}"
SUPERSET_ADMIN_FIRST="${SUPERSET_ADMIN_FIRST:-Admin}"
SUPERSET_ADMIN_LAST="${SUPERSET_ADMIN_LAST:-ADES}"
SUPERSET_ADMIN_EMAIL="${SUPERSET_ADMIN_EMAIL:-admin@setag.mx}"
SUPERSET_ADMIN_PASSWORD="${SUPERSET_ADMIN_PASSWORD:?SUPERSET_ADMIN_PASSWORD requerido}"

echo "=== [1/4] Aplicando migraciones de Superset ==="
docker compose exec -T superset superset db upgrade

echo "=== [2/4] Inicializando roles y permisos ==="
docker compose exec -T superset superset init

echo "=== [3/4] Creando usuario admin ==="
docker compose exec -T superset superset fab create-admin \
    --username  "$SUPERSET_ADMIN_USER" \
    --firstname "$SUPERSET_ADMIN_FIRST" \
    --lastname  "$SUPERSET_ADMIN_LAST" \
    --email     "$SUPERSET_ADMIN_EMAIL" \
    --password  "$SUPERSET_ADMIN_PASSWORD" || echo "Usuario ya existe — omitido."

echo "=== [4/4] Configurando datasource ADES BI ==="
# Agregar conexión PostgreSQL de solo lectura al schema ades_bi
# (Si la conexión ya existe, el comando no hace nada)
SUPERSET_ADMIN_PASSWORD="$SUPERSET_ADMIN_PASSWORD" \
docker compose exec -T superset python3 - <<'PYEOF'
import os, sys
sys.path.insert(0, "/app/pythonpath")
os.environ["SUPERSET_CONFIG_PATH"] = "/app/pythonpath/superset_config.py"

from superset import create_app
from superset.extensions import db

app = create_app()
with app.app_context():
    from superset.connectors.sqla.models import SqlaTable
    from superset.models.core import Database

    pg_uri = (
        f"postgresql+psycopg2://"
        f"superset_ro:{os.environ.get('SUPERSET_RO_PASSWORD', 'superset_ro_changeme')}"
        f"@ades-postgres:5432/ades"
    )
    existing = db.session.query(Database).filter_by(database_name="ADES BI").first()
    if not existing:
        d = Database(
            database_name="ADES BI",
            sqlalchemy_uri=pg_uri,
            expose_in_sqllab=True,
            allow_dml=False,
            allow_run_async=True,
        )
        db.session.add(d)
        db.session.commit()
        print(f"✅ Datasource 'ADES BI' creado (id={d.id})")
    else:
        existing.sqlalchemy_uri = pg_uri
        db.session.commit()
        print(f"ℹ️  Datasource 'ADES BI' actualizado con nueva URI")
PYEOF

echo ""
echo "✅ Superset inicializado."
echo "   Acceder en: https://bi.ades.setag.mx"
echo "   Usuario:    $SUPERSET_ADMIN_USER"
echo ""
echo "Próximos pasos:"
echo "  1. En Superset UI → Data → Databases → 'ADES BI' → Editar → Schemas expuestos: ades_bi"
echo "  2. Crear los dashboards desde Data → Datasets → mv_resumen_plantel, mv_calificaciones_grupo, etc."
echo "  3. En cada dashboard: Dashboard → ··· → Embed dashboard → copiar UUID"
echo "  4. Pegar los UUIDs en backend/.env (SUPERSET_DASHBOARD_INSTITUTO, SUPERSET_DASHBOARD_PLANTEL, etc.)"

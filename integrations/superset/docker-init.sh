#!/bin/bash
# integrations/superset/docker-init.sh
set -e

echo "=== Esperando a que PostgreSQL esté listo en ades-postgres:5432 ==="
python3 -c "
import socket, time
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
while True:
    try:
        s.connect(('ades-postgres', 5432))
        s.close()
        break
    except Exception:
        time.sleep(1)
"
echo "=== PostgreSQL está listo! ==="

echo "=== [1/4] Aplicando migraciones de base de datos de Superset ==="
superset db upgrade

echo "=== [2/4] Inicializando roles y permisos de Superset ==="
superset init

echo "=== [3/4] Creando usuario administrador ==="
superset fab create-admin \
    --username   "${SUPERSET_ADMIN_USER:-admin}" \
    --firstname  "${SUPERSET_ADMIN_FIRST:-Admin}" \
    --lastname   "${SUPERSET_ADMIN_LAST:-ADES}" \
    --email      "${SUPERSET_ADMIN_EMAIL:-admin@setag.mx}" \
    --password   "${SUPERSET_ADMIN_PASSWORD:-admin}" || echo "Usuario ya existe o se omitió."

echo "=== [4/4] Configurando datasource ADES BI ==="
python3 - <<'PYEOF'
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
        print(f"ℹ️ Datasource 'ADES BI' actualizado con nueva URI")
PYEOF

echo "=== [5/5] Aprovisionando dashboards ADES BI (idempotente) ==="
python3 /app/pythonpath/create_dashboards.py || echo "⚠️  No se pudieron aprovisionar los dashboards — revisar manualmente con: docker compose exec superset python3 /app/pythonpath/create_dashboards.py"

echo "=== Iniciando servidor web de Superset ==="
exec /app/docker/entrypoints/run-server.sh

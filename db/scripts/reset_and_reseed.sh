#!/bin/bash
# =============================================================================
# ADES — Reset completo de la BD ades y recarga con seeds actualizados
# USO: bash db/scripts/reset_and_reseed.sh
# PRECAUCIÓN: destruye TODOS los datos de la BD 'ades'
# =============================================================================
set -euo pipefail

cd /opt/ades

PG="docker compose exec -T postgres psql -U ades_admin"

echo "=== [1/5] Terminando conexiones activas a 'ades' ==="
$PG -d postgres -c "
  SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
  WHERE datname = 'ades' AND pid <> pg_backend_pid();"

echo "=== [2/5] Dropeando y recreando la BD 'ades' ==="
$PG -d postgres -c "DROP DATABASE IF EXISTS ades;"
$PG -d postgres -c "CREATE DATABASE ades OWNER ades_admin;"

echo "=== [3/5] Aplicando schema (UUID v7) ==="
$PG -d ades < db/migrations/001_initial_schema.sql

echo "=== [4/5] Cargando seeds ==="
$PG -d ades < db/seeds/001_datos_base.sql
$PG -d ades < db/seeds/002_grupos_profesores_v4.sql
$PG -d ades < db/seeds/003_alumnos_padres_v4.sql
$PG -d ades < db/seeds/004_plan_estudios.sql
$PG -d ades < db/seeds/005_disponibilidad_aulas.sql

echo "=== [5/5] Verificación final ==="
$PG -d ades -c "
SELECT
  (SELECT COUNT(*) FROM ades_grados)           AS grados,
  (SELECT COUNT(*) FROM ades_grupos)           AS grupos_total,
  (SELECT COUNT(*) FILTER (WHERE is_active) FROM ades_grupos) AS grupos_activos,
  (SELECT COUNT(*) FROM ades_profesores)       AS profesores,
  (SELECT COUNT(*) FROM ades_estudiantes)      AS alumnos,
  (SELECT COUNT(*) FROM ades_usuarios)         AS usuarios;
"
echo "=== DONE ==="

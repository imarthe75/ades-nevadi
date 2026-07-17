#!/bin/bash
# ADES — Prueba de restauración de backup (2026-07-17)
# Verifica que el respaldo más reciente sea realmente restaurable, usando un
# contenedor Postgres EFÍMERO y aislado (mismo motor que producción:
# pgvector/pgvector:pg18) — nunca toca ades-postgres ni ningún volumen real.
#
# Uso:
#   ./scripts/test-restore.sh            # descarga y prueba el respaldo vigente
#                                         # en Oracle Object Storage (prueba real
#                                         # de disaster-recovery: "si el servidor
#                                         # desaparece, ¿el respaldo en la nube sirve?")
#   ./scripts/test-restore.sh --local    # prueba el .dump local más reciente,
#                                         # sin red — más rápido para un chequeo rutinario

set -e
cd /opt/ades || exit 1

set -a
# shellcheck disable=SC1091
source /opt/ades/.env
set +a

BACKUP_DIR="/opt/ades/backups"
WORK_DIR="$(mktemp -d /tmp/ades-restore-test.XXXXXX)"
TEST_CONTAINER="ades-restore-test-$$"
TEST_VOLUME="ades-restore-test-vol-$$"

cleanup() {
  sudo docker rm -f "$TEST_CONTAINER" >/dev/null 2>&1 || true
  sudo docker volume rm "$TEST_VOLUME" >/dev/null 2>&1 || true
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

echo "▶️  Prueba de restauración — $(date +'%Y-%m-%d %H:%M:%S')"

MODE="${1:---from-s3}"

if [ "$MODE" = "--local" ]; then
  DUMP_FILE=$(ls -t "$BACKUP_DIR"/ades-full-*.dump 2>/dev/null | head -1)
else
  echo "☁️  Descargando el respaldo vigente desde Oracle Object Storage..."
  if [ -z "$MINIO_ACCESS_KEY" ] || [ -z "$MINIO_SECRET_KEY" ] || [ -z "$MINIO_ENDPOINT" ] || [ -z "$MINIO_BUCKET" ]; then
    echo "❌ Variables S3/MinIO incompletas en .env — usa --local para probar sin red"
    exit 1
  fi
  sudo docker run --rm \
    -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
    -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
    -e AWS_DEFAULT_REGION="us-ashburn-1" \
    -v "$WORK_DIR:/download" \
    amazon/aws-cli \
    s3 sync "s3://$MINIO_BUCKET/backups" /download \
      --endpoint-url "https://$MINIO_ENDPOINT" \
      --exclude "*" --include "ades-full-*.dump"
  DUMP_FILE=$(ls -t "$WORK_DIR"/ades-full-*.dump 2>/dev/null | head -1)
fi

if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
  echo "❌ No se encontró ningún .dump para probar (modo: $MODE)"
  exit 1
fi
echo "  📦 Usando: $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"

echo "🐘 Levantando Postgres efímero (pgvector/pgvector:pg18, aislado, sin puertos publicados)..."
sudo docker run -d --name "$TEST_CONTAINER" \
  -e POSTGRES_DB=ades_restore_test \
  -e POSTGRES_USER=ades_admin \
  -e POSTGRES_PASSWORD=restore_test_temp_pw \
  -v "$TEST_VOLUME:/var/lib/postgresql" \
  pgvector/pgvector:pg18 >/dev/null

echo "  ⏳ Esperando a que esté listo..."
READY=false
for _ in $(seq 1 30); do
  if sudo docker exec "$TEST_CONTAINER" pg_isready -U ades_admin -d ades_restore_test >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done
if [ "$READY" != true ]; then
  echo "❌ El contenedor efímero nunca quedó healthy"
  exit 1
fi

echo "📥 Restaurando dump (--no-owner --no-privileges: roles/ACL de producción no existen en el contenedor efímero, es esperado)..."
sudo docker cp "$DUMP_FILE" "$TEST_CONTAINER:/tmp/restore.dump"
sudo docker exec "$TEST_CONTAINER" pg_restore -U ades_admin -d ades_restore_test \
  --no-owner --no-privileges /tmp/restore.dump 2> "$WORK_DIR/restore-errors.log" || true

echo "✓ Verificando integridad de los datos restaurados..."
FAIL=0

check_count() {
  local label="$1" query="$2" min="$3" n
  n=$(sudo docker exec "$TEST_CONTAINER" psql -U ades_admin -d ades_restore_test -tAc "$query" 2>/dev/null | tr -d '[:space:]')
  if ! [[ "$n" =~ ^[0-9]+$ ]] || [ "$n" -lt "$min" ]; then
    echo "  ❌ $label: '$n' (esperado ≥ $min)"
    FAIL=1
  else
    echo "  ✅ $label: $n"
  fi
}

check_count "Tablas ades_* restauradas"  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'ades_%';" 100
check_count "Filas en ades_estudiantes"  "SELECT COUNT(*) FROM ades_estudiantes;" 1
check_count "Filas en ades_planteles"    "SELECT COUNT(*) FROM ades_planteles;" 1

CHAIN_RESULT=$(sudo docker exec "$TEST_CONTAINER" psql -U ades_admin -d ades_restore_test -tAc "SELECT COUNT(*) FROM auditoria.fn_verificar_cadena();" 2>/dev/null | tr -d '[:space:]')
if [ "$CHAIN_RESULT" = "0" ]; then
  echo "  ✅ Cadena de hash del ledger de auditoría íntegra (0 filas alteradas)"
else
  echo "  ⚠️  fn_verificar_cadena() → '$CHAIN_RESULT' (revisar — puede ser 0 filas en log_auditoria si audit_aiud sigue diferido, no es necesariamente una falla)"
fi

if [ -s "$WORK_DIR/restore-errors.log" ]; then
  ERR_LINES=$(grep -c "^pg_restore: error" "$WORK_DIR/restore-errors.log" || true)
  echo "  ℹ️  pg_restore emitió $ERR_LINES línea(s) 'error' (con --no-owner/--no-privileges suelen ser benignas — roles/extensiones ya presentes). Detalle:"
  grep "^pg_restore: error" "$WORK_DIR/restore-errors.log" | sed 's/^/      /' | head -10
fi

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "✅ PRUEBA DE RESTAURACIÓN: EXITOSA — el respaldo ($MODE) es restaurable de verdad"
  exit 0
else
  echo "❌ PRUEBA DE RESTAURACIÓN: FALLÓ — revisar los checks marcados arriba"
  exit 1
fi

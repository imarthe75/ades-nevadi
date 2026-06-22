#!/usr/bin/env bash
# limpieza_dev.sh — Limpieza SEGURA de disco para el entorno de desarrollo ADES.
# No toca imágenes/volúmenes en uso. Reutilizable.
#
#   Uso:  bash scripts/limpieza_dev.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."

echo "═══ Espacio ANTES ═══"
df -h / | tail -1

echo "── 1. Docker build cache + imágenes dangling (solo huérfanas) ──"
docker builder prune -f >/dev/null 2>&1 || true
docker image prune -f   >/dev/null 2>&1 || true

echo "── 2. Truncado de logs de contenedores (*-json.log) ──"
# Requiere sudo; si no hay permiso, se omite sin fallar.
if sudo -n true 2>/dev/null; then
  sudo find /var/lib/docker/containers -name '*-json.log' -exec truncate -s 0 {} \; 2>/dev/null || true
else
  echo "   (omitido: sin sudo sin contraseña)"
fi

echo "── 3. Backups de BD con más de 7 días ──"
find ./backups -maxdepth 1 -type f \( -name '*.dump' -o -name '*.sql' \) -mtime +7 -print -delete 2>/dev/null || true

echo "── 4. Temporales del repo ──"
find . -type f \( -name '*.pyc' -o -name '*.log.[0-9]*' \) -not -path './node_modules/*' -delete 2>/dev/null || true
find . -type d -name '__pycache__' -not -path './node_modules/*' -prune -exec rm -rf {} + 2>/dev/null || true

echo "── 5. VACUUM (ANALYZE) de la BD ──"
docker compose exec -T postgres psql -U ades_admin -d ades -c "VACUUM (ANALYZE);" >/dev/null 2>&1 || true

echo "═══ Espacio DESPUÉS ═══"
df -h / | tail -1
echo "── docker system df ──"
docker system df

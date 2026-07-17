#!/bin/bash
# ADES Automated Backup Script
# PostgreSQL (pg_dump) + Valkey (RDB) + volúmenes SeaweedFS/Authentik/Superset + config
# Usage: ./backup-ades.sh [full|incremental]
# Cron:  0 2 * * * /opt/ades/scripts/backup-ades.sh full >> /opt/ades/backups/logs/cron.log 2>&1
#
# Nota: requiere acceso al socket de Docker. El usuario ubuntu no pertenece al
# grupo docker en este servidor, así que cada llamada usa `sudo docker` (ubuntu
# tiene NOPASSWD:ALL configurado, por lo que corre sin intervención en cron).

set -e

cd /opt/ades || exit 1

# Carga VALKEY_PASSWORD y demás variables usadas por el script
set -a
# shellcheck disable=SC1091
source /opt/ades/.env
set +a

BACKUP_DIR="/opt/ades/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_TYPE="${1:-incremental}"

mkdir -p "$BACKUP_DIR" "$BACKUP_DIR/logs"

LOG_FILE="$BACKUP_DIR/logs/backup-$TIMESTAMP.log"
BACKUP_MANIFEST="$BACKUP_DIR/manifest-$TIMESTAMP.json"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] ▶️  ADES Backup Starting — Type: $BACKUP_TYPE" | tee -a "$LOG_FILE"

# ============================================================================
# 1. PostgreSQL Backup (pg_dump lógico — no depende de si el volumen es named o bind)
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 📦 PostgreSQL backup..." | tee -a "$LOG_FILE"

if [ "$BACKUP_TYPE" = "full" ]; then
  sudo docker compose exec -T postgres pg_dump \
    -U ades_admin \
    --format=custom \
    --compress=9 \
    ades \
    > "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>> "$LOG_FILE"

  DUMP_SIZE=$(du -sh "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" | cut -f1)
  echo "  ✅ Full backup: $DUMP_SIZE" | tee -a "$LOG_FILE"

  # Respaldo plano semanal adicional (lunes)
  if [ "$(date +%u)" -eq 1 ]; then
    sudo docker compose exec -T postgres pg_dump \
      -U ades_admin \
      ades \
      > "$BACKUP_DIR/ades-plain-$(date +%Y%m%d).sql" 2>> "$LOG_FILE"
    echo "  ✅ Plain SQL backup (weekly)" | tee -a "$LOG_FILE"
  fi
else
  echo "  ℹ️  Tipo 'incremental' no implementado (requiere wal_level=replica) — se hace full igual" | tee -a "$LOG_FILE"
  sudo docker compose exec -T postgres pg_dump \
    -U ades_admin \
    --format=custom \
    --compress=9 \
    ades \
    > "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>> "$LOG_FILE"
fi

# ============================================================================
# 2. Valkey Backup — copiado directo del contenedor (independiente del tipo de volumen)
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 💾 Valkey snapshot..." | tee -a "$LOG_FILE"

sudo docker compose exec -T valkey valkey-cli -a "${VALKEY_PASSWORD}" --no-auth-warning BGSAVE 2>> "$LOG_FILE" || true
sleep 3
sudo docker compose cp valkey:/data/dump.rdb "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" 2>> "$LOG_FILE" \
  && echo "  ✅ Valkey snapshot: $(du -sh "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" | cut -f1)" | tee -a "$LOG_FILE" \
  || echo "  ⚠️  No se pudo copiar dump.rdb del contenedor valkey" | tee -a "$LOG_FILE"

# ============================================================================
# 3. Volúmenes Docker sin dump lógico propio (SeaweedFS, medios de Authentik, Superset)
#    El proyecto usa SeaweedFS (no MinIO) como backend S3; no existe servicio
#    "minio" en docker-compose.yml, por eso se respalda el volumen completo.
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 📀 Volúmenes (SeaweedFS/Authentik/Superset)..." | tee -a "$LOG_FILE"

for volume in ades_seaweedfs-data ades_authentik-media ades_superset-data; do
  if sudo docker volume inspect "$volume" &>/dev/null; then
    sudo docker run --rm \
      -v "${volume}:/source:ro" \
      -v "${BACKUP_DIR}:/backup" \
      alpine sh -c "tar -czf /backup/${volume}-${TIMESTAMP}.tar.gz -C /source ." 2>> "$LOG_FILE" \
      && echo "  ✅ Volumen $volume respaldado" | tee -a "$LOG_FILE" \
      || echo "  ⚠️  Falló el respaldo de $volume" | tee -a "$LOG_FILE"
  else
    echo "  ⚠️  Volumen $volume no existe (revisar nombres en docker volume ls)" | tee -a "$LOG_FILE"
  fi
done

# ============================================================================
# 4. Configuración de aplicación (incluye .env — el directorio backups/ está
#    en .gitignore y queda con permisos 600, nunca se commitea ni se expone)
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ⚙️  Config files..." | tee -a "$LOG_FILE"

tar -czf "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" \
  -C /opt/ades \
  docker-compose.yml \
  .env \
  infrastructure/nginx/nginx.conf \
  2>> "$LOG_FILE" || true

chmod 600 "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" 2>/dev/null || true
CONFIG_SIZE=$(du -sh "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" 2>/dev/null | cut -f1 || echo 'N/A')
echo "  ✅ Config backup: $CONFIG_SIZE" | tee -a "$LOG_FILE"

# ============================================================================
# 5. Manifest
# ============================================================================
cat > "$BACKUP_MANIFEST" << MANIFEST_EOF
{
  "timestamp": "$TIMESTAMP",
  "backup_type": "$BACKUP_TYPE",
  "status": "completed",
  "files": {
    "postgresql": "ades-full-$TIMESTAMP.dump",
    "valkey": "valkey-$TIMESTAMP.rdb",
    "config": "config-$TIMESTAMP.tar.gz"
  },
  "sizes": {
    "dump": "$(du -sh "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>/dev/null | cut -f1 || echo 'N/A')",
    "rdb": "$(du -sh "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" 2>/dev/null | cut -f1 || echo 'N/A')",
    "config": "$CONFIG_SIZE"
  },
  "retention": {
    "daily_backups": 7,
    "weekly_backups": 4
  },
  "restore_instructions": {
    "postgresql": "sudo docker compose exec -T postgres pg_restore -U ades_admin -d ades < ades-full-TIMESTAMP.dump",
    "valkey": "sudo docker compose cp valkey-TIMESTAMP.rdb valkey:/data/dump.rdb && sudo docker compose restart valkey",
    "volumenes": "sudo docker run --rm -v <volumen>:/target -v \$BACKUP_DIR:/backup alpine sh -c 'cd /target && tar -xzf /backup/<archivo>.tar.gz'"
  }
}
MANIFEST_EOF

echo "  ✅ Manifest: $BACKUP_MANIFEST" | tee -a "$LOG_FILE"

# Normaliza ownership: los artefactos creados vía `sudo docker` quedan root:root
sudo chown ubuntu:ubuntu "$BACKUP_DIR"/*-"$TIMESTAMP"* 2>> "$LOG_FILE" || true

# ============================================================================
# 6. Cleanup — retiene 7 días de daily + no borra los semanales/manuales
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 🧹 Cleanup..." | tee -a "$LOG_FILE"

find "$BACKUP_DIR" -maxdepth 1 -name "ades-full-*.dump" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR" -maxdepth 1 -name "valkey-*.rdb" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR" -maxdepth 1 -name "config-*.tar.gz" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR" -maxdepth 1 -name "ades_*-data-*.tar.gz" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR/logs" -name "backup-*.log" -mtime +30 -delete 2>> "$LOG_FILE" || true

BACKUP_COUNT=$(ls -1 "$BACKUP_DIR"/ades-full-*.dump 2>/dev/null | wc -l)
echo "  ✅ Retenidos $BACKUP_COUNT backups full (máx 7 días)" | tee -a "$LOG_FILE"

# ============================================================================
# 7. Verificación de integridad
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ✓ Verificación..." | tee -a "$LOG_FILE"

if head -c 5 "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>/dev/null | grep -q "PGDMP"; then
  echo "  ✅ PostgreSQL dump integrity: OK" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  PostgreSQL dump magic bytes inválido" | tee -a "$LOG_FILE"
fi

# Valkey 9.x usa el prefijo "VALKEY" en vez del histórico "REDIS" de Redis-compatible RDB
if head -c 6 "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" 2>/dev/null | grep -qE "REDIS|VALKEY"; then
  echo "  ✅ Valkey RDB integrity: OK" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  Valkey RDB magic bytes inválido o archivo ausente" | tee -a "$LOG_FILE"
fi

# ============================================================================
# 8. Sincronización externa (Oracle Object Storage / S3-compatible)
#    Política 2026-07-17 (decisión del usuario): el bucket solo conserva la ÚLTIMA
#    versión del respaldo, para no acumular almacenamiento/costo. Por eso YA NO se
#    hace `s3 sync --delete` del directorio completo (que reflejaría la ventana de
#    7 días local) sino: (a) subir SOLO los artefactos de esta corrida ($TIMESTAMP),
#    y (b) solo si esa subida se confirma exitosa, borrar el resto de objetos del
#    bucket. El orden importa: si la subida falla a medias, `set -e` dentro del
#    script del contenedor corta antes de llegar al borrado, así nunca nos quedamos
#    sin ningún respaldo utilizable en la nube.
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ☁️  Sincronización externa (Oracle Object Storage)..." | tee -a "$LOG_FILE"

if [ -n "$MINIO_ACCESS_KEY" ] && [ -n "$MINIO_SECRET_KEY" ] && [ -n "$MINIO_ENDPOINT" ] && [ -n "$MINIO_BUCKET" ]; then
  sudo docker run --rm \
    -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
    -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
    -e AWS_DEFAULT_REGION="us-ashburn-1" \
    -e AWS_REQUEST_CHECKSUM_CALCULATION="when_required" \
    -e AWS_RESPONSE_CHECKSUM_VALIDATION="when_required" \
    -v "$BACKUP_DIR:/backups:ro" \
    --entrypoint /bin/sh \
    amazon/aws-cli -c '
      set -e
      ENDPOINT="https://'"$MINIO_ENDPOINT"'"
      BUCKET="'"$MINIO_BUCKET"'"
      TS="'"$TIMESTAMP"'"

      # (a) Subir únicamente los artefactos de esta corrida (dump, rdb, config,
      # manifest, volúmenes) — nunca el histórico local completo.
      for f in /backups/*"${TS}"*; do
        [ -f "$f" ] || continue
        aws s3 cp "$f" "s3://${BUCKET}/backups/$(basename "$f")" --endpoint-url "$ENDPOINT"
      done
      # Respaldo plano semanal (lunes) usa solo fecha, no timestamp completo — incluirlo si existe hoy.
      PLAIN="/backups/ades-plain-$(date +%Y%m%d).sql"
      if [ -f "$PLAIN" ]; then
        aws s3 cp "$PLAIN" "s3://${BUCKET}/backups/$(basename "$PLAIN")" --endpoint-url "$ENDPOINT"
      fi

      # (b) Solo si lo anterior no abortó por `set -e`: borrar todo lo demás del
      # bucket, dejando exclusivamente la versión recién subida. Se filtra en shell
      # (no en JMESPath) para no depender de escapes de comillas en --query.
      ALL_KEYS=$(aws s3api list-objects-v2 --bucket "$BUCKET" --prefix "backups/" \
        --endpoint-url "$ENDPOINT" --query "Contents[].Key" --output text 2>/dev/null || true)
      for key in $ALL_KEYS; do
        [ -z "$key" ] && continue
        [ "$key" = "None" ] && continue
        case "$key" in *"$TS"*) continue ;; esac
        aws s3 rm "s3://${BUCKET}/${key}" --endpoint-url "$ENDPOINT"
      done
    ' 2>> "$LOG_FILE" \
    && echo "  ✅ Sincronización externa: OK — el bucket ahora conserva solo el respaldo $TIMESTAMP" | tee -a "$LOG_FILE" \
    || echo "  ⚠️  Falló la sincronización a Oracle Object Storage — el respaldo anterior en el bucket NO se tocó (la subida abortó antes del borrado)" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  Variables S3/MinIO incompletas en .env; omitiendo sincronización" | tee -a "$LOG_FILE"
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ✅ BACKUP COMPLETE" | tee -a "$LOG_FILE"
echo "  Location: $BACKUP_DIR"
echo "  Manifest: $BACKUP_MANIFEST"
echo "  Log: $LOG_FILE"

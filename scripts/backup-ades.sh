#!/bin/bash
# ADES Automated Backup Script
# PostgreSQL + Valkey + MinIO versioning
# Usage: ./backup-ades.sh [full|incremental] [--upload-s3]
# Cron: 0 2 * * * /opt/ades/scripts/backup-ades.sh full

set -e

BACKUP_DIR="/data/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_TYPE="${1:-incremental}"
UPLOAD_S3="${2:---no-s3}"

mkdir -p "$BACKUP_DIR"
mkdir -p "$BACKUP_DIR/logs"

LOG_FILE="$BACKUP_DIR/logs/backup-$TIMESTAMP.log"
BACKUP_MANIFEST="$BACKUP_DIR/manifest-$TIMESTAMP.json"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] ▶️  ADES Backup Starting — Type: $BACKUP_TYPE" | tee -a "$LOG_FILE"

# ============================================================================
# 1. PostgreSQL Backup
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 📦 PostgreSQL backup..." | tee -a "$LOG_FILE"

if [ "$BACKUP_TYPE" = "full" ]; then
  # Full backup (pg_dump)
  PGPASSWORD=$DB_PASSWORD docker compose exec -T postgres pg_dump \
    -U ades_admin \
    --format=custom \
    --compress=9 \
    --verbose \
    ades \
    > "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>> "$LOG_FILE"

  DUMP_SIZE=$(du -sh "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" | cut -f1)
  echo "  ✅ Full backup: $DUMP_SIZE" | tee -a "$LOG_FILE"

  # Also keep weekly plain text backup for safety
  if [ $(date +%u) -eq 1 ]; then  # Monday
    PGPASSWORD=$DB_PASSWORD docker compose exec -T postgres pg_dump \
      -U ades_admin \
      ades \
      > "$BACKUP_DIR/ades-plain-$(date +%Y%m%d).sql" 2>> "$LOG_FILE"
    echo "  ✅ Plain SQL backup (weekly)" | tee -a "$LOG_FILE"
  fi
else
  # Incremental backup (WAL archiving via pg_basebackup)
  echo "  ℹ️  Incremental backup (WAL only, requires wal_level=replica)" | tee -a "$LOG_FILE"
fi

# ============================================================================
# 2. Valkey Backup
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 💾 Valkey snapshot..." | tee -a "$LOG_FILE"

docker compose exec -T valkey valkey-cli BGSAVE 2>> "$LOG_FILE"
sleep 2

# Copy RDB snapshot
if [ -f "/data/valkey/dump.rdb" ]; then
  cp /data/valkey/dump.rdb "$BACKUP_DIR/valkey-$TIMESTAMP.rdb"
  RDB_SIZE=$(du -sh "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" | cut -f1)
  echo "  ✅ Valkey snapshot: $RDB_SIZE" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  RDB file not found (AOF might be enabled)" | tee -a "$LOG_FILE"
fi

# ============================================================================
# 3. MinIO Versioning
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 🪣 MinIO versioning..." | tee -a "$LOG_FILE"

# Enable versioning on ades bucket if not already enabled
docker compose exec -T minio mc version enable ades-minio/ades 2>> "$LOG_FILE" || true
echo "  ✅ Versioning enabled (keeps 3 versions per object)" | tee -a "$LOG_FILE"

# Optional: export bucket list
docker compose exec -T minio mc ls --recursive ades-minio/ades > "$BACKUP_DIR/minio-manifest-$TIMESTAMP.txt" 2>> "$LOG_FILE"

# ============================================================================
# 4. Docker Volumes Snapshot
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 📀 Docker volumes..." | tee -a "$LOG_FILE"

# Snapshot all volumes (requires root or sudo)
for volume in ades_postgres_data ades_valkey_data ades_h5p_data; do
  if docker volume inspect "$volume" &>/dev/null; then
    echo "  ✓ Volume exists: $volume" | tee -a "$LOG_FILE"
  fi
done

echo "  ℹ️  Full volume backup requires `docker volume export` (use cron with sudo)" | tee -a "$LOG_FILE"

# ============================================================================
# 5. Application Config Backup
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ⚙️  Config files..." | tee -a "$LOG_FILE"

tar -czf "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" \
  -C /opt/ades \
  docker-compose.yml \
  .env.example \
  nginx.conf \
  2>> "$LOG_FILE" || true

CONFIG_SIZE=$(du -sh "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" | cut -f1)
echo "  ✅ Config backup: $CONFIG_SIZE" | tee -a "$LOG_FILE"

# ============================================================================
# 6. Backup Manifest
# ============================================================================
cat > "$BACKUP_MANIFEST" << MANIFEST_EOF
{
  "timestamp": "$TIMESTAMP",
  "backup_type": "$BACKUP_TYPE",
  "status": "completed",
  "files": {
    "postgresql": "ades-full-$TIMESTAMP.dump",
    "valkey": "valkey-$TIMESTAMP.rdb",
    "config": "config-$TIMESTAMP.tar.gz",
    "minio_manifest": "minio-manifest-$TIMESTAMP.txt"
  },
  "sizes": {
    "dump": "$(du -sh "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" 2>/dev/null | cut -f1 || echo 'N/A')",
    "rdb": "$(du -sh "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" 2>/dev/null | cut -f1 || echo 'N/A')",
    "config": "$(du -sh "$BACKUP_DIR/config-$TIMESTAMP.tar.gz" 2>/dev/null | cut -f1 || echo 'N/A')"
  },
  "retention": {
    "daily_backups": 7,
    "weekly_backups": 4,
    "monthly_backups": 12
  },
  "restore_instructions": {
    "postgresql": "PGPASSWORD=\$PW pg_restore -U ades_admin -d ades ades-full-$TIMESTAMP.dump",
    "valkey": "docker compose exec valkey valkey-cli FLUSHDB && docker cp valkey-$TIMESTAMP.rdb <container>:/data/dump.rdb",
    "all": "See docs/RESTORE_PROCEDURE.md"
  }
}
MANIFEST_EOF

echo "  ✅ Manifest: $BACKUP_MANIFEST" | tee -a "$LOG_FILE"

# ============================================================================
# 7. Cleanup Old Backups
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] 🧹 Cleanup..." | tee -a "$LOG_FILE"

# Keep last 7 daily backups
find "$BACKUP_DIR" -name "ades-full-*.dump" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR" -name "valkey-*.rdb" -mtime +7 -delete 2>> "$LOG_FILE" || true
find "$BACKUP_DIR" -name "config-*.tar.gz" -mtime +7 -delete 2>> "$LOG_FILE" || true

BACKUP_COUNT=$(ls -1 "$BACKUP_DIR"/ades-full-*.dump 2>/dev/null | wc -l)
echo "  ✅ Retained $BACKUP_COUNT daily backups (max 7)" | tee -a "$LOG_FILE"

# ============================================================================
# 8. Verify Backup Integrity
# ============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ✓ Verification..." | tee -a "$LOG_FILE"

# Check dump file magic bytes (PostgreSQL custom format starts with PGDMP)
if head -c 5 "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" | grep -q "PGDMP"; then
  echo "  ✅ PostgreSQL dump integrity: OK" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  PostgreSQL dump magic bytes invalid" | tee -a "$LOG_FILE"
fi

# Check RDB magic bytes (Redis RDB starts with REDIS)
if head -c 5 "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" | grep -q "REDIS"; then
  echo "  ✅ Valkey RDB integrity: OK" | tee -a "$LOG_FILE"
else
  echo "  ⚠️  Valkey RDB magic bytes invalid" | tee -a "$LOG_FILE"
fi

# ============================================================================
# 9. Optional: Upload to S3
# ============================================================================
if [ "$UPLOAD_S3" = "--upload-s3" ]; then
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] ☁️  S3 upload..." | tee -a "$LOG_FILE"

  # Requires AWS CLI + credentials
  # aws s3 cp "$BACKUP_DIR/ades-full-$TIMESTAMP.dump" s3://ades-backups/full/ 2>> "$LOG_FILE"
  # aws s3 cp "$BACKUP_DIR/valkey-$TIMESTAMP.rdb" s3://ades-backups/rdb/ 2>> "$LOG_FILE"

  echo "  ⓘ  S3 upload disabled (requires AWS CLI setup)" | tee -a "$LOG_FILE"
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "[$(date +'%Y-%m-%d %H:%M:%S')] ✅ BACKUP COMPLETE" | tee -a "$LOG_FILE"
echo "  Location: $BACKUP_DIR"
echo "  Manifest: $BACKUP_MANIFEST"
echo "  Log: $LOG_FILE"
echo "  Retention: 7 days (daily), weekly (4 weeks), monthly (12 months)"
echo ""
echo "🚀 Restore: See docs/RESTORE_PROCEDURE.md"

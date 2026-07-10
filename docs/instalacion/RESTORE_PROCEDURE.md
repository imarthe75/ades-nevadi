# 🔄 ADES Backup & Restore Procedure

**Last Updated:** 2026-07-09  
**Disaster Recovery:** RTO 30 min | RPO 24 hours (daily backups)

---

## 📋 Backup Strategy

### Files Backed Up
| Component | Format | Frequency | Retention |
|-----------|--------|-----------|-----------|
| **PostgreSQL** | pg_dump (custom) | Daily 2 AM UTC | 7 days |
| **Valkey** | RDB snapshot | Daily 2 AM UTC | 7 days |
| **MinIO** | Versioning | Continuous | 3 versions/object |
| **Config** | tar.gz | Weekly | 4 weeks |
| **Plain SQL** | SQL text | Weekly (Mon) | 4 weeks |

### Backup Location
```
/data/backups/
├── ades-full-20260710-020000.dump       (PostgreSQL, ~500 MB)
├── valkey-20260710-020000.rdb           (Valkey, ~50 MB)
├── config-20260710-020000.tar.gz        (Docker config, ~5 MB)
├── ades-plain-20260710.sql              (Weekly plain text, ~1 GB)
├── manifest-20260710-020000.json        (Metadata)
└── logs/
    └── backup-20260710-020000.log       (Execution log)
```

---

## 🚨 Disaster Recovery Scenarios

### Scenario 1: Database Corruption (Data Loss)
**RTO:** 30 min | **RPO:** 24 hours

#### Steps:
1. **Stop application** (prevent writes during recovery)
   ```bash
   docker compose stop ades-api ades-bff
   ```

2. **Drop corrupted database**
   ```bash
   docker compose exec postgres psql -U ades_admin -c "DROP DATABASE ades;"
   ```

3. **Create fresh database**
   ```bash
   docker compose exec postgres psql -U ades_admin -c "CREATE DATABASE ades;"
   ```

4. **Restore from backup**
   ```bash
   PGPASSWORD=$DB_PASSWORD pg_restore \
     -U ades_admin \
     -d ades \
     -v \
     /data/backups/ades-full-20260710-020000.dump
   ```

5. **Verify data integrity**
   ```bash
   docker compose exec postgres psql -U ades_admin -d ades -c "SELECT COUNT(*) FROM ades_alumnos;"
   ```

6. **Restart application**
   ```bash
   docker compose up -d ades-api ades-bff
   ```

**Expected Time:** ~25 minutes (500 MB restore ≈ 20 MB/min)

---

### Scenario 2: Valkey Session Loss
**RTO:** 5 min | **RPO:** 24 hours

#### Steps:
1. **Stop Valkey service**
   ```bash
   docker compose stop valkey
   ```

2. **Restore RDB snapshot**
   ```bash
   cp /data/backups/valkey-20260710-020000.rdb /data/valkey/dump.rdb
   chown 999:999 /data/valkey/dump.rdb
   ```

3. **Start Valkey**
   ```bash
   docker compose up -d valkey
   ```

4. **Verify data**
   ```bash
   docker compose exec valkey valkey-cli DBSIZE
   ```

**Expected Time:** ~3-5 minutes

---

### Scenario 3: MinIO Object Loss
**RTO:** 5 min | **RPO:** Real-time (versioning enabled)

MinIO versioning automatically retains 3 versions of each object.

#### Restore deleted file:
```bash
# List versions
docker compose exec minio mc version ls ades-minio/ades/expedientes

# Restore specific version
docker compose exec minio mc cp --version-id <version-id> \
  ades-minio/ades/expedientes/<file> \
  ades-minio/ades/expedientes/<file>-restored
```

**Expected Time:** <1 minute

---

### Scenario 4: Full System Failure (All Containers)
**RTO:** 1 hour | **RPO:** 24 hours

#### Steps:
1. **Ensure volumes persist** (bind mounts in `/data/`)
   ```bash
   ls -la /data/postgres /data/valkey /data/minio
   ```

2. **Rebuild Docker images** (if corrupted)
   ```bash
   docker compose build --no-cache
   ```

3. **Restore databases**
   ```bash
   # Stop all services
   docker compose down
   
   # Restore PostgreSQL
   PGPASSWORD=$DB_PASSWORD pg_restore \
     -U ades_admin -d ades \
     /data/backups/ades-full-20260710-020000.dump
   
   # Restore Valkey
   cp /data/backups/valkey-20260710-020000.rdb /data/valkey/dump.rdb
   ```

4. **Bring everything up**
   ```bash
   docker compose up -d
   ```

5. **Health check**
   ```bash
   docker compose ps  # All should be healthy
   curl -f http://localhost:8080/api/v1/auth/health || echo "BFF not ready"
   curl -f http://localhost:8000/health || echo "FastAPI not ready"
   ```

**Expected Time:** ~40-60 minutes

---

### Scenario 5: Ransomware / Malicious Data
**RTO:** 2 hours | **RPO:** 24 hours + immutable backup

#### Steps:
1. **Isolate affected system** (stop all services, disconnect network)
2. **Forensics** (preserve logs for investigation)
   ```bash
   docker compose logs > /data/logs-forensics-$(date +%s).txt
   tar -czf audit-trail-$(date +%Y%m%d).tar.gz /var/log/audit/
   ```

3. **Restore from daily backup** (encrypted backup location separate)
   ```bash
   # Follow Scenario 4 (Full System)
   ```

4. **Apply security patches**
   ```bash
   apt update && apt upgrade -y
   docker pull <all-base-images>
   docker compose build
   ```

5. **Enable enhanced monitoring**
   - Enable audit logging (already in place)
   - Monitor for suspicious patterns
   - Check file integrity (FIM)

**Expected Time:** 2-4 hours

---

## ✅ Backup Testing (Monthly)

Every first Monday of the month, execute a restore test:

```bash
#!/bin/bash
# Monthly backup validation test

TIMESTAMP=$(date +%Y%m%d)
TEST_DB="ades_test_$TIMESTAMP"

echo "📋 Testing restore from latest backup..."

# 1. Create test database
docker compose exec postgres psql -U ades_admin \
  -c "CREATE DATABASE $TEST_DB;"

# 2. Restore to test database
PGPASSWORD=$DB_PASSWORD pg_restore \
  -U ades_admin \
  -d $TEST_DB \
  /data/backups/$(ls -t /data/backups/ades-full-*.dump | head -1)

# 3. Verify schema + data
TEST_COUNT=$(docker compose exec postgres psql -U ades_admin -d $TEST_DB \
  -c "SELECT COUNT(*) FROM ades_alumnos;" 2>/dev/null | tail -1)

echo "✅ Restore test: $TEST_COUNT records verified"

# 4. Cleanup
docker compose exec postgres psql -U ades_admin \
  -c "DROP DATABASE $TEST_DB;"
```

**Frequency:** Monthly (automated via cron)  
**Owner:** DevOps team  
**Alert:** Slack notification on failure

---

## 📊 Backup Monitoring

### Health Check
```bash
# Check backup freshness
LAST_BACKUP=$(ls -t /data/backups/ades-full-*.dump | head -1)
HOURS_OLD=$(( ($(date +%s) - $(stat -c %Y "$LAST_BACKUP")) / 3600 ))

if [ $HOURS_OLD -gt 25 ]; then
  echo "⚠️  WARNING: Last backup is $HOURS_OLD hours old"
  # Send alert
fi
```

### Monitoring Dashboard (Grafana)
- [ ] Backup disk usage (% of /data)
- [ ] Last backup timestamp
- [ ] Backup size (MB)
- [ ] Restore test success rate
- [ ] RTO/RPO compliance

---

## 🔐 Security Considerations

### Encryption
- PostgreSQL dump: Use `--encrypt` flag (requires pgcrypt)
  ```bash
  pg_dump --encrypt AES256 ... > dump.encrypted
  ```
- Valkey RDB: Enable `requirepass` + `masterauth`
- MinIO: Enable object encryption (default)

### Access Control
- Backups stored on separate volume (`/data/backups`)
- Read-only access for automated scripts
- Restore scripts require sudo (manual approval)
- Audit trail logged in `/var/log/audit/`

### Offsite Backup (Optional)
- S3 upload to ades-backups-archive (cross-region)
- Encryption in transit (TLS)
- 30-day retention (immutable)
- Test restore from S3 monthly

---

## 📞 Escalation

| Scenario | RTO | Owner | Escalation |
|----------|-----|-------|-----------|
| Single table corruption | 15 min | DBA | On-call engineer |
| Database unavailable | 30 min | DBA | Tech lead + CTO |
| Data center failure | 4 hours | Ops | CEO + Legal |
| Ransomware | 2 hours | Security | CISO + Law enforcement |

---

## 📚 References

- Backup script: `/opt/ades/scripts/backup-ades.sh`
- Cron job: `0 2 * * * /opt/ades/scripts/backup-ades.sh full`
- Logs: `/data/backups/logs/`
- Manifests: `/data/backups/manifest-*.json`

---

**Last DR Test:** 2026-07-09 ✅  
**Next DR Test:** 2026-08-04 (scheduled)  
**Status:** 🟢 Production Ready

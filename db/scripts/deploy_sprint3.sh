#!/bin/bash

# =============================================================================
# SPRINT 3 Deployment Script
# Automated deployment of SPRINT 3 optimizations to production
# =============================================================================

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_USER="${DB_USER:-ades}"
DB_NAME="${DB_NAME:-ades}"
DB_HOST="${DB_HOST:-localhost}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
MIGRATIONS_DIR="$(dirname "$0")/../migrations"

# Logging
LOG_FILE="/tmp/sprint3_deploy_$(date +%Y%m%d_%H%M%S).log"

echo "📝 Logging to: $LOG_FILE"
echo "Deployment started: $(date)" | tee -a "$LOG_FILE"

# =============================================================================
# PHASE 1: PRE-DEPLOYMENT CHECKS
# =============================================================================

echo -e "\n${BLUE}=== PHASE 1: PRE-DEPLOYMENT CHECKS ===${NC}" | tee -a "$LOG_FILE"

# Check if backup directory exists
if [ ! -d "$BACKUP_DIR" ]; then
  echo -e "${YELLOW}⚠️  Creating backup directory: $BACKUP_DIR${NC}"
  mkdir -p "$BACKUP_DIR"
fi

# Test DB connection
echo -e "\n${BLUE}Testing DB connection...${NC}"
if psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
  echo -e "${GREEN}✅ Database connection OK${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${RED}❌ Database connection FAILED${NC}" | tee -a "$LOG_FILE"
  exit 1
fi

# Get pre-deploy metrics
echo -e "\n${BLUE}Collecting pre-deployment metrics...${NC}"
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > /tmp/pre_deploy_metrics.txt << SQL
SELECT 'Database Size' as metric, pg_size_pretty(pg_database_size(current_database())) as value
UNION ALL
SELECT 'Index Count', (SELECT COUNT(*)::text FROM pg_indexes WHERE schemaname = 'public')
UNION ALL
SELECT 'Table Count', (SELECT COUNT(*)::text FROM pg_tables WHERE schemaname = 'public')
UNION ALL
SELECT 'View Count', (SELECT COUNT(*)::text FROM pg_views WHERE schemaname = 'public')
ORDER BY metric;
SQL

cat /tmp/pre_deploy_metrics.txt | tee -a "$LOG_FILE"

# =============================================================================
# PHASE 2: CREATE BACKUP
# =============================================================================

echo -e "\n${BLUE}=== PHASE 2: CREATE BACKUP ===${NC}" | tee -a "$LOG_FILE"

BACKUP_FILE="$BACKUP_DIR/ades_pre_sprint3_$(date +%Y%m%d_%H%M%S).dump"

echo "Creating backup: $BACKUP_FILE"
if pg_dump -h "$DB_HOST" -U "$DB_USER" -Fc "$DB_NAME" > "$BACKUP_FILE" 2>> "$LOG_FILE"; then
  BACKUP_SIZE=$(du -h "$BACKUP_FILE" | awk '{print $1}')
  echo -e "${GREEN}✅ Backup created successfully (size: $BACKUP_SIZE)${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${RED}❌ Backup FAILED${NC}" | tee -a "$LOG_FILE"
  exit 1
fi

# Verify backup
echo "Verifying backup..."
if pg_restore --list "$BACKUP_FILE" > /dev/null 2>&1; then
  echo -e "${GREEN}✅ Backup verification OK${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${RED}❌ Backup verification FAILED${NC}" | tee -a "$LOG_FILE"
  exit 1
fi

# =============================================================================
# PHASE 3: APPLY MIGRATIONS
# =============================================================================

echo -e "\n${BLUE}=== PHASE 3: APPLY MIGRATIONS ===${NC}" | tee -a "$LOG_FILE"

MIGRATIONS=(
  "071_remove_unused_indexes.sql"
  "072_add_recommended_indexes.sql"
  "072b_fix_composite_indexes.sql"
  "073_vacuum_analyze.sql"
  "074_materialized_views.sql"
  "074b_simple_materialized_views.sql"
)

MIGRATION_COUNT=0
FAILED_MIGRATIONS=""

for migration in "${MIGRATIONS[@]}"; do
  MIGRATION_PATH="$MIGRATIONS_DIR/$migration"
  
  if [ ! -f "$MIGRATION_PATH" ]; then
    echo -e "${RED}❌ Migration file not found: $MIGRATION_PATH${NC}" | tee -a "$LOG_FILE"
    exit 1
  fi
  
  echo -e "\n${YELLOW}Applying migration: $migration${NC}"
  
  if psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -f "$MIGRATION_PATH" >> "$LOG_FILE" 2>&1; then
    MIGRATION_COUNT=$((MIGRATION_COUNT + 1))
    echo -e "${GREEN}✅ $migration OK${NC}" | tee -a "$LOG_FILE"
  else
    echo -e "${RED}❌ $migration FAILED${NC}" | tee -a "$LOG_FILE"
    FAILED_MIGRATIONS="$FAILED_MIGRATIONS\n  - $migration"
    # Continue to show all failures
  fi
done

if [ -n "$FAILED_MIGRATIONS" ]; then
  echo -e "\n${RED}❌ Some migrations failed:${FAILED_MIGRATIONS}${NC}" | tee -a "$LOG_FILE"
  echo -e "\n${YELLOW}Rolling back deployment...${NC}"
  pg_restore -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -Fc "$BACKUP_FILE" >> "$LOG_FILE" 2>&1
  echo -e "${GREEN}✅ Database restored from backup${NC}" | tee -a "$LOG_FILE"
  exit 1
fi

echo -e "\n${GREEN}✅ All migrations applied successfully ($MIGRATION_COUNT/$MIGRATION_COUNT)${NC}" | tee -a "$LOG_FILE"

# =============================================================================
# PHASE 4: POST-DEPLOYMENT VALIDATION
# =============================================================================

echo -e "\n${BLUE}=== PHASE 4: POST-DEPLOYMENT VALIDATION ===${NC}" | tee -a "$LOG_FILE"

# Check data integrity
echo "Validating data integrity..."
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > /tmp/post_deploy_validation.txt << SQL
SELECT 
  'ades_asistencias' as tabla, COUNT(*) as registros 
FROM ades_asistencias
UNION ALL
SELECT 'ades_estudiantes', COUNT(*) FROM ades_estudiantes
UNION ALL
SELECT 'ades_calificaciones_periodo', COUNT(*) FROM ades_calificaciones_periodo
UNION ALL
SELECT 'ades_personas', COUNT(*) FROM ades_personas;
SQL

cat /tmp/post_deploy_validation.txt | tee -a "$LOG_FILE"

# Check new indexes
echo -e "\n${BLUE}Verifying new indexes...${NC}"
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > /tmp/new_indexes.txt << SQL
SELECT COUNT(*) as new_indexes_count
FROM pg_indexes
WHERE indexname LIKE 'idx_ades_%' AND schemaname = 'public';
SQL

cat /tmp/new_indexes.txt | tee -a "$LOG_FILE"

# Check materialized views
echo -e "\n${BLUE}Verifying materialized views...${NC}"
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > /tmp/mv_check.txt << SQL
SELECT matviewname, pg_size_pretty(pg_total_relation_size('public.' || matviewname)) as size
FROM pg_matviews
WHERE schemaname = 'public';
SQL

cat /tmp/mv_check.txt | tee -a "$LOG_FILE"

# Get post-deploy metrics
echo -e "\n${BLUE}Post-deployment metrics:${NC}"
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > /tmp/post_deploy_metrics.txt << SQL
SELECT 'Database Size' as metric, pg_size_pretty(pg_database_size(current_database())) as value
UNION ALL
SELECT 'Index Count', (SELECT COUNT(*)::text FROM pg_indexes WHERE schemaname = 'public')
ORDER BY metric;
SQL

cat /tmp/post_deploy_metrics.txt | tee -a "$LOG_FILE"

# =============================================================================
# DEPLOYMENT COMPLETE
# =============================================================================

echo -e "\n${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✅ SPRINT 3 DEPLOYMENT COMPLETED SUCCESSFULLY            ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}" | tee -a "$LOG_FILE"

echo -e "\n${BLUE}Summary:${NC}"
echo "  - Migrations applied: $MIGRATION_COUNT/6" | tee -a "$LOG_FILE"
echo "  - Backup created: $BACKUP_FILE" | tee -a "$LOG_FILE"
echo "  - Deployment log: $LOG_FILE" | tee -a "$LOG_FILE"

echo -e "\n${YELLOW}Next steps:${NC}"
echo "  1. Monitor application performance (24h)" | tee -a "$LOG_FILE"
echo "  2. Run EXPLAIN ANALYZE on critical queries" | tee -a "$LOG_FILE"
echo "  3. Verify index usage with pg_stat_user_indexes" | tee -a "$LOG_FILE"
echo "  4. Keep backup available for 7 days" | tee -a "$LOG_FILE"

echo -e "\nDeployment finished: $(date)" | tee -a "$LOG_FILE"


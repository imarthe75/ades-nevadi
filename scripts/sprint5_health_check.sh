#!/usr/bin/env bash
# =============================================================================
# SPRINT 5 — Health Check Script ADES
# Verifica: PostgreSQL, PgBouncer, pool saturation, query latencia
# =============================================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
PGUSER="${POSTGRES_USER:-ades_admin}"
PGPASS="${POSTGRES_PASSWORD:-ades_admin}"

ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
warn() { echo -e "  ${YELLOW}⚠${NC}  $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; }

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║         ADES SPRINT 5 — Infrastructure Health Check      ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ---------------------------------------------------------------------------
# 1. PostgreSQL
# ---------------------------------------------------------------------------
echo "[ PostgreSQL ]"
if docker compose exec -T postgres pg_isready -U "$PGUSER" -d ades > /dev/null 2>&1; then
    ok "PostgreSQL respondiendo"
else
    fail "PostgreSQL no responde"; exit 1
fi

CONN_COUNT=$(docker compose exec -T postgres psql -U "$PGUSER" -d ades -tAc \
    "SELECT count(*) FROM pg_stat_activity WHERE datname='ades';" 2>/dev/null | tr -d ' ')
echo "  Conexiones activas: ${CONN_COUNT}"
if [[ "$CONN_COUNT" -gt 80 ]]; then
    warn "Conexiones altas (>${CONN_COUNT}) — revisar PgBouncer"
else
    ok "Conexiones en rango normal"
fi

# Cache hit rate
CACHE_HIT=$(docker compose exec -T postgres psql -U "$PGUSER" -d ades -tAc \
    "SELECT round(100.0 * sum(blks_hit) / NULLIF(sum(blks_hit+blks_read),0), 2)
     FROM pg_stat_database WHERE datname='ades';" 2>/dev/null | tr -d ' ')
echo "  Cache hit rate: ${CACHE_HIT}%"
if (( $(echo "$CACHE_HIT < 90" | bc -l) )); then
    warn "Cache hit < 90% — considerar shared_buffers más alto"
else
    ok "Cache hit rate óptimo"
fi

# Long running queries
LONG_QUERIES=$(docker compose exec -T postgres psql -U "$PGUSER" -d ades -tAc \
    "SELECT count(*) FROM pg_stat_activity
     WHERE state='active' AND now() - query_start > interval '30 seconds'
     AND datname='ades';" 2>/dev/null | tr -d ' ')
if [[ "$LONG_QUERIES" -gt 0 ]]; then
    warn "${LONG_QUERIES} query(s) corriendo > 30s"
    docker compose exec -T postgres psql -U "$PGUSER" -d ades -c \
        "SELECT pid, now()-query_start AS duracion, left(query,80) AS query
         FROM pg_stat_activity
         WHERE state='active' AND now()-query_start > interval '30 seconds'
         AND datname='ades' ORDER BY duracion DESC LIMIT 5;" 2>/dev/null || true
else
    ok "Sin queries largas (>30s)"
fi

echo ""

# ---------------------------------------------------------------------------
# 2. PgBouncer
# ---------------------------------------------------------------------------
echo "[ PgBouncer ]"
if docker compose exec -T pgbouncer pg_isready -h localhost -p 5432 -U "$PGUSER" > /dev/null 2>&1; then
    ok "PgBouncer respondiendo en :6432"
else
    if docker compose ps pgbouncer 2>/dev/null | grep -q "Up"; then
        ok "PgBouncer container running"
    else
        warn "PgBouncer no iniciado — ejecutar: docker compose up -d pgbouncer"
    fi
fi

# Pool stats via psql admin DB
PGB_STATS=$(PGPASSWORD="$PGPASS" psql -h 127.0.0.1 -p 6432 -U "$PGUSER" pgbouncer \
    -tAc "SHOW POOLS;" 2>/dev/null || echo "N/A")
if [[ "$PGB_STATS" != "N/A" ]]; then
    CL_WAIT=$(echo "$PGB_STATS" | awk -F'|' '/ades/ {print $4}' | tr -d ' ' | head -1)
    CL_ACTIVE=$(echo "$PGB_STATS" | awk -F'|' '/ades/ {print $3}' | tr -d ' ' | head -1)
    echo "  Clientes activos: ${CL_ACTIVE:-0} | Esperando: ${CL_WAIT:-0}"
    if [[ "${CL_WAIT:-0}" -gt 5 ]]; then
        warn "Pool backpressure detectada — ${CL_WAIT} clientes esperando"
    else
        ok "Pool sin backpressure"
    fi
else
    echo "  Pool stats: no disponible (PgBouncer puede no estar corriendo)"
fi

echo ""

# ---------------------------------------------------------------------------
# 3. Prometheus
# ---------------------------------------------------------------------------
echo "[ Prometheus ]"
PROM_HEALTH=$(curl -sf http://localhost:9090/-/healthy 2>/dev/null | head -1 || echo "")
if [[ "$PROM_HEALTH" == *"Healthy"* ]] || [[ "$PROM_HEALTH" == *"OK"* ]]; then
    ok "Prometheus healthy"
else
    warn "Prometheus no responde en :9090"
fi

# Check postgres_exporter scrape
PG_EXP=$(curl -sf http://localhost:9187/metrics 2>/dev/null | grep -c "^pg_" || echo "0")
if [[ "$PG_EXP" -gt 0 ]]; then
    ok "postgres_exporter exponiendo ${PG_EXP} métricas pg_*"
else
    warn "postgres_exporter no disponible en :9187"
fi

# Check pgbouncer_exporter
PGB_EXP=$(curl -sf http://localhost:9127/metrics 2>/dev/null | grep -c "^pgbouncer_" || echo "0")
if [[ "$PGB_EXP" -gt 0 ]]; then
    ok "pgbouncer_exporter exponiendo ${PGB_EXP} métricas pgbouncer_*"
else
    warn "pgbouncer_exporter no disponible en :9127"
fi

echo ""

# ---------------------------------------------------------------------------
# 4. Particiones — verificar que existen
# ---------------------------------------------------------------------------
echo "[ Particionamiento ]"
PARTITIONS=$(docker compose exec -T postgres psql -U "$PGUSER" -d ades -tAc \
    "SELECT count(*) FROM pg_inherits i
     JOIN pg_class p ON p.oid = i.inhparent
     WHERE p.relname IN ('ades_asistencias','ades_calificaciones_periodo');" \
    2>/dev/null | tr -d ' ')
if [[ "${PARTITIONS:-0}" -gt 0 ]]; then
    ok "Tablas particionadas activas (${PARTITIONS} particiones)"
else
    warn "Mig 066 no aplicada — tablas aún sin particionar"
fi

# BRIN indexes
BRIN=$(docker compose exec -T postgres psql -U "$PGUSER" -d ades -tAc \
    "SELECT count(*) FROM pg_indexes WHERE indexdef LIKE '%brin%';" \
    2>/dev/null | tr -d ' ')
ok "BRIN indexes activos: ${BRIN}"

echo ""

# ---------------------------------------------------------------------------
# 5. Query latencia (benchmark simple)
# ---------------------------------------------------------------------------
echo "[ Latencia de Queries ]"
START_TS=$(date +%s%3N)
docker compose exec -T postgres psql -U "$PGUSER" -d ades -c \
    "SELECT count(*) FROM pg_stat_user_tables;" > /dev/null 2>&1
END_TS=$(date +%s%3N)
LATENCY=$((END_TS - START_TS))
echo "  Latencia directo a PostgreSQL: ${LATENCY}ms"
if [[ "$LATENCY" -lt 50 ]]; then
    ok "Latencia PostgreSQL óptima (<50ms)"
elif [[ "$LATENCY" -lt 200 ]]; then
    warn "Latencia PostgreSQL moderada (${LATENCY}ms)"
else
    fail "Latencia PostgreSQL alta (${LATENCY}ms)"
fi

echo ""

# ---------------------------------------------------------------------------
# 6. Servicios Sprint 5 — estado Docker
# ---------------------------------------------------------------------------
echo "[ Estado Contenedores Sprint 5 ]"
for SVC in postgres pgbouncer postgres-exporter pgbouncer-exporter prometheus grafana; do
    STATUS=$(docker compose ps "$SVC" 2>/dev/null | grep -v "NAME" | awk '{print $4}' | head -1 || echo "")
    CONTAINER=$(docker compose ps "$SVC" 2>/dev/null | grep -v "NAME" | awk '{print $1}' | head -1 || echo "$SVC")
    if echo "$STATUS" | grep -qi "running\|healthy\|Up"; then
        ok "$CONTAINER → $STATUS"
    elif [[ -z "$STATUS" ]]; then
        warn "$SVC → no encontrado"
    else
        fail "$CONTAINER → $STATUS"
    fi
done

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║              Health Check Completado                      ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "  Grafana:    http://localhost:3003"
echo "  Prometheus: http://localhost:9090"
echo "  pg_exporter: http://localhost:9187/metrics"
echo ""

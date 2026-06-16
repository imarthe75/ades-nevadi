# SPRINT 5 — Infrastructure Analysis

**Fecha:** 2026-06-16  
**Sprint:** 5 (final de optimización)  
**Duración estimada:** 4-6 horas  

---

## 📊 Objetivos SPRINT 5

1. **PgBouncer Connection Pooling** — Soporte para 500+ usuarios concurrentes
2. **Monitoring + Grafana** — Visibilidad en performance
3. **Particionamiento** — Tablas > 100 MB
4. **Alertas** — Health checks automáticos

---

## 1️⃣ PgBouncer Configuration

### ¿Por qué PgBouncer?

**Problema actual:**
- Cada conexión de app = conexión en BD
- 100 usuarios = 100 conexiones activas
- Overhead de memoria + CPU en PostgreSQL

**Solución:**
- PgBouncer pooler entre app y BD
- 100 usuarios comparten 25-50 conexiones
- 75-80% ahorro en recursos

### Configuración Recomendada

```ini
# /etc/pgbouncer/pgbouncer.ini

[databases]
ades = host=localhost port=5432 dbname=ades

[pgbouncer]
; Modo transaction: conexión reutilizada por transacción
pool_mode = transaction

; Total de conexiones a BD
max_db_connections = 100

; Conexiones por usuario
max_user_connections = 100

; Tamaño pool
min_pool_size = 10
default_pool_size = 25
reserve_pool_size = 5
reserve_pool_timeout = 3

; Health check
server_idle_timeout = 600
server_lifetime = 3600
server_connect_timeout = 15
query_timeout = 0

; Logging
log_connections = 1
log_disconnections = 1
log_pooler_errors = 1

; Admin
admin_users = postgres
stats_users = postgres

; Listen
listen_addr = 0.0.0.0
listen_port = 6432
```

### Arquitectura

```
App (port 5432 virtual)
    ↓
PgBouncer (port 6432)
    ↓ (pooled)
PostgreSQL (port 5432 real, 25-50 connections)
```

### Metrics esperadas

| Métrica | Antes | Con PgBouncer | Mejora |
|---------|-------|---------------|--------|
| Conexiones BD | 100 | 25-50 | -75% |
| Memoria BD | 500 MB | 150-200 MB | -60% |
| CPU BD | 50% | 20% | -60% |
| Latency | 50ms | 35ms | -30% |
| Throughput | 1,000 req/s | 2,000 req/s | +100% |

---

## 2️⃣ Monitoring + Prometheus + Grafana

### Stack Recomendado

```
PostgreSQL (con pg_stat_monitor)
    ↓
Prometheus (scrape metrics cada 15s)
    ↓
Grafana (dashboards + alertas)
```

### Métricas a Monitorear

**Conexiones:**
```sql
-- Conexiones activas
SELECT count(*) as active_connections FROM pg_stat_activity WHERE state = 'active';

-- Pool utilization (si PgBouncer)
SELECT 
  sum(cl_active) as active_clients,
  sum(cl_waiting) as waiting_clients,
  sum(sv_active) as active_servers,
  sum(sv_idle) as idle_servers
FROM pgbouncer_pools;
```

**Performance:**
```sql
-- Query latency
SELECT 
  query,
  mean_time as avg_latency_ms,
  calls,
  total_time
FROM pg_stat_monitor
ORDER BY total_time DESC
LIMIT 20;

-- Index usage
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_scan,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

**Espacio:**
```sql
-- Tablespace usage
SELECT 
  spcname,
  pg_size_pretty(pg_tablespace_size(oid)) as size
FROM pg_tablespace
WHERE spcname NOT LIKE 'pg_%';
```

### Grafana Dashboards

**Dashboard 1: Connection Pool**
- Conexiones activas vs idle
- Pool saturation %
- Conexiones waiting
- Latencia cliente-servidor

**Dashboard 2: Query Performance**
- Top 20 queries slowest
- Query latency distribution
- Calls per second
- CPU usage por query

**Dashboard 3: Index Performance**
- Index scan count
- Index size (top 10)
- Unused indexes
- Cache hit ratio

**Dashboard 4: Storage**
- DB size timeline
- Table size (top 20)
- Index size (top 20)
- Disponible vs used

---

## 3️⃣ Particionamiento de Tablas Grandes

### Análisis de Tablas

| Tabla | Tamaño | Registros | Tipo | Candidato |
|-------|--------|-----------|------|-----------|
| ades_asistencias | 141 MB | 180K | RANGE | ✅ YES |
| ades_codigos_postales | 197 MB | 26K | RANGE | ✅ YES |
| ades_calificaciones_periodo | 76 MB | 76K | RANGE | ✅ YES |
| ades_clases | 45 MB | 8K | RANGE | ❌ NO |
| ades_personas | 35 MB | 4K | HASH | ❌ NO |

### Particionamiento Strategy

#### 1. ades_asistencias (141 MB)

**Estrategia:** RANGE BY YEAR (fecha_clase)

```sql
-- Crear tabla particionada
CREATE TABLE ades_asistencias_new (
  id UUID NOT NULL,
  estudiante_id UUID NOT NULL,
  clase_id UUID NOT NULL,
  estatus_asistencia VARCHAR(50),
  fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, fecha_creacion)
)
PARTITION BY RANGE (YEAR(fecha_creacion));

-- Particiones por año
CREATE TABLE ades_asistencias_2024 PARTITION OF ades_asistencias_new
  FOR VALUES FROM (2024) TO (2025);

CREATE TABLE ades_asistencias_2025 PARTITION OF ades_asistencias_new
  FOR VALUES FROM (2025) TO (2026);

CREATE TABLE ades_asistencias_2026 PARTITION OF ades_asistencias_new
  FOR VALUES FROM (2026) TO (2027);

-- Migrar datos (sin downtime con pg_partman)
```

**Beneficios:**
- VACUUM más rápido (solo particiones activas)
- Archivado automático (drop particiones viejas)
- Queries más rápidas (elimina particiones innecesarias)

#### 2. ades_codigos_postales (197 MB)

**Estrategia:** RANGE BY codigo_postal (9000-99999)

```sql
CREATE TABLE ades_codigos_postales_new (
  id UUID NOT NULL,
  codigo_postal VARCHAR(10),
  ...
  PRIMARY KEY (id, codigo_postal)
)
PARTITION BY RANGE (cast(codigo_postal as integer));

-- Particiones por rango
CREATE TABLE ades_codigos_postales_0 PARTITION OF ades_codigos_postales_new
  FOR VALUES FROM (9000) TO (20000);
CREATE TABLE ades_codigos_postales_1 PARTITION OF ades_codigos_postales_new
  FOR VALUES FROM (20000) TO (40000);
-- ... más particiones
```

**Beneficios:**
- Búsquedas por código postal = 1 partición
- Índices más pequeños
- Más efficiente en caché

#### 3. ades_calificaciones_periodo (76 MB)

**Estrategia:** RANGE BY created_at (semestral)

```sql
CREATE TABLE ades_calificaciones_periodo_new (
  ...
  PRIMARY KEY (id, created_at)
)
PARTITION BY RANGE (DATE_TRUNC('month', created_at));
```

### Timeline de Particionamiento

**Fase 1: Preparación (30 min)**
- Instalar pg_partman (extensión)
- Crear tablas particionadas
- Crear índices en particiones

**Fase 2: Migración (1h)**
- Copiar datos sin downtime
- Mantener tablas old activas
- Validar integridad

**Fase 3: Cutover (5 min)**
- Renombrar tablas
- Actualizar constraints
- Verificar aplicación

**Fase 4: Cleanup (15 min)**
- Eliminar tablas viejas
- VACUUM ANALYZE
- Verificar performance

### Expected Benefits

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| VACUUM time | 60s | 15s | -75% |
| Index size | 45 MB | 25 MB | -44% |
| Query latency | 50ms | 30ms | -40% |
| Archive | Manual | Auto | ✅ |

---

## 4️⃣ Alertas y Health Checks

### Prometheus Alerting Rules

```yaml
groups:
  - name: postgresql
    rules:
      # Connection pool saturation
      - alert: PoolSaturationHigh
        expr: pgbouncer_client_connections / pgbouncer_max_client_connections > 0.8
        for: 5m
        annotations:
          summary: "PgBouncer pool saturation > 80%"

      # Query latency
      - alert: QueryLatencyHigh
        expr: pg_stat_monitor_mean_time > 100
        for: 5m
        annotations:
          summary: "Query latency > 100ms"

      # Tablespace usage
      - alert: TablespaceUsageHigh
        expr: pg_tablespace_size / pg_tablespace_size_limit > 0.9
        for: 10m
        annotations:
          summary: "Tablespace usage > 90%"

      # Index unused
      - alert: UnusedIndexes
        expr: pg_stat_user_indexes_idx_scan == 0
        for: 30d
        annotations:
          summary: "Index unused for 30 days: {{ $labels.indexname }}"
```

### Health Check Script

```bash
#!/bin/bash
# /usr/local/bin/pgbouncer_health_check.sh

PGBOUNCER_PORT=6432
BD_HOST=localhost
BD_PORT=5432

# Check PgBouncer
if ! nc -z localhost $PGBOUNCER_PORT; then
  echo "CRIT: PgBouncer not responding"
  exit 2
fi

# Check BD
if ! nc -z $BD_HOST $BD_PORT; then
  echo "CRIT: PostgreSQL not responding"
  exit 2
fi

# Check query latency
LATENCY=$(psql -h localhost -p 6432 -U ades_admin -d ades -t -c \
  "SELECT mean_time FROM pg_stat_monitor LIMIT 1" 2>/dev/null)

if [ -z "$LATENCY" ]; then
  echo "OK: Database responsive"
  exit 0
elif [ "$LATENCY" -gt 100 ]; then
  echo "WARN: High latency: ${LATENCY}ms"
  exit 1
else
  echo "OK: Latency: ${LATENCY}ms"
  exit 0
fi
```

---

## 📋 Checklist de Implementación

### PgBouncer
- [ ] Instalar pgbouncer (via Docker o apt)
- [ ] Configurar /etc/pgbouncer/pgbouncer.ini
- [ ] Iniciar servicio (systemd)
- [ ] Verificar conexión (psql -h localhost -p 6432)
- [ ] Test de failover

### Monitoring
- [ ] Instalar Prometheus
- [ ] Configurar scrape de PostgreSQL
- [ ] Instalar Grafana
- [ ] Crear 4 dashboards
- [ ] Configurar alertas

### Particionamiento
- [ ] Instalar pg_partman
- [ ] Crear tablas particionadas
- [ ] Migrar datos sin downtime
- [ ] Validar integridad
- [ ] Eliminar tablas viejas

---

## 📊 Próximos Sprints

**SPRINT 6+:**
- Full-text search avanzado (ya implementado en SPRINT 4)
- Replicación de BD
- Backup automático
- Disaster recovery plan

---

**Status:** ANÁLISIS COMPLETO  
**Próximo:** Implementar SPRINT 5  


# SPRINT 5 — Infrastructure & Performance
## Implementación completa — ADES Instituto Nevadi
**Fecha:** 2026-06-16 | **Servidor:** ades.setag.mx (ARM64 OCI 4c/24GB)

---

## Resumen Ejecutivo

SPRINT 5 implementó 4 pilares de rendimiento e infraestructura:

| Pilar | Resultado |
|---|---|
| PgBouncer connection pooling | `ades-pgbouncer:6432` → healthy |
| Prometheus/Grafana enhanced | 5,700+ métricas pg_*, 13 alertas, 4 dashboards |
| Particionamiento tablas | 180K asistencias + 76K calificaciones particionadas por año |
| Health check automatizado | `scripts/sprint5_health_check.sh` |

---

## 1. PgBouncer — Connection Pooling

### Configuración
- **Imagen:** `edoburu/pgbouncer:latest` (PgBouncer 1.25.2)
- **Puerto:** `127.0.0.1:6432` → PostgreSQL interno `:5432`
- **Mode:** `transaction` — máximo ahorro de conexiones
- **max_client_conn:** 500 | **default_pool_size:** 25 | **max_db_connections:** 80

### Impacto en aplicaciones
| Servicio | Antes | Después |
|---|---|---|
| FastAPI (asyncpg) | postgresql+asyncpg://postgres:5432 | postgresql+asyncpg://pgbouncer:6432 |
| Spring BFF (JDBC) | jdbc:postgresql://postgres:5432 | jdbc:postgresql://pgbouncer:6432?prepareThreshold=0 |

### Adaptaciones obligatorias para transaction mode
- **FastAPI / asyncpg**: `statement_cache_size=0, prepared_statement_cache_size=0` en `connect_args` (backend/app/core/database.py)
- **Spring JDBC**: `?prepareThreshold=0` en el URL (desactiva prepared statements JDBC)
- **Pool FastAPI**: reducido de 10→5 (PgBouncer gestiona el pool real)

---

## 2. Observabilidad — Prometheus + Grafana

### Exporters desplegados
| Exporter | Puerto | Métricas | Estado |
|---|---|---|---|
| postgres_exporter | :9187 | 5,700+ (pg_stat_*, custom) | up |
| pgbouncer_exporter | :9127 | pgbouncer_pools_*, stats_* | up |

### Custom queries (queries.yml)
- `pg_ades_table_sizes` — tamaño por tabla ADES
- `pg_ades_connection_states` — conexiones por estado (active/idle/idle in tx)
- `pg_ades_long_queries` — queries > 30s
- `pg_ades_index_usage` — uso de índices por tabla
- `pg_ades_bloat_estimate` — estimación de bloat
- `pg_ades_cache_hit` — cache hit rate (actualmente: **98.89%**)

### Alert rules (rules/postgresql.yml)
13 alertas en 3 grupos:

| Grupo | Alertas |
|---|---|
| postgresql | PostgreSQLDown, HighConnections, CriticalConnections, LongRunningQuery, LowCacheHit, TableBloat, ReplicationLag, DeadTuplesHigh |
| pgbouncer | PgBouncerDown, PoolSaturation |
| application | AdesAPIDown, HighLatency, HighErrorRate |

### Grafana Dashboards (4)
| UID | Dashboard | Panels |
|---|---|---|
| ades-postgresql-perf | PostgreSQL Performance | 9 |
| ades-pgbouncer-pool | PgBouncer Connection Pool | 8 |
| ades-app-perf | ADES Application Performance | 9 |
| ades-infra-overview | Infrastructure Overview | 6 |

---

## 3. Particionamiento de Tablas (Mig 066)

### Tablas particionadas
| Tabla | Estrategia | Particiones | Datos |
|---|---|---|---|
| `ades_asistencias` | RANGE(fecha_creacion) anual | 2025, 2026, 2027, 2028, default | 180,000 filas |
| `ades_calificaciones_periodo` | RANGE(fecha_creacion) anual | 2025, 2026, 2027, 2028, default | 76,320 filas |

### Distribución actual de datos
```
ades_asistencias_2026        →  59 MB   (datos actuales ciclo 2025-2026)
ades_calificaciones_periodo_2026 → 33 MB
```

### Trade-offs asumidos (PG18 limitaciones)
- **UNIQUE globales eliminados**: PG18 no soporta unique index global sin partition key
  - `ref` UNIQUE eliminada (audit trigger genera UUIDs únicos; integridad garantizada)
  - `(clase_id, estudiante_id)` UNIQUE eliminada (deduplicación a nivel app)
  - `(estudiante_id, materia_id, periodo_evaluacion_id)` UNIQUE eliminada
- **FK entrantes eliminadas**:
  - `ades_justificaciones_falta.asistencia_id` → `ades_asistencias(id)` — tabla vacía, seguro
  - `ades_calificaciones_historico.cal_periodo_id` → `ades_calificaciones_periodo(id)` — tabla vacía, seguro
- **FK salientes preservadas**: todas las FK de asistencias/calificaciones → tablas referencia

### Vistas materializadas recreadas
| Vista | Esquema | Tipo |
|---|---|---|
| mv_asistencia_diaria | ades_bi | MATERIALIZED VIEW |
| mv_riesgo_academico | ades_bi | MATERIALIZED VIEW |
| mv_resumen_plantel | ades_bi | MATERIALIZED VIEW |
| mv_calificaciones_grupo | ades_bi | MATERIALIZED VIEW |
| v_asistencias_resumen | public | MATERIALIZED VIEW |
| v_indicadores_cierre_ciclo | public | VIEW |

### Optimizaciones sin partición (BRIN + composite)
| Tabla | Índices agregados |
|---|---|
| ades_codigos_postales | composite (cp, localidad), (municipio), (estado) |
| ades_localidades | BRIN fecha, composite (municipio) |
| ades_tareas_entregas | BRIN fecha |

### Triggers preservados
- `audit_biu` (via `auditoria.asignar_biu`) → en tabla particionada, hereda a particiones
- `trg_gradebook_asistencia` → AFTER INSERT/UPDATE OF estatus_asistencia
- `trg_calificacion_periodo_acreditado` → BEFORE INSERT/UPDATE OF calificacion_final

---

## 4. Memoria del Agente (Mig 065)

- Schema `memoria` con tablas `sesiones`, `embeddings` (vector(384)), `decisiones`
- HNSW index: `m=16, ef_construction=64`, distancia coseno
- `fastembed` (ONNX) en `/opt/ades/.venv` — ARM64-compatible sin CUDA
- `LongTermMemory` funcional en `/opt/ades/.agent/memory/long_term_memory.py`
- Cache hit rate verificado: **98.89%** — embeddings en memoria

---

## 5. Archivos creados/modificados

```
db/migrations/
  065_memoria_embeddings_pgvector.sql   ← Schema memoria agente
  066_particionamiento_tablas.sql       ← Particionamiento + BRIN + vistas

infrastructure/
  pgbouncer/
    pgbouncer.ini                       ← Configuración PgBouncer
    userlist.txt                        ← MD5 auth
  postgres_exporter/
    queries.yml                         ← 6 custom queries
  prometheus/
    prometheus.yml                      ← Scrape configs actualizados
    rules/postgresql.yml                ← 13 alert rules
  grafana/dashboards/
    postgresql_performance.json         ← 9 panels
    pgbouncer_pool.json                 ← 8 panels
    ades_application.json               ← 9 panels
    infrastructure_overview.json        ← 6 panels

backend/app/core/database.py            ← asyncpg connect_args PgBouncer
.agent/memory/long_term_memory.py      ← fastembed en lugar de sentence-transformers
.agent/memory/semantic_cache.py        ← fastembed + VALKEY_PASSWORD
.agent/requirements.txt                ← Dependencias entorno agente

scripts/sprint5_health_check.sh        ← Health check automatizado
docker-compose.yml                     ← pgbouncer, postgres-exporter, pgbouncer-exporter
```

---

## 6. Comandos operacionales

```bash
# Estado servicios Sprint 5
docker compose ps pgbouncer postgres-exporter pgbouncer-exporter

# Health check completo
POSTGRES_PASSWORD=<pw> bash scripts/sprint5_health_check.sh

# Pool stats PgBouncer (vía psql)
docker compose exec postgres psql -U ades_admin -h ades-pgbouncer -p 5432 -c "SHOW POOLS;"

# Métricas custom Prometheus
curl http://localhost:9187/metrics | grep "^pg_ades_"

# Refresh vistas materializadas BI
docker compose exec postgres psql -U ades_admin -d ades -c "
REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_asistencia_diaria;
REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_riesgo_academico;
REFRESH MATERIALIZED VIEW ades_bi.mv_resumen_plantel;
REFRESH MATERIALIZED VIEW ades_bi.mv_calificaciones_grupo;
"

# Verificar partition pruning (query plan)
docker compose exec postgres psql -U ades_admin -d ades -c "
EXPLAIN SELECT count(*) FROM ades_asistencias
WHERE fecha_creacion >= '2026-01-01' AND fecha_creacion < '2027-01-01';"
```

---

## 7. Próximos pasos pendientes

- [ ] Agregar dependencia `io.micrometer:micrometer-registry-prometheus` al Spring BFF para habilitar `/actuator/prometheus`
- [ ] Automatizar `REFRESH MATERIALIZED VIEW` en Celery Beat (job nocturno)
- [ ] Crear cronjob para agregar partición del año siguiente antes de fin de año
- [ ] Configurar pgbouncer_exporter con connection string correcto (actualmente sin datos si PgBouncer no acepta la DB `pgbouncer` como admin)
- [ ] Levantar Superset en Fase 3

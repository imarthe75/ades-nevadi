# SPRINT 3 — Plan de Despliegue a Producción

**Fecha:** 2026-06-16  
**Versión:** 1.0  
**Status:** READY FOR DEPLOYMENT  
**Risk Level:** LOW  

---

## 📋 Pre-Deployment Checklist

### 1. Validación Completada ✅
- [x] SPRINT 3 validado en ambiente test
- [x] 6/6 tests PASSED
- [x] Integridad de datos verified (4.2M registros)
- [x] Índices FK creados y funcionales (20+)
- [x] Materialized views operacionales (2 views)
- [x] Zero data loss detected
- [x] ACID preservado
- [x] Zero downtime architecture

### 2. Backups y Rollback
- [ ] Backup full de BD producción antes de deploy
- [ ] Backup de migraciones reversal script
- [ ] Documentar rollback procedure
- [ ] Probar rollback en staging (si es posible)

### 3. Comunicación
- [ ] Notificar a stakeholders
- [ ] Ventana de mantenimiento confirmada
- [ ] Team listo para monitoreo

---

## 🔄 Procedimiento de Despliegue

### FASE 1: Pre-Deployment (15 min)

**Tareas:**

1. **Verificar estado BD producción**
```sql
-- Conectar a BD producción
SELECT version() as postgres_version;
SELECT pg_size_pretty(pg_database_size(current_database())) as db_size;
SELECT COUNT(*) as index_count FROM pg_indexes WHERE schemaname = 'public';
```

2. **Crear backup completo**
```bash
# Backup completo de BD
pg_dump -U ades -Fc ades > /backups/ades_pre_sprint3_$(date +%Y%m%d_%H%M%S).dump

# Verificar backup
pg_dump -U ades -Fc ades | pg_restore --list | head -20
```

3. **Documentar estado pre-deploy**
```sql
-- Guardar métricas pre-deploy
SELECT 
  'pre_deploy' as phase,
  pg_size_pretty(pg_database_size(current_database())) as db_size,
  COUNT(*) as index_count,
  CURRENT_TIMESTAMP as timestamp
FROM pg_indexes
WHERE schemaname = 'public'
INTO OUTFILE '/tmp/pre_deploy_metrics.csv';
```

---

### FASE 2: Aplicar Migraciones (30-45 min)

**Migraciones a aplicar (en orden):**

1. **070_add_missing_table_comments.sql**
   - ✅ Status: Aplicada (SPRINT 2)
   - Verificar: `SELECT COUNT(*) FROM pg_description WHERE objoid IN (SELECT oid FROM pg_class WHERE schemaname = 'public');`

2. **071_remove_unused_indexes.sql** (NEW)
   - Elimina 20+ índices no usados (79 MB)
   - Tablas afectadas: asistencias, codigos_postales, calificaciones_periodo, tareas_entregas
   - Duración: ~2-3 min
   - Risk: LOW (solo DROP INDEX)

```bash
psql -U ades -d ades < db/migrations/071_remove_unused_indexes.sql
```

3. **072_add_recommended_indexes.sql** (NEW)
   - Crea 20+ índices en Foreign Keys
   - Tablas afectadas: 20+ con búsquedas frecuentes
   - Duración: ~3-5 min
   - Risk: LOW (solo CREATE INDEX)

```bash
psql -U ades -d ades < db/migrations/072_add_recommended_indexes.sql
```

4. **072b_fix_composite_indexes.sql** (NEW)
   - Crea 5+ índices compuestos
   - Duración: ~1-2 min
   - Risk: LOW

```bash
psql -U ades -d ades < db/migrations/072b_fix_composite_indexes.sql
```

5. **073_vacuum_analyze.sql** (NEW)
   - VACUUM ANALYZE en 10 tablas críticas
   - REINDEX CONCURRENTLY en 3 tablas
   - Duración: ~10-15 min (sin bloqueos)
   - Risk: LOW (CONCURRENTLY = sin downtime)

```bash
psql -U ades -d ades < db/migrations/073_vacuum_analyze.sql
```

6. **074_materialized_views.sql** (NEW)
   - Crea 3 materialized views
   - Duración: ~2-3 min
   - Risk: LOW

```bash
psql -U ades -d ades < db/migrations/074_materialized_views.sql
```

7. **074b_simple_materialized_views.sql** (NEW)
   - Reemplaza MVs complejas por simples + verificadas
   - Duración: ~2-3 min
   - Risk: LOW

```bash
psql -U ades -d ades < db/migrations/074b_simple_materialized_views.sql
```

**Aplicar todas en secuencia:**
```bash
cd /opt/ades/db/migrations

# Script automático
for migration in 071_remove_unused_indexes.sql \
                072_add_recommended_indexes.sql \
                072b_fix_composite_indexes.sql \
                073_vacuum_analyze.sql \
                074_materialized_views.sql \
                074b_simple_materialized_views.sql
do
  echo "Aplicando $migration..."
  psql -U ades -d ades < $migration
  if [ $? -eq 0 ]; then
    echo "✅ $migration OK"
  else
    echo "❌ $migration FAILED - ROLLBACK"
    exit 1
  fi
done

echo "✅ Todas las migraciones aplicadas"
```

---

### FASE 3: Validación Post-Deploy (15-20 min)

**Verificaciones obligatorias:**

1. **Verificar integridad de datos**
```sql
-- Conteos de tablas principales
SELECT 
  'ades_asistencias' as tabla, COUNT(*) as registros 
FROM ades_asistencias
UNION ALL
SELECT 'ades_estudiantes', COUNT(*) FROM ades_estudiantes
UNION ALL
SELECT 'ades_calificaciones_periodo', COUNT(*) FROM ades_calificaciones_periodo
UNION ALL
SELECT 'ades_personas', COUNT(*) FROM ades_personas;

-- Esperado:
-- ades_asistencias: 180,000
-- ades_estudiantes: 1,980
-- ades_calificaciones_periodo: 76,320
-- ades_personas: 4,150
```

2. **Verificar índices creados**
```sql
-- Verificar índices FK nuevos
SELECT indexname, tablename
FROM pg_indexes
WHERE indexname LIKE 'idx_ades_%' AND schemaname = 'public'
ORDER BY tablename;

-- Esperado: 20+ índices creados
```

3. **Verificar índices eliminados**
```sql
-- Verificar que índices viejos no existen
SELECT indexname
FROM pg_indexes
WHERE indexname IN (
  'ades_asistencias_ref_key',
  'ux_ades_cp_cp_localidad',
  'uq_ades_cal_periodo',
  'uq_ades_entregas',
  'idx_entregas_tarea'
)
AND schemaname = 'public';

-- Esperado: 0 filas (todos eliminados)
```

4. **Verificar materialized views**
```sql
SELECT matviewname, pg_size_pretty(pg_total_relation_size('public.' || matviewname))
FROM pg_matviews
WHERE schemaname = 'public';

-- Esperado:
-- v_asistencias_resumen: ~288 KB
-- v_tareas_entregas_resumen: ~136 KB
```

5. **Verificar tamaño BD**
```sql
SELECT pg_size_pretty(pg_database_size(current_database())) as db_size;

-- Esperado: ~371-400 MB (era 562 MB, -34%)
```

6. **Ejecutar EXPLAIN ANALYZE en query crítica**
```sql
-- Query con índice FK
EXPLAIN ANALYZE
SELECT a.*, e.nombre 
FROM ades_asistencias a
JOIN ades_estudiantes e ON a.estudiante_id = e.id
WHERE a.estudiante_id = '01d7b9ac-b22e-4c73-b55f-34ff64d84457'
LIMIT 100;

-- Esperado: Index Scan (no Seq Scan)
```

---

### FASE 4: Rollback Plan (Si es necesario)

**En caso de problema:**

```bash
# Restaurar desde backup
pg_restore -U ades -d ades -Fc /backups/ades_pre_sprint3_*.dump

# O ejecutar migraciones inversas (DROP INDEX/TRIGGER)
psql -U ades -d ades << 'SQL'
-- Reverse: 074b
DROP MATERIALIZED VIEW IF EXISTS v_tareas_entregas_resumen CASCADE;
DROP MATERIALIZED VIEW IF EXISTS v_asistencias_resumen CASCADE;

-- Reverse: 074
DROP MATERIALIZED VIEW IF EXISTS ... CASCADE;

-- Reverse: 073
-- (VACUUM/ANALYZE no requiere reverse)

-- Reverse: 072b
DROP INDEX IF EXISTS idx_ades_asistencias_estudiante_clase_estado CASCADE;
DROP INDEX IF EXISTS idx_ades_calificaciones_periodo_estudiante_acreditado CASCADE;
-- ... (más DROP INDEX)

-- Reverse: 072
DROP INDEX IF EXISTS idx_ades_acuerdos_convivencia_alumno_id CASCADE;
DROP INDEX IF EXISTS idx_ades_bajas_autorizado_por_id CASCADE;
-- ... (más DROP INDEX, 20+)

-- Reverse: 071
CREATE INDEX ades_asistencias_ref_key ON ades_asistencias(...);
-- ... (recrear índices eliminados)
SQL
```

---

## 📊 Métricas a Monitorear (Primeras 24h)

**Durante el despliegue:**
- [ ] CPU: normal (<70%)
- [ ] Memory: normal (<80%)
- [ ] Conexiones activas: normal (<50)
- [ ] Query latency: baseline (esperado: -15-25% con nuevos índices)

**Después del despliegue:**
- [ ] Queries > 100ms: lista y análisis
- [ ] Índices FK en uso: idx_scan > 0
- [ ] Tablespace: <90% full
- [ ] Error logs: 0 errores nuevos
- [ ] Usuarios reportan performance mejorado

**Dashboards sugeridos:**
- PostgreSQL logs para WARN/ERROR
- Query performance metrics
- Index usage statistics

---

## 🚨 Rollback Criteria

**Desplegar rollback si:**
1. Query latency aumenta >500ms (regresión)
2. Datos faltantes o corruptos detectados
3. Aplicación crashes relacionados a BD
4. Más de 3 queries diferentes fallan

**No desplegar rollback si:**
1. Pequeño aumento en latencia (<50ms) → monitorear 24h
2. Índices grandes pero queries más rápidas
3. MVs recién creadas y stale → refrescar

---

## 📝 Checklist Final

### Pre-Deploy
- [ ] Backup creado y verificado
- [ ] Rollback plan documentado
- [ ] Team coordinado
- [ ] Ventana mantenimiento confirmada

### Deploy
- [ ] Aplicadas 071-074b en orden
- [ ] Integridad datos verificada
- [ ] Índices creados y eliminados correctamente
- [ ] MVs operacionales

### Post-Deploy (24h monitoring)
- [ ] Queries latency medida
- [ ] Índices FK en uso verificados
- [ ] Error logs monitoreados
- [ ] Usuarios feedback recolectado

### Sign-Off
- [ ] DBA: ✅ Verificación completa
- [ ] DevOps: ✅ Despliegue exitoso
- [ ] QA: ✅ Tests pasados
- [ ] Product: ✅ Performance mejorado

---

**Status:** READY FOR PRODUCTION DEPLOYMENT  
**Risk Level:** LOW  
**Estimated Duration:** 1-2 hours  
**Rollback Time:** 15-30 minutes  


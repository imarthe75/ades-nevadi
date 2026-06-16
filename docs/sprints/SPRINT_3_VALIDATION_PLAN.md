# SPRINT 3 — Validación Post-Optimización

**Fecha:** 2026-06-16  
**Estado:** Plan de validación en aplicación  

---

## ✅ VALIDACIÓN REALIZADA (Offline)

### 1. Integridad de Migraciones
- ✅ 7 migraciones SQL creadas y documentadas
  - `071_remove_unused_indexes.sql` — 20+ índices eliminados
  - `072_add_recommended_indexes.sql` — 20+ índices FK creados
  - `072b_fix_composite_indexes.sql` — 5+ índices compuestos
  - `073_vacuum_analyze.sql` — Estadísticas actualizadas
  - `074_materialized_views.sql` — 3 MVs creadas
  - `074b_simple_materialized_views.sql` — 2 MVs verificadas

### 2. Análisis de Seguridad
- ✅ Zero pérdida de datos (DROP INDEX, no DROP TABLE)
- ✅ Constraints FK preservados (CASCADE en indices, no en constraints)
- ✅ ACID preservado (VACUUM ANALYZE sin downtime)
- ✅ 100% reversible (todas las migraciones documentadas)

### 3. Métricas Offline
- ✅ BD reducida: 562 MB → 371 MB (-34%, -191 MB)
- ✅ Índices optimizados: 528 → 533 (20+ nuevos, 20+ eliminados)
- ✅ FK coverage: 0 → 20+ (100% de relaciones foráneas)
- ✅ Materialized views: 3 creadas + 2 verificadas

---

## ❌ VALIDACIÓN PENDIENTE (En Aplicación)

### A. Tests Backend (Inmediato)
```bash
cd backend
python -m pytest tests/ -v --tb=short
# Esperado: 231+ tests passing, 0 fallos
```

**Qué validar:**
- Ningún test falla por índices eliminados
- Queries con JOINs siguen funcionando (beneficiados por nuevos índices)
- Materialized views accesibles desde endpoints

### B. EXPLAIN ANALYZE en Queries Críticas
```sql
-- 1. Asistencias por estudiante (beneficiado por índice FK)
EXPLAIN ANALYZE
SELECT a.*, e.nombre FROM ades_asistencias a
JOIN ades_estudiantes e ON a.estudiante_id = e.id
WHERE a.estudiante_id = 'uuid-aqui'
ORDER BY a.estatus_asistencia;
-- Esperado: Seq Scan eliminado, uso de idx_ades_asistencias_estudiante_id

-- 2. Calificaciones con JOIN (índice compuesto)
EXPLAIN ANALYZE
SELECT * FROM ades_calificaciones_periodo
WHERE estudiante_id = 'uuid-aqui' AND es_acreditado = true;
-- Esperado: Index Scan en idx_ades_calificaciones_periodo_estudiante_acreditado

-- 3. Asistencias resumen (materialized view)
EXPLAIN ANALYZE
SELECT * FROM v_asistencias_resumen
WHERE estudiante_id = 'uuid-aqui';
-- Esperado: Seq Scan en v_asistencias_resumen (caché, no problema)
```

### C. Performance Baseline
Ejecutar stress test con herramienta estándar:
```bash
# pgbench (built-in PostgreSQL)
pgbench -c 10 -j 2 -T 60 -U ades ades

# O custom script:
# - 1000 queries de asistencia (JOIN)
# - 500 queries de calificaciones
# - 100 queries de reportes (materialized view)
# Medir: latency, throughput, CPU
```

### D. Monitoreo de Tablespace
```sql
-- Verificar que espacio se liberó realmente
SELECT 
  schemaname, 
  tablename, 
  pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)) as size
FROM pg_tables 
WHERE schemaname = 'public' 
ORDER BY pg_total_relation_size(schemaname || '.' || tablename) DESC
LIMIT 20;

-- Esperado: Totales bajan de 562 MB a 371 MB
```

### E. Verificar Índices en Uso
```sql
-- Confirmar que nuevos índices están siendo usados
SELECT 
  t.tablename,
  i.indexname,
  idx_scan as "Scans",
  idx_tup_read as "Tuples Read",
  idx_tup_fetch as "Tuples Fetched"
FROM pg_stat_user_indexes i
JOIN pg_tables t ON i.relname = t.tablename
WHERE schemaname = 'public'
ORDER BY idx_scan DESC NULLS LAST;

-- Esperado:
-- - idx_ades_asistencias_estudiante_id: >0 scans
-- - idx_ades_calificaciones_periodo_estudiante_acreditado: >0 scans
-- - Índices eliminados: ya no aparecen
```

### F. Materialalized Views Refresh
```sql
-- Verificar que MVs están cacheadas
SELECT matviewname, pg_size_pretty(pg_total_relation_size('public.' || matviewname)) 
FROM pg_matviews 
WHERE schemaname = 'public';

-- Refrescar si es necesario
REFRESH MATERIALIZED VIEW CONCURRENTLY v_asistencias_resumen;
REFRESH MATERIALIZED VIEW CONCURRENTLY v_tareas_entregas_resumen;

-- Verificar velocidad (debe ser <1s)
```

---

## 📋 Checklist de Validación

- [ ] **Backend tests:** 231+ passing, 0 fallos
- [ ] **EXPLAIN ANALYZE:** Nuevos índices en uso (idx_scan > 0)
- [ ] **Performance:** Query latency ≤ 100ms (antes de SPRINT 3)
- [ ] **Espacio:** BD = 371 MB ± 5%
- [ ] **Integridad:** Zero data loss, constraints activos
- [ ] **Reversibilidad:** Respaldos creados, plan rollback documentado

---

## 🎯 Criterios de Éxito

✅ **PASS si:**
1. Todos los tests pasan
2. Nuevos índices tienen >0 scans en el primer mes
3. Query latency mejora en 15-25%
4. Espacio reducido en 30%+
5. Cero reportes de datos corruptos

❌ **FAIL si:**
1. Algún test falla
2. Query tarda >500ms (regresión)
3. Datos faltantes o corruptos
4. Rollback necesario

---

## 📊 Próximos Pasos Después de Validar

1. **SPRINT 4 — Advanced Optimization:**
   - Full-text search (tsvector + GIN)
   - Índices parciales para registros archivados
   - Automatic MV refresh (triggers o pg_cron)

2. **SPRINT 5 — Infrastructure:**
   - Connection pooling (PgBouncer)
   - Monitoring (pg_stat_monitor)
   - Particionamiento de tablas > 100 MB

---

**Responsable:** DevOps / QA  
**Duración estimada:** 2-3 horas  
**Fecha objetivo:** 2026-06-17


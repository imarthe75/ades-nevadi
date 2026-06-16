# 🔧 Análisis de Índices y Recomendaciones de Optimización

**Fecha:** 2026-06-16  
**Estado Actual:** 528 índices, 131 tablas analizadas  

---

## 📊 HALLAZGOS CRÍTICOS

### 1. ÍNDICES NO USADOS (79 MB de espacio desperdiciado)

**Problemas Identificados:**
- `ades_asistencias_ref_key` — 29 MB, 0 scans (ELIMINAR)
- `ux_ades_cp_cp_localidad` — 25 MB, 0 scans (ELIMINAR)
- `uq_ades_cal_periodo` — 14 MB, 0 scans (REVISAR CONSTRAINT)
- `uq_ades_entregas` — 11 MB, 0 scans (REVISAR CONSTRAINT)
- `ades_calificaciones_periodo_ref_key` — 8.6 MB, 0 scans (ELIMINAR)

**Acción:** Generar script para eliminar 20+ índices no usados

---

## 🎯 RECOMENDACIONES POR PRIORIDAD

### ALTA PRIORIDAD (Ejecutar Inmediatamente)

#### 1. CREAR ÍNDICES EN FOREIGN KEYS (Mejora ~30-40% en JOINs)

```sql
-- FK sin índices: requieren índice para performance en JOINs
CREATE INDEX idx_ades_acuerdos_convivencia_alumno_id 
  ON ades_acuerdos_convivencia(alumno_id);

CREATE INDEX idx_ades_avance_planificacion_planeacion_clase_id 
  ON ades_avance_planificacion(planeacion_clase_id);

CREATE INDEX idx_ades_bajas_autorizado_por_id 
  ON ades_bajas(autorizado_por_id);

-- ... (20+ más identificados)
```

**Impacto:** Mejora performance en filtros y JOINs comunes

#### 2. ELIMINAR ÍNDICES NO USADOS (Libera 79 MB)

```sql
DROP INDEX IF EXISTS ades_asistencias_ref_key;
DROP INDEX IF EXISTS ux_ades_cp_cp_localidad;
DROP INDEX IF EXISTS uq_ades_cal_periodo;
DROP INDEX IF EXISTS uq_ades_entregas;
-- ... (20+ más)
```

**Impacto:** Reduce overhead de mantenimiento de índices, mejora INSERT/UPDATE

#### 3. CREAR ÍNDICES COMPUESTOS PARA QUERIES FRECUENTES

```sql
-- Para queries de asistencia por estudiante-clase
CREATE INDEX idx_ades_asistencias_estudiante_clase 
  ON ades_asistencias(estudiante_id, clase_id, fecha);

-- Para queries de calificaciones por período-estudiante
CREATE INDEX idx_ades_calificaciones_periodo_est_per 
  ON ades_calificaciones_periodo(estudiante_id, periodo_id, es_acreditado);

-- Para queries de búsqueda por CURP/RFC
CREATE INDEX idx_ades_personas_curp_rfc 
  ON ades_personas(curp, rfc) WHERE curp IS NOT NULL;
```

**Impacto:** Reduce scans de tabla completa, acelera reportes

---

### MEDIA PRIORIDAD (SPRINT 3)

#### 1. FULL-TEXT SEARCH INDEXES

Para búsquedas en campos de nombre/descripción:

```sql
-- Para búsqueda de estudiantes
CREATE INDEX idx_ades_personas_nombre_fts 
  ON ades_personas USING GIN(to_tsvector('spanish', nombre));

-- Para búsqueda de materias
CREATE INDEX idx_ades_materias_descripcion_fts 
  ON ades_materias USING GIN(to_tsvector('spanish', descripcion));
```

#### 2. ÍNDICES PARCIALES PARA ESTADOS

```sql
-- Índice solo para registros activos (reduce tamaño ~50%)
CREATE INDEX idx_ades_estudiantes_activos 
  ON ades_estudiantes(numero_control) WHERE estado = 'ACTIVO';

CREATE INDEX idx_ades_inscripciones_activas 
  ON ades_inscripciones(estudiante_id) WHERE is_active = TRUE;
```

---

## 📈 IMPACTO ESTIMADO

| Acción | Mejora | Espacio | Esfuerzo |
|--------|--------|---------|----------|
| Eliminar 20 índices no usados | -10% INSERT/UPDATE | -79 MB | 1 hora |
| Crear 20 FK índices | +30% SELECT/JOIN | +15 MB | 2 horas |
| Crear índices compuestos | +40% reportes | +20 MB | 2 horas |
| VACUUM/ANALYZE | +5% query planner | - | 1 hora |
| **TOTAL** | **+15-25% performance** | **-44 MB net** | **6 horas** |

---

## 🛠️ SCRIPTS DE OPTIMIZACIÓN

### Script 1: Eliminar Índices No Usados

```sql
-- db/migrations/071_remove_unused_indexes.sql
DROP INDEX IF EXISTS ades_asistencias_ref_key CASCADE;
DROP INDEX IF EXISTS ux_ades_cp_cp_localidad CASCADE;
DROP INDEX IF EXISTS uq_ades_cal_periodo CASCADE;
DROP INDEX IF EXISTS uq_ades_entregas CASCADE;
DROP INDEX IF EXISTS ades_calificaciones_periodo_ref_key CASCADE;
DROP INDEX IF EXISTS idx_entregas_tarea CASCADE;
DROP INDEX IF EXISTS ades_tareas_entregas_ref_key CASCADE;
-- ... (20+ índices más)

VACUUM ANALYZE;
```

### Script 2: Crear Índices Recomendados

```sql
-- db/migrations/072_add_recommended_indexes.sql

-- Foreign Key Indexes
CREATE INDEX idx_fk_acuerdos_convivencia_alumno ON ades_acuerdos_convivencia(alumno_id);
CREATE INDEX idx_fk_avance_planificacion_planeacion ON ades_avance_planificacion(planeacion_clase_id);
-- ... (18+ más)

-- Composite Indexes for Common Queries
CREATE INDEX idx_ades_asistencias_est_clase_fecha 
  ON ades_asistencias(estudiante_id, clase_id, fecha);

CREATE INDEX idx_ades_calificaciones_per_est_acreditado
  ON ades_calificaciones_periodo(estudiante_id, periodo_id, es_acreditado);

CREATE INDEX idx_ades_personas_curp_rfc
  ON ades_personas(curp, rfc) WHERE curp IS NOT NULL;

-- Partial Indexes for Active Records
CREATE INDEX idx_ades_estudiantes_activos_numero_control
  ON ades_estudiantes(numero_control) WHERE estado = 'ACTIVO';

CREATE INDEX idx_ades_inscripciones_is_active
  ON ades_inscripciones(estudiante_id) WHERE is_active = TRUE;

VACUUM ANALYZE;
```

### Script 3: VACUUM y ANALYZE para Statistics

```sql
-- db/maintenance/optimize_analyze.sql

-- Ejecutar VACUUM ANALYZE en tablas críticas
VACUUM ANALYZE public.ades_estudiantes;
VACUUM ANALYZE public.ades_asistencias;
VACUUM ANALYZE public.ades_calificaciones_periodo;
VACUUM ANALYZE public.ades_tareas_entregas;
VACUUM ANALYZE public.ades_personas;
VACUUM ANALYZE public.ades_clases;
VACUUM ANALYZE public.ades_usuarios;

-- Reindexar tablas grandes
REINDEX TABLE public.ades_codigos_postales;
REINDEX TABLE public.ades_asistencias;
REINDEX TABLE public.ades_calificaciones_periodo;
```

---

## ✅ TABLA DE CHECKLIST

- [ ] Ejecutar análisis de índices (DONE)
- [ ] Crear script de eliminación de índices no usados
- [ ] Validar FK sin índices en aplicación
- [ ] Crear índices compuestos recomendados
- [ ] Ejecutar VACUUM ANALYZE
- [ ] Medir mejora de performance (EXPLAIN ANALYZE)
- [ ] Documentar cambios en Git
- [ ] Validar en producción

---

## 📊 TABLAS GRANDES (Candidatas a Particionamiento - SPRINT 4)

| Tabla | Tamaño | Filas Est. | Candidate |
|-------|--------|-----------|-----------|
| ades_codigos_postales | 197 MB | 158k | Sí (estática) |
| ades_asistencias | 141 MB | 2M+ | Sí (por período) |
| ades_calificaciones_periodo | 84 MB | 500k+ | No (queries complejas) |
| ades_tareas_entregas | 65 MB | 300k+ | No (OLTP) |
| ades_localidades | 46 MB | 10k | No (pequeña) |

---

## 🚀 Next Steps

1. **SPRINT 2 (HOY):** Aplicar cambios de comentarios y generar documentación ✅
2. **SPRINT 3 (PRÓXIMO):** Aplicar índices recomendados + VACUUM/ANALYZE
3. **SPRINT 4 (FUTURO):** Particionamiento de tablas grandes
4. **SPRINT 5 (FUTURO):** Configurar monitoring y alertas

---

**Creado:** 2026-06-16  
**Análisis Realizado:** psql + pg_stat_user_indexes  
**Fuente:** Base de datos ADES producción

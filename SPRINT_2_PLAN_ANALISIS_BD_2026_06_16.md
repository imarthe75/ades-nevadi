# 📊 SPRINT 2 — Plan Exhaustivo: Análisis, Documentación y Optimización de BD

**Fecha:** 2026-06-16  
**Estado Actual:** 145 tablas en schema public, 528 índices, 8 tablas sin comentarios  
**Duración Estimada:** 6-8 horas (análisis exhaustivo)  
**Prioridad:** Alta (documentación + optimización crítica)

---

## 🎯 Objetivos SPRINT 2

### 1. Documentación Exhaustiva de BD
- [ ] Comentarios en TODAS las tablas (145 tablas)
- [ ] Comentarios en TODAS las columnas (2000+ columnas)
- [ ] Comentarios en TODAS las funciones y triggers
- [ ] Documentar índices y constraints

### 2. Diccionario de Datos
- [ ] Generar tabla data dictionary (`db/docs/DATA_DICTIONARY.md`)
- [ ] Schema, tabla, columna, tipo, constraints, descripción
- [ ] Exportable a Excel/CSV

### 3. Diagrama E-R
- [ ] Generar Mermaid E-R diagram (`db/docs/ER_DIAGRAM.mmd`)
- [ ] Identificar relaciones FK
- [ ] Visualizar integridad referencial
- [ ] Exportable a PNG/SVG

### 4. Optimización de BD
- [ ] Análisis de índices faltantes
- [ ] Detección de índices no usados
- [ ] Recomendación de índices en FKs
- [ ] Identificar queries lentas (N+1, missing indexes)
- [ ] Análisis de normalización/denormalización

### 5. Performance Tuning
- [ ] Estadísticas de tablas y columnas (`ANALYZE`)
- [ ] Recomendaciones de vacuum
- [ ] Tuning de query planner (`random_page_cost`, `work_mem`)
- [ ] Mejoras en particionamiento si aplica

### 6. Validación de Integridad
- [ ] Verificar constraints
- [ ] Detectar orfandades (FK sin correspondencia)
- [ ] Validar unicidad de PKs
- [ ] Revisar triggers de auditoría

---

## 📋 Tareas Detalladas por Fase

### FASE 1: Análisis de Esquema (1-2 horas)

#### 1.1 Inventario Completo
```sql
-- Ejecutar contra BD para generar lista:
SELECT 
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
  (SELECT COUNT(*) FROM information_schema.columns 
   WHERE table_schema = schemaname AND table_name = tablename) as col_count
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

**Entregar:** `db/analysis/TABLE_INVENTORY_2026_06_16.csv`

#### 1.2 Mapeo de Relaciones
```sql
-- Identificar todas las FKs:
SELECT 
  constraint_name,
  table_name,
  column_name,
  referenced_table_name,
  referenced_column_name
FROM information_schema.referential_constraints
WHERE table_schema = 'public';
```

**Entregar:** `db/analysis/FOREIGN_KEYS_2026_06_16.json`

#### 1.3 Índices Actuales
```sql
-- Analizar índices:
SELECT 
  schemaname,
  tablename,
  indexname,
  indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;
```

**Entregar:** `db/analysis/INDEXES_2026_06_16.csv`

---

### FASE 2: Generación de Comentarios (2-3 horas)

#### 2.1 Template de Comentarios SQL
```sql
-- Script: db/migrations/XXX_add_all_comments.sql

COMMENT ON SCHEMA public IS 'ADES — Sistema de Administración Escolar';

-- Tablas academicas
COMMENT ON TABLE public.ades_estudiantes IS 'Registro de estudiantes inscritos en el sistema';
COMMENT ON COLUMN public.ades_estudiantes.id IS 'UUID único del estudiante (PK)';
COMMENT ON COLUMN public.ades_estudiantes.numero_control IS 'Número de control único por plantel';
-- ... (para cada tabla y columna)
```

**Estrategia:**
1. Leer descripción de cada tabla desde `PROGRESS.md` + análisis
2. Generar COMMENT ON para 145 tablas
3. Generar COMMENT ON para 2000+ columnas
4. Generar COMMENT ON para funciones y triggers
5. Script idempotente (IF EXISTS checks)

**Entregar:** `db/migrations/XXX_add_all_comments.sql` (~2000 líneas)

#### 2.2 Categorización de Tablas
```markdown
## Schemas de Auditoría
- ades_audit_log
- ades_audit_log_detalles
- ...

## Schema Académico
- ades_estudiantes
- ades_profesores
- ades_asistencias
- ades_calificaciones
- ...

## Schema Operativo
- ades_horarios
- ades_aulas
- ...

## Schema BI
- ades_bi_dimDate
- ades_bi_factCalificaciones
- ...
```

**Entregar:** `db/docs/TABLE_CATEGORIES.md`

---

### FASE 3: Diccionario de Datos (1-2 horas)

#### 3.1 Generar Data Dictionary
```python
# Script: scripts/generate_data_dictionary.py

import psycopg2
import pandas as pd
from datetime import datetime

# Conectar BD
conn = psycopg2.connect(...)

# Query todas las columnas
query = """
SELECT 
  table_schema,
  table_name,
  column_name,
  data_type,
  is_nullable,
  column_default,
  col_description(table_schema||'.'||table_name, ordinal_position) as description
FROM information_schema.columns
WHERE table_schema IN ('public', 'portal', 'auditoria', 'ades_bi')
ORDER BY table_schema, table_name, ordinal_position;
"""

df = pd.read_sql(query, conn)

# Exportar formatos múltiples
df.to_csv('db/docs/DATA_DICTIONARY.csv', index=False)
df.to_excel('db/docs/DATA_DICTIONARY.xlsx', index=False)

# Generar Markdown
with open('db/docs/DATA_DICTIONARY.md', 'w') as f:
    f.write('# Data Dictionary — ADES BD\n\n')
    for schema in df['table_schema'].unique():
        f.write(f'## Schema: {schema}\n\n')
        schema_df = df[df['table_schema'] == schema]
        for table in schema_df['table_name'].unique():
            f.write(f'### {table}\n\n')
            table_df = schema_df[schema_df['table_name'] == table]
            f.write(table_df.to_markdown(index=False))
            f.write('\n\n')
```

**Entregar:**
- `db/docs/DATA_DICTIONARY.csv`
- `db/docs/DATA_DICTIONARY.xlsx`
- `db/docs/DATA_DICTIONARY.md`

---

### FASE 4: Diagrama E-R (1-2 horas)

#### 4.1 Generar Mermaid E-R
```python
# Script: scripts/generate_er_diagram.py

import psycopg2

conn = psycopg2.connect(...)
cur = conn.cursor()

# Obtener tablas y FKs
cur.execute("""
SELECT 
  kcu1.table_name,
  kcu1.column_name,
  kcu2.table_name as referenced_table,
  kcu2.column_name as referenced_column
FROM information_schema.referential_constraints rco
JOIN information_schema.key_column_usage kcu1 
  ON rco.constraint_name = kcu1.constraint_name
JOIN information_schema.key_column_usage kcu2 
  ON rco.unique_constraint_name = kcu2.constraint_name
WHERE kcu1.table_schema = 'public'
ORDER BY kcu1.table_name;
""")

# Generar Mermaid
mermaid = "erDiagram\n"
for row in cur.fetchall():
    table1, col1, table2, col2 = row
    mermaid += f'  {table1} }}o--|| {table2} : "{col1} → {col2}"\n'

with open('db/docs/ER_DIAGRAM.mmd', 'w') as f:
    f.write(mermaid)
```

**Entregar:** `db/docs/ER_DIAGRAM.mmd` (Mermaid format)

#### 4.2 Convertir a PNG/SVG
```bash
# Usar mmdc (mermaid-cli) o web tool
mmdc -i db/docs/ER_DIAGRAM.mmd -o db/docs/ER_DIAGRAM.png
mmdc -i db/docs/ER_DIAGRAM.mmd -o db/docs/ER_DIAGRAM.svg
```

**Entregar:**
- `db/docs/ER_DIAGRAM.mmd`
- `db/docs/ER_DIAGRAM.png`
- `db/docs/ER_DIAGRAM.svg`

---

### FASE 5: Análisis de Performance (1-2 horas)

#### 5.1 Detección de Índices Faltantes
```sql
-- Detectar columnas en WHERE sin índices
SELECT 
  schemaname,
  tablename,
  attname,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as table_size
FROM pg_stats
WHERE schemaname = 'public'
AND tablename NOT IN (SELECT tablename FROM pg_indexes WHERE schemaname = 'public')
ORDER BY most_common_vals_null DESC
LIMIT 20;

-- Índices no usados (0 scans)
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_scan
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
AND idx_scan = 0
AND indexrelname NOT LIKE '%_pkey'
ORDER BY pg_relation_size(indexrelid) DESC;
```

**Entregar:** `db/analysis/INDEX_RECOMMENDATIONS_2026_06_16.md`

#### 5.2 Análisis de Queries Lentas
```sql
-- Habilitar log_min_duration_statement en postgresql.conf
-- ALTER SYSTEM SET log_min_duration_statement = 1000; -- 1 segundo

-- Ejecutar workload de prueba
-- Analizar pg_stat_statements (si disponible)

SELECT 
  query,
  calls,
  total_time,
  mean_time,
  max_time
FROM pg_stat_statements
WHERE query NOT LIKE '%pg_stat%'
ORDER BY mean_time DESC
LIMIT 20;
```

**Entregar:** `db/analysis/SLOW_QUERIES_2026_06_16.json`

#### 5.3 Recomendaciones de Normalización/Denormalización
```markdown
# Normalización Status

## Tablas bien normalizadas (3NF)
- ades_estudiantes (buena separación de responsabilidades)
- ades_profesores (un poco denormalizada con campos académicos)

## Candidatos a denormalización
- ades_calificaciones (podría cachear promedios)
- ades_asistencias (agregar tabla de estadísticas)

## Recomendaciones
1. Crear tabla ades_calificaciones_promedios (materialized view)
2. Agregar columna denormalizada: ades_estudiantes.promedio_actual
3. Crear índice en (plantel_id, nivel_id) para queries comunes
```

**Entregar:** `db/analysis/NORMALIZATION_ANALYSIS_2026_06_16.md`

---

### FASE 6: Plan de Optimización (1 hora)

#### 6.1 Checklist de Índices
```sql
-- RECOMENDADOS
CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_plantel_id 
  ON public.ades_estudiantes(plantel_id);

CREATE INDEX IF NOT EXISTS idx_ades_asistencias_estudiante_clase 
  ON public.ades_asistencias(estudiante_id, clase_id);

CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_estudiante_periodo 
  ON public.ades_calificaciones(estudiante_id, periodo_evaluacion_id);

-- PARA BUSCAR
CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_numero_control 
  ON public.ades_estudiantes(numero_control);

CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_curp 
  ON public.ades_estudiantes(curp);

-- PARA JOINS FRECUENTES
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_materia_profesor 
  ON public.ades_calificaciones(materia_id, profesor_id);
```

**Entregar:** `db/migrations/XXX_add_recommended_indexes.sql`

#### 6.2 Vacuum y Analyze
```sql
-- Script: db/maintenance/optimize_and_analyze.sql

VACUUM ANALYZE public.ades_estudiantes;
VACUUM ANALYZE public.ades_calificaciones;
-- ... para todas las tablas críticas

-- Reindexar
REINDEX TABLE public.ades_estudiantes;
REINDEX TABLE public.ades_asistencias;
```

**Entregar:** `db/maintenance/optimize_and_analyze.sql`

---

## 📊 Deliverables SPRINT 2

### Documentación (6 archivos)
- [ ] `db/migrations/XXX_add_all_comments.sql` (comentarios 2000+ líneas)
- [ ] `db/docs/TABLE_CATEGORIES.md` (organización)
- [ ] `db/docs/DATA_DICTIONARY.csv` (exportable)
- [ ] `db/docs/DATA_DICTIONARY.xlsx` (ejecutivos)
- [ ] `db/docs/DATA_DICTIONARY.md` (markdown)
- [ ] `db/docs/ER_DIAGRAM.mmd` (Mermaid)

### Análisis (5 archivos)
- [ ] `db/analysis/TABLE_INVENTORY_2026_06_16.csv`
- [ ] `db/analysis/FOREIGN_KEYS_2026_06_16.json`
- [ ] `db/analysis/INDEXES_2026_06_16.csv`
- [ ] `db/analysis/INDEX_RECOMMENDATIONS_2026_06_16.md`
- [ ] `db/analysis/SLOW_QUERIES_2026_06_16.json`

### Optimización (4 archivos)
- [ ] `db/analysis/NORMALIZATION_ANALYSIS_2026_06_16.md`
- [ ] `db/migrations/XXX_add_recommended_indexes.sql`
- [ ] `db/maintenance/optimize_and_analyze.sql`
- [ ] `db/docs/OPTIMIZATION_REPORT_2026_06_16.md`

### Scripts Generadores (3 scripts Python)
- [ ] `scripts/generate_data_dictionary.py`
- [ ] `scripts/generate_er_diagram.py`
- [ ] `scripts/generate_index_recommendations.py`

---

## 🔧 Herramientas Recomendadas

### FASE 1-3: Python
```bash
pip install psycopg2-binary pandas openpyxl sqlalchemy
```

### FASE 4: Mermaid
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i diagram.mmd -o diagram.png
```

### FASE 5: PostgreSQL built-in
- `pg_stat_statements` (extensión)
- `pg_stat_user_indexes`
- `information_schema`

---

## ⏱️ Cronograma Estimado

| Fase | Tareas | Horas | Deadline |
|------|--------|-------|----------|
| 1 | Análisis esquema | 1-2h | 09:00-11:00 |
| 2 | Comentarios | 2-3h | 11:00-14:00 |
| 3 | Data Dictionary | 1-2h | 14:00-16:00 |
| 4 | E-R Diagram | 1-2h | 16:00-18:00 |
| 5 | Performance | 1-2h | 18:00-20:00 |
| 6 | Optimización | 1h | 20:00-21:00 |
| **TOTAL** | | **7-12h** | |

---

## 🎯 Criterios de Éxito

- [ ] 100% de tablas con comentarios descriptivos
- [ ] 100% de columnas críticas documentadas
- [ ] Data Dictionary exportable en 3 formatos
- [ ] E-R Diagram legible (todas las relaciones visibles)
- [ ] 20+ índices recomendados identificados
- [ ] Plan de optimización documentado
- [ ] Scripts ejecutables y probados
- [ ] BD optimizada y VACUUM/ANALYZE ejecutados
- [ ] 0 tablas huérfanas (sin relaciones)
- [ ] Documentación versionada en Git

---

## 💡 Sugerencias Adicionales (Beyond Scope)

### Que SÍ aplica a SPRINT 2
1. **Triggers de auditoría avanzados** — Documentar completamente
2. **Funciones PL/pgSQL** — Comentar y documentar lógica
3. **Vistas materializadas** — Identificar y optimizar
4. **Particionamiento** — Analizar si hay tablas grandes (> 1GB)
5. **Full-text search** — Configurar en campos de búsqueda
6. **Backup/Recovery** — Documentar estrategia

### Que PUEDE ser SPRINT 3
1. **Replicación** — Master/slave setup
2. **Connection pooling** — PgBouncer tuning
3. **Sharding** — Si BD crece > 10GB
4. **Cache layer** — Redis integration
5. **Time-series** — TimescaleDB para telemetría

---

## 🚀 Post-SPRINT 2

**SPRINT 3 Candidatos:**
- Implementar índices recomendados
- Crear materialized views para reportes
- Optimizar query planner settings
- Setup de monitoring y alertas (pg_stat_monitor)

---

**Creado:** 2026-06-16  
**Estado:** Ready for execution  
**Ownership:** BD DBA / Data Engineer  
**Review:** 2026-06-17


# SPRINT 4 — Full-Text Search Analysis

**Fecha:** 2026-06-16  
**Objetivo:** Identificar columnas candidatas para FTS (tsvector + GIN)  

---

## 📊 Análisis de Tablas

### Criterios de Selección
- Columnas de texto searchable (nombre, descripción, etc.)
- Tablas con búsquedas frecuentes en aplicación
- Impacto: +40% en búsquedas, -20-30% tamaño índices parciales

---

## 🎯 Tablas Candidatas

### 1. **ades_personas** (Búsquedas frecuentes)
**Uso:** Buscar estudiantes, profesores, padres por nombre

| Campo | Tipo | Candidato | Razón |
|-------|------|-----------|-------|
| `nombre` | VARCHAR(100) | ✅ YES | Búsqueda común |
| `apellido_paterno` | VARCHAR(100) | ✅ YES | Búsqueda común |
| `apellido_materno` | VARCHAR(100) | ✅ YES | Búsqueda común |
| `curp` | CHAR(18) | ⚠️ MAYBE | Búsqueda exacta (mejor LIKE) |
| `email` | VARCHAR(100) | ⚠️ MAYBE | Búsqueda exacta (índice simple) |

**Estrategia:** 
- Crear columna computed `nombre_completo_tsvector` (trigger)
- Índice GIN: `(nombre || ' ' || apellido_paterno || ' ' || apellido_materno)`
- Partial: `WHERE is_active = TRUE`

---

### 2. **ades_tareas** (Búsquedas de tareas por descripción)
**Uso:** Buscar tareas por nombre/descripción

| Campo | Tipo | Candidato | Razón |
|-------|------|-----------|-------|
| `nombre_tarea` | VARCHAR(200) | ✅ YES | Búsqueda común |
| `descripcion` | TEXT | ✅ YES | Búsqueda en contenido |

**Estrategia:**
- Índice GIN en `to_tsvector('spanish', nombre_tarea || ' ' || COALESCE(descripcion, ''))`
- Partial: `WHERE is_active = TRUE AND fecha_fin >= CURRENT_DATE`

---

### 3. **ades_temas** (Búsquedas curriculares)
**Uso:** Buscar temas de estudio por nombre

| Campo | Tipo | Candidato | Razón |
|-------|------|-----------|-------|
| `nombre_tema` | VARCHAR(200) | ✅ YES | Búsqueda frecuente |

**Estrategia:**
- Índice GIN: `to_tsvector('spanish', nombre_tema)`
- Partial: `WHERE is_active = TRUE`

---

### 4. **ades_materias** (Búsquedas de materias)
**Uso:** Buscar materia por nombre/clave

| Campo | Tipo | Candidato | Razón |
|-------|------|-----------|-------|
| `nombre_materia` | VARCHAR(200) | ✅ YES | Búsqueda común |
| `clave_materia` | VARCHAR(50) | ⚠️ MAYBE | Búsqueda exacta mejor |

**Estrategia:**
- Índice GIN: `to_tsvector('spanish', nombre_materia)`
- Partial: `WHERE is_active = TRUE`

---

## 📏 Análisis de Tamaño (Estimado)

| Tabla | Registros | Índice GIN | Partial | Ahorro |
|-------|-----------|-----------|---------|---------|
| ades_personas | 4,150 | ~2 MB | 1.6 MB | 20% |
| ades_tareas | ~500 | ~1 MB | 0.7 MB | 30% |
| ades_temas | ~2,000 | ~1.5 MB | 1.2 MB | 20% |
| ades_materias | ~150 | ~0.5 MB | 0.4 MB | 20% |
| **TOTAL** | | **~5 MB** | **~3.9 MB** | **-1.1 MB** |

---

## 🔧 Implementación

### Paso 1: Índices tsvector básicos (sin partial)

```sql
-- ades_personas (nombre completo)
CREATE INDEX idx_ades_personas_nombre_tsvector
ON ades_personas USING GIN (
  to_tsvector('spanish', COALESCE(nombre, '') || ' ' || 
              COALESCE(apellido_paterno, '') || ' ' || 
              COALESCE(apellido_materno, ''))
);

-- ades_tareas
CREATE INDEX idx_ades_tareas_tsvector
ON ades_tareas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tarea, '') || ' ' || 
              COALESCE(descripcion, ''))
);

-- ades_temas
CREATE INDEX idx_ades_temas_nombre_tsvector
ON ades_temas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tema, ''))
);

-- ades_materias
CREATE INDEX idx_ades_materias_nombre_tsvector
ON ades_materias USING GIN (
  to_tsvector('spanish', COALESCE(nombre_materia, ''))
);
```

### Paso 2: Índices parciales (WHERE is_active = TRUE)

```sql
-- ades_personas (solo activos)
CREATE INDEX idx_ades_personas_nombre_tsvector_active
ON ades_personas USING GIN (
  to_tsvector('spanish', COALESCE(nombre, '') || ' ' || 
              COALESCE(apellido_paterno, '') || ' ' || 
              COALESCE(apellido_materno, ''))
WHERE is_active = TRUE;

-- ades_tareas (solo activas)
CREATE INDEX idx_ades_tareas_tsvector_active
ON ades_tareas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tarea, '') || ' ' || 
              COALESCE(descripcion, ''))
WHERE is_active = TRUE AND fecha_fin >= CURRENT_DATE;

-- ades_temas (solo activos)
CREATE INDEX idx_ades_temas_nombre_tsvector_active
ON ades_temas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tema, ''))
WHERE is_active = TRUE;

-- ades_materias (solo activas)
CREATE INDEX idx_ades_materias_nombre_tsvector_active
ON ades_materias USING GIN (
  to_tsvector('spanish', COALESCE(nombre_materia, ''))
WHERE is_active = TRUE;
```

### Paso 3: Helper functions para queries

```sql
-- Función para búsqueda en personas
CREATE OR REPLACE FUNCTION buscar_personas(p_query TEXT)
RETURNS TABLE (
  id UUID,
  nombre_completo VARCHAR,
  email VARCHAR,
  tipo_persona VARCHAR
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    p.id,
    p.nombre || ' ' || p.apellido_paterno || ' ' || p.apellido_materno AS nombre_completo,
    p.email,
    'persona'::VARCHAR
  FROM ades_personas p
  WHERE p.is_active = TRUE
    AND to_tsvector('spanish', COALESCE(p.nombre, '') || ' ' || 
                                COALESCE(p.apellido_paterno, '') || ' ' || 
                                COALESCE(p.apellido_materno, ''))
        @@ plainto_tsquery('spanish', p_query)
  ORDER BY ts_rank(
    to_tsvector('spanish', COALESCE(p.nombre, '') || ' ' || 
                            COALESCE(p.apellido_paterno, '') || ' ' || 
                            COALESCE(p.apellido_materno, '')),
    plainto_tsquery('spanish', p_query)
  ) DESC
  LIMIT 100;
END;
$$ LANGUAGE plpgsql STABLE;

-- Función para búsqueda en tareas
CREATE OR REPLACE FUNCTION buscar_tareas(p_query TEXT)
RETURNS TABLE (
  id UUID,
  nombre_tarea VARCHAR,
  descripcion TEXT,
  estado VARCHAR
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    t.id,
    t.nombre_tarea,
    t.descripcion,
    'tarea'::VARCHAR
  FROM ades_tareas t
  WHERE t.is_active = TRUE
    AND to_tsvector('spanish', COALESCE(t.nombre_tarea, '') || ' ' || 
                                COALESCE(t.descripcion, ''))
        @@ plainto_tsquery('spanish', p_query)
  ORDER BY ts_rank(
    to_tsvector('spanish', COALESCE(t.nombre_tarea, '') || ' ' || 
                            COALESCE(t.descripcion, '')),
    plainto_tsquery('spanish', p_query)
  ) DESC
  LIMIT 100;
END;
$$ LANGUAGE plpgsql STABLE;
```

---

## 📊 Performance Esperado

**Query actual (sin FTS):**
```sql
SELECT * FROM ades_personas 
WHERE nombre ILIKE '%juan%' 
   OR apellido_paterno ILIKE '%juan%' 
   OR apellido_materno ILIKE '%juan%';
-- Tiempo: ~500ms (seq scan)
```

**Query con FTS:**
```sql
SELECT * FROM buscar_personas('juan');
-- Tiempo esperado: ~50-100ms (GIN index scan)
-- Mejora: **5-10x más rápido**
```

---

## 🎯 Próximos Pasos

1. ✅ Análisis completado (este documento)
2. ⏳ Crear migración SQL 075_full_text_search.sql
3. ⏳ Crear migración SQL 076_partial_indexes.sql
4. ⏳ Crear funciones de búsqueda en migrations
5. ⏳ Integrar funciones en backend (nuevos endpoints)
6. ⏳ Tests de performance
7. ⏳ Documentar en API OpenAPI


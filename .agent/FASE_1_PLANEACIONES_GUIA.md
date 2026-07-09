# FASE 1: Foundation - Planeaciones Semanales
**Estado:** ✅ COMPLETADO | Fecha: 2026-07-08

---

## Resumen de Cambios

###  Migraciones Aplicadas

#### Migración 116: Schema - Competencias, Aprendizajes y Extensiones
**Archivo:** `db/migrations/116_competencias_aprendizajes_planeacion.sql`

**Cambios:**
1. ✅ Tabla `ades_competencias` (14 columnas)
   - PK: `ref UUID`
   - Índices: nivel, campo_formativo, area_conocimiento
   - Auditoría: `audit_biu` activado
   
2. ✅ Tabla `ades_aprendizajes_esperados` (13 columnas)
   - PK: `ref UUID`
   - FKs: grado_id, materia_id, competencia_id
   - Índices: grado, materia, competencia, grado+materia
   - Auditoría: `audit_biu` activado

3. ✅ Tabla `ades_planeacion_aprendizajes` (12 columnas)
   - Relación muchos-a-muchos: planeación ↔ aprendizajes
   - Tracking de completitud por aprendizaje
   - Auditoría: `audit_biu` activado

4. ✅ ALTER TABLE `ades_planeacion_clases`
   - Columna: `numero_trimestre SMALLINT` (1, 2, 3)
   - Columna: `numero_semana SMALLINT` (1-40)
   - Columna: `modalidad VARCHAR(20)` (PRESENCIAL, VIRTUAL, HIBRIDA)
   - Columna: `competencia_id UUID REFERENCES ades_competencias`
   - Columna: `fecha_fin DATE`
   - Índices: trimestre, semana, modalidad, competencia, grupo+trimestre+semana

5. ✅ View `vw_planeacion_cobertura_semanal`
   - Consulta cobertura: temas planeados vs impartidos
   - Agrupa por: grupo, trimestre, semana, materia
   - Calcula: % cobertura

#### Migración 117: Seed Data - Competencias y Aprendizajes Iniciales
**Archivo:** `db/migrations/117_seed_competencias_aprendizajes.sql`

**Datos Cargados:**
- ✅ 4 competencias (Primaria + Secundaria)
- ✅ 2 aprendizajes esperados (muestra)

**Estructura (ampliable):**
```sql
Competencias:
- PRI.LENGUAJES.1 → Primaria
- PRI.SPC.2 → Primaria
- SEC.LENGUAJES.1 → Secundaria  
- SEC.SPC.2 → Secundaria

Aprendizajes Esperados:
- ES.1.1.1 → Primaria 1° Español
- MAT.1.1.1 → Primaria 1° Matemáticas
- ES.SEC.1.1 → Secundaria 1° Español
- MAT.SEC.1.1 → Secundaria 1° Matemáticas
```

---

## Cómo Ejecutar la Fase 1 en Otros Ambientes

### Dev
```bash
# Aplicar migraciones secuencialmente
docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/116_competencias_aprendizajes_planeacion.sql
docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/117_seed_competencias_aprendizajes.sql

# Verificar
docker compose exec -T postgres psql -U ades_admin -d ades -c \
  "SELECT COUNT(*) FROM ades_competencias; SELECT COUNT(*) FROM ades_aprendizajes_esperados;"
```

### Staging
```bash
# Mismo proceso, usando conexión staging
psql -h staging-db.example.com -U ades_admin -d ades < db/migrations/116_competencias_aprendizajes_planeacion.sql
psql -h staging-db.example.com -U ades_admin -d ades < db/migrations/117_seed_competencias_aprendizajes.sql
```

### Producción
```bash
# IMPORTANTE: Crear backup antes
pg_dump -h prod-db.example.com -U ades_admin ades > backup_fase1_$(date +%Y%m%d).sql

# Aplicar con precaución
psql -h prod-db.example.com -U ades_admin -d ades < db/migrations/116_competencias_aprendizajes_planeacion.sql
psql -h prod-db.example.com -U ades_admin -d ades < db/migrations/117_seed_competencias_aprendizajes.sql
```

---

## Cómo Agregar Más Competencias y Aprendizajes

### Plantilla SQL para Nuevas Competencias

```sql
-- Insertar nueva competencia
INSERT INTO ades_competencias 
(codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 
  'CODIGO.UNICO',           -- Ej: PRI.LENGUAJES.3
  'Nombre de competencia',
  'Descripción detallada',
  ref,                      -- nivel_educativo_id
  'Campo Formativo',        -- Ej: Lenguajes, SPC, DHC, etc.
  orden_numero,             -- Número de orden
  TRUE
FROM ades_niveles_educativos 
WHERE nombre_nivel ILIKE '%Nivel%';  -- Cambiar Nivel según corresponda
```

### Plantilla SQL para Nuevos Aprendizajes Esperados

```sql
-- Insertar aprendizaje esperado
INSERT INTO ades_aprendizajes_esperados
(codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT
  'CODIGO.UNICO',           -- Ej: ES.3.1.1
  g.ref,                    -- Del FROM
  m.ref,                    -- Del FROM
  c.ref,                    -- Del FROM
  'Descripción del aprendizaje',
  orden_numero,
  TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = grado_num
  AND m.nombre_materia ILIKE '%Materia%'
  AND c.codigo = 'COMPETENCIA.CODIGO'
LIMIT 1;
```

### Ejemplo: Agregar Competencias para Primaria 2°

```sql
BEGIN;

-- Competencia: Pensamiento crítico Primaria 2°
INSERT INTO ades_competencias
(codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 'PRI.SPC.1', 'Pensamiento crítico y reflexivo', 
  'Cuestionar, analizar y argumentar ideas', ref, 
  'Saberes y Pensamiento Crítico', 1, TRUE
FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%' LIMIT 1;

-- Aprendizaje esperado: Primaria 2° Español
INSERT INTO ades_aprendizajes_esperados
(codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT 'ES.3.1.1', g.ref, m.ref, c.ref,
  'Analiza estructura de cuentos infantiles', 1, TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = 3  -- Grado 3° (cambiar según necesite)
  AND m.nombre_materia ILIKE '%Español%'
  AND c.codigo = 'PRI.SPC.1'
LIMIT 1;

COMMIT;
```

---

## Consultas Útiles para Verificación

### Ver todas las competencias
```sql
SELECT codigo, nombre, campo_formativo, activo, fecha_creacion
FROM ades_competencias
ORDER BY nivel_educativo_id, campo_formativo, orden;
```

### Ver aprendizajes esperados por grado y materia
```sql
SELECT 
  ag.nombre_grado,
  am.nombre_materia,
  aae.codigo,
  aae.descripcion,
  ac.nombre as competencia
FROM ades_aprendizajes_esperados aae
JOIN ades_grados ag ON aae.grado_id = ag.ref
JOIN ades_materias am ON aae.materia_id = am.ref
JOIN ades_competencias ac ON aae.competencia_id = ac.ref
ORDER BY ag.numero_grado, am.nombre_materia;
```

### Ver cobertura semanal (vista creada en mig 116)
```sql
SELECT * FROM vw_planeacion_cobertura_semanal
WHERE numero_semana = 5 AND numero_trimestre = 1
ORDER BY nombre_grupo, nombre_materia;
```

### Auditoría: verificar triggers activos
```sql
SELECT trigger_name, event_object_table, event_manipulation
FROM information_schema.triggers
WHERE event_object_schema = 'public'
  AND (event_object_table IN ('ades_competencias', 'ades_aprendizajes_esperados', 
       'ades_planeacion_aprendizajes', 'ades_planeacion_clases'))
ORDER BY event_object_table;
```

---

## Estado de Auditoría

✅ Todas las tablas tienen triggers `audit_biu` activos:
- `ades_competencias` → auditoría automática de inserts/updates
- `ades_aprendizajes_esperados` → auditoría automática de inserts/updates
- `ades_planeacion_aprendizajes` → auditoría automática de inserts/updates
- `ades_planeacion_clases` → auditoría EXTENDIDA con nuevos campos

**Verificar auditoría:**
```sql
-- Ver registros auditados
SELECT tabla, COUNT(*) as cambios
FROM auditoria.log_auditoria
WHERE tabla IN ('ades_competencias', 'ades_aprendizajes_esperados')
  AND fecha > NOW() - INTERVAL '24 hours'
GROUP BY tabla;
```

---

## Próximos Pasos: Fase 2 (3 semanas)

1. ✅ Foundation completada
2. ⏳ **UI Profesor:** Selector trimestre/semana + formulario planeación
3. ⏳ **Backend:** Extensiones PlaneacionController + nuevas queries
4. ⏳ **Testing:** Playwright + Spring tests

**Go-live:** 19-26 agosto 2026

---

## Referencia Rápida

| Recurso | Ubicación |
|---------|-----------|
| Migraciones | `/opt/ades/db/migrations/116_*.sql`, `117_*.sql` |
| Documentación | Este archivo: `.agent/FASE_1_PLANEACIONES_GUIA.md` |
| Análisis viabilidad | `.agent/VIABILIDAD_PLANEACIONES_SEMANALES.md` |
| Backend cambios | Próxima: `backend-spring/src/main/java/mx/ades/modules/planeacion/` |
| Frontend cambios | Próxima: `frontend/src/app/features/planeacion-profesor/` |

---

**Versión:** FASE 1 v1.0  
**Fecha:** 2026-07-08  
**Estado:** ✅ Listo para Fase 2


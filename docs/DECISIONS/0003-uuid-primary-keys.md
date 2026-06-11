# ADR 0003: Llaves Primarias UUID en todas las tablas ADES

## Estado
Aprobado ✅ — 2026-06-03

## Contexto

El esquema inicial (001_initial_schema.sql) fue diseñado con `BIGINT GENERATED ALWAYS AS IDENTITY`
como llave primaria en todas las tablas. Esta decisión es adecuada para bases de datos
monolíticas de alta velocidad de inserción secuencial, pero presenta limitaciones
relevantes para el contexto de ADES:

1. **Ambigüedad de ID entre tablas:** Las referencias polimórficas (`entidad_id BIGINT`)
   no pueden distinguir un `id=1` de `ades_planteles` de un `id=1` de `ades_grupos`.
   Con UUID cada ID es globalmente único.

2. **Exposición de volumen:** Los IDs secuenciales exponen información de negocio en la API
   (un atacante puede estimar cuántos alumnos hay). Los UUID evitan esto.

3. **Distribución y sincronización futura:** Si en el futuro ADES necesita sincronización
   entre instancias (multi-plantel distribuido, réplica offline), los IDs numéricos generan
   conflictos de PK. Los UUID se generan sin coordinación central.

4. **Guía de estilo SQL corporativa:** La guía de estilo del proyecto (sección 4.1-B) ya
   recomendaba UUID como opción preferida para sistemas distribuidos/alto volumen. Esta ADR
   formaliza la adopción mandatoria.

5. **PostgreSQL 18 + UUIDv7:** PG18 incluye `uuidv7()` nativo, que genera UUIDs ordenados
   temporalmente. Esto elimina la fragmentación de índice B-tree (principal desventaja
   histórica de los UUID v4 aleatorios vs. BIGINT secuencial).

## Decisión

1. **Todas las tablas ADES** usarán `id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY`.
   En PG18, migrar a `DEFAULT uuidv7()` cuando esté disponible y confirmado en la instancia.

2. **Todas las columnas FK** que referencian un `id` de otra tabla serán de tipo `UUID`
   (ej. `plantel_id UUID NOT NULL REFERENCES ades_planteles(id)`).

3. **Referencias polimórficas** (`entidad_id`, `entidad_tipo`) también pasan a `UUID`.

4. **La columna `ref`** se conserva como business key estable para SCD2 y exposición
   a sistemas externos, también de tipo UUID.

5. **El esquema de referencia** (`db/migrations/001_initial_schema.sql`) documenta la nueva
   convención. La migración real de la base de datos existente (con datos) requiere un
   script `002_` dedicado con estrategia de reconstrucción aprobada por DBA.

6. **El Skill de Liquibase** (`skills/database-liquibase-postgresql/SKILL.md`) y el
   contexto del agente (`.agent/CONTEXT.md`) reflejan esta convención mandatoria.

## Consecuencias

### Positivas
- IDs globalmente únicos — sin colisiones entre tablas ni entre instancias.
- Referencias polimórficas tipadas correctamente.
- Preparados para `uuidv7()` en PG18 (orden temporal, sin fragmentación de índice).
- API externa no expone volumen de negocio.
- Coherencia total entre documentación, skill y esquema DDL.

### Negativas / Riesgos Mitigados
- **UUIDs en índices:** Mayor tamaño de índice vs. BIGINT. Mitigado con UUIDv7 (orden temporal).
- **Migración de BD existente:** Las tablas ya creadas con BIGINT requieren un script
  `002_uuid_migration.sql` complejo (drop FKs → cambio de tipo → recrear FKs).
  Este script se genera como tarea separada y requiere aprobación explícita antes de ejecutar.
- **Seeds:** Los seeds actuales usan referencias numéricas hardcodeadas; deben regenerarse
  usando `gen_random_uuid()` o cargarse con UUIDs explícitos.

## Referencias

- Guía de estilo SQL ADES — Sección 4.1-B: UUID como PK
- Skill: `skills/database-liquibase-postgresql/SKILL.md` — "Data types and modeling"
- Contexto: `.agent/CONTEXT.md` — "Convenciones de Base de Datos"
- PostgreSQL 18 release notes: función `uuidv7()` nativa
- ADR 0001: Arquitectura de Memoria Soberana (base del framework)

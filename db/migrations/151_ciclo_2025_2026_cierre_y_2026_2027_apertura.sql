-- =============================================================================
-- Migración 151 — Transición de ciclo escolar 2025-2026 → 2026-2027
-- =============================================================================
-- Instituto Nevadi / ADES — 2026-07-17
--
-- Contexto: el ciclo vigente al momento de esta migración estaba sembrado con
-- el nombre "2026-2027" (SEP) / "26B"+"27A" (UAEMEX Preparatoria), pero por
-- fecha real del calendario escolar (instrucción explícita del usuario:
-- "ahorita terminó ciclo 25-26 y va a iniciar 26-27") ese ciclo es en realidad
-- el que está cerrando. Esta migración:
--   1. Renombra y ajusta -1 año las fechas del ciclo vigente actual a su
--      nombre correcto "2025-2026" (SEP) / "25B"+"26A" (Preparatoria), lo
--      marca estado=CERRADO (es_vigente se apaga en el paso 2, vía
--      cerrar_ciclo_y_promover(), no aquí — esa función exige que el ciclo
--      origen siga es_vigente=TRUE al momento de invocarla desde la API).
--   2. Crea el ciclo entrante "2026-2027" (SEP) / "26B"+"27A" (Preparatoria)
--      reutilizando las fechas reales que tenía el ciclo recién renombrado.
--   3. Clona la estructura de grupos (vacíos) al ciclo entrante — requisito
--      de cerrar_ciclo_y_promover(), que busca "grupo homónimo" (mismo
--      nombre) en el grado siguiente dentro del ciclo destino; sin esto,
--      cada alumno promovido cae en ades_promociones_pendientes.
--
-- La promoción efectiva de los 2,028 alumnos (crear ades_inscripciones en el
-- ciclo entrante + cerrar el ciclo saliente) se ejecuta DESPUÉS de esta
-- migración, vía POST /api/v1/reinscripcion/{destino}/validar-masivo y
-- /aprobar-masivo (ADMIN_GLOBAL) — no aquí, para pasar por
-- ades_reinscripcion_ciclo y su máquina de estados real, no un UPDATE crudo.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. SEP (Primaria, Secundaria) — renombrar el ciclo vigente a "2025-2026"
--    en 3 pasos: el trigger trg_ciclo_sistema_vigente (mig. 083) exige que
--    TODO ciclo vigente del mismo sistema (SEP cubre Primaria+Secundaria)
--    comparta el mismo nombre_ciclo en TODO momento — un UPDATE de 2 filas
--    que renombra ambas "a la vez" dispara el trigger fila por fila, y la
--    fila procesada primero ve a la otra todavía con el nombre viejo
--    (conflicto falso). Se apaga es_vigente primero (el trigger no valida
--    nada si es_vigente=FALSE), se renombra, y se vuelve a encender.
-- -----------------------------------------------------------------------------
UPDATE ades_ciclos_escolares c
SET es_vigente = FALSE
FROM ades_niveles_educativos n
WHERE c.nivel_educativo_id = n.id
  AND n.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
  AND c.nombre_ciclo = '2026-2027'
  AND c.es_vigente = TRUE;

UPDATE ades_ciclos_escolares c
SET nombre_ciclo = '2025-2026',
    fecha_inicio = fecha_inicio - INTERVAL '1 year',
    fecha_fin    = fecha_fin - INTERVAL '1 year',
    estado       = 'CERRADO'
FROM ades_niveles_educativos n
WHERE c.nivel_educativo_id = n.id
  AND n.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
  AND c.nombre_ciclo = '2026-2027';

UPDATE ades_ciclos_escolares c
SET es_vigente = TRUE
FROM ades_niveles_educativos n
WHERE c.nivel_educativo_id = n.id
  AND n.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
  AND c.nombre_ciclo = '2025-2026';

-- 2. Crear el ciclo entrante "2026-2027" (fechas reales: las que tenía el
--    ciclo recién renombrado antes del ajuste de arriba).
INSERT INTO ades_ciclos_escolares
    (nombre_ciclo, nivel_educativo_id, fecha_inicio, fecha_fin, tipo_ciclo,
     es_vigente, estado, sistema_educativo)
SELECT '2026-2027', n.id, '2026-08-24'::date, '2027-07-09'::date, 'ANUAL',
       FALSE, 'ACTIVO', 'SEP'
FROM ades_niveles_educativos n
WHERE n.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA');

-- -----------------------------------------------------------------------------
-- 3. UAEMEX Preparatoria (semestral) — mismo tratamiento
--    26B (vigente, Aug2026-Ene2027) → 25B (cerrado)
--    27A (no vigente, Feb2027-Jul2027) → 26A (cerrado)
-- -----------------------------------------------------------------------------
UPDATE ades_ciclos_escolares c
SET nombre_ciclo = '25B',
    fecha_inicio = fecha_inicio - INTERVAL '1 year',
    fecha_fin    = fecha_fin - INTERVAL '1 year',
    estado       = 'CERRADO'
FROM ades_niveles_educativos n
WHERE c.nivel_educativo_id = n.id
  AND n.nombre_nivel = 'PREPARATORIA'
  AND c.nombre_ciclo = '26B'
  AND c.es_vigente = TRUE;

UPDATE ades_ciclos_escolares c
SET nombre_ciclo = '26A',
    fecha_inicio = fecha_inicio - INTERVAL '1 year',
    fecha_fin    = fecha_fin - INTERVAL '1 year',
    estado       = 'CERRADO',
    es_vigente   = FALSE
FROM ades_niveles_educativos n
WHERE c.nivel_educativo_id = n.id
  AND n.nombre_nivel = 'PREPARATORIA'
  AND c.nombre_ciclo = '27A';

-- Crear los semestres entrantes 26B (primero) y 27A (segundo, placeholder
-- futuro — mismo patrón que ya existía: un semestre siguiente pre-creado).
INSERT INTO ades_ciclos_escolares
    (nombre_ciclo, nivel_educativo_id, fecha_inicio, fecha_fin, tipo_ciclo,
     es_vigente, estado, sistema_educativo)
SELECT '26B', n.id, '2026-08-04'::date, '2027-01-30'::date, 'SEMESTRAL',
       FALSE, 'ACTIVO', 'UAEMEX'
FROM ades_niveles_educativos n WHERE n.nombre_nivel = 'PREPARATORIA'
UNION ALL
SELECT '27A', n.id, '2027-02-01'::date, '2027-07-09'::date, 'SEMESTRAL',
       FALSE, 'ACTIVO', 'UAEMEX'
FROM ades_niveles_educativos n WHERE n.nombre_nivel = 'PREPARATORIA';

-- -----------------------------------------------------------------------------
-- 4. Clonar estructura de grupos (vacíos) al ciclo entrante — requisito de
--    cerrar_ciclo_y_promover() (migración 009), que busca grupo homónimo
--    (mismo nombre_grupo) en el grado siguiente dentro del ciclo destino.
-- -----------------------------------------------------------------------------

-- 4a. SEP: Primaria y Secundaria — clona los 6+3 grados × grupos × 3 planteles
INSERT INTO ades_grupos
    (nombre_grupo, grado_id, ciclo_escolar_id, profesor_titular_id,
     capacidad_maxima, turno, estatus_id, aula_id)
SELECT g.nombre_grupo, g.grado_id, cn.id, g.profesor_titular_id,
       g.capacidad_maxima, g.turno, g.estatus_id, g.aula_id
FROM ades_grupos g
JOIN ades_ciclos_escolares co ON co.id = g.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = co.nivel_educativo_id
JOIN ades_ciclos_escolares cn ON cn.nivel_educativo_id = co.nivel_educativo_id
                              AND cn.nombre_ciclo = '2026-2027'
WHERE co.nombre_ciclo = '2025-2026'
  AND n.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
  AND g.is_active = TRUE;

-- 4b. Preparatoria — solo hacia el nuevo "26B" (primer semestre del año
--     entrante). "27A" se poblará de grupos en la siguiente transición
--     semestral (26B→27A), no en esta migración.
INSERT INTO ades_grupos
    (nombre_grupo, grado_id, ciclo_escolar_id, profesor_titular_id,
     capacidad_maxima, turno, estatus_id, aula_id)
SELECT g.nombre_grupo, g.grado_id, cn.id, g.profesor_titular_id,
       g.capacidad_maxima, g.turno, g.estatus_id, g.aula_id
FROM ades_grupos g
JOIN ades_ciclos_escolares co ON co.id = g.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = co.nivel_educativo_id
JOIN ades_ciclos_escolares cn ON cn.nivel_educativo_id = co.nivel_educativo_id
                              AND cn.nombre_ciclo = '26B'
WHERE co.nombre_ciclo = '25B'
  AND n.nombre_nivel = 'PREPARATORIA'
  AND g.is_active = TRUE;

COMMIT;

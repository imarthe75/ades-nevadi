-- 163_seed_reglas_horario_muestra_sec_prepa.sql
-- Extiende el seed de reglas de horario "de muestra" (mig 161, que solo cubría
-- Primaria) a SECUNDARIA y PREPARATORIA, para que el panel de Reglas del módulo
-- de Horarios arranque con reglas reales en esos niveles también (antes salía
-- vacío al pararse en Secundaria/Prepa, lo que parecía un bug de "no cargan").
--
-- Reglas por nivel × ciclo vigente × cada plantel que ofrece el nivel. Los tipos
-- usados (dias_permitidos, ventana_horaria, dias_no_consecutivos) están en el
-- catálogo ades_horario_tipo_regla y los interpreta HorarioConstraintProvider.
--
-- CRÍTICO: 'materia' en params debe calzar EXACTAMENTE con ades_materias.nombre_materia
-- del nivel correspondiente (el ConstraintProvider compara contra lesson.materiaNombre).
-- Los nombres de abajo se verificaron contra la BD viva (2026-07-22).
--
-- Idempotente: el WHERE NOT EXISTS evita duplicar si la migración se re-corre.
-- Las columnas de auditoría (ref/row_version/timestamps/usuario) las gestiona el
-- trigger ades_horario_regla_audit_biu; aquí solo se dan las columnas de negocio.

BEGIN;

-- ══════════════════════════════════ SECUNDARIA ══════════════════════════════════
WITH ciclo AS (
    SELECT c.id AS ciclo_id, c.nivel_educativo_id AS nivel_id
    FROM ades_ciclos_escolares c
    JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
    WHERE n.nombre_nivel = 'SECUNDARIA' AND c.es_vigente AND c.is_active
    LIMIT 1
),
planteles AS (
    SELECT DISTINCT gr.plantel_id
    FROM ades_grados gr
    JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
    WHERE n.nombre_nivel = 'SECUNDARIA'
),
reglas(tipo, dura, peso, params, descripcion) AS (
    VALUES
    -- Informática/Tecnología: solo miércoles y jueves (aula de cómputo compartida)
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Informática y Tecnología Digital","dias":[3,4]}'::jsonb,
     'Informática y Tecnología Digital solo miércoles y jueves'),
    -- Proyectos: solo en la tarde (desde las 12:00)
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Proyectos","modo":"despues_de","hora":"12:00"}'::jsonb,
     'Proyectos solo por la tarde (a partir de las 12:00)'),
    -- TLEC (Taller de Lectura, Escritura y Cultura): solo por la mañana
    ('ventana_horaria', TRUE, 100,
     '{"materia":"TLEC","modo":"antes_de","hora":"12:00"}'::jsonb,
     'TLEC solo por la mañana (antes de las 12:00)'),
    -- Maker (taller de fabricación): de lunes a jueves (sin viernes)
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Maker","dias":[1,2,3,4]}'::jsonb,
     'Maker solo de lunes a jueves (sin viernes)')
)
INSERT INTO ades_horario_regla
    (plantel_id, ciclo_escolar_id, nivel_educativo_id, tipo, dura, peso, activa, params, descripcion, is_active)
SELECT p.plantel_id, ciclo.ciclo_id, ciclo.nivel_id, r.tipo, r.dura, r.peso, TRUE, r.params, r.descripcion, TRUE
FROM planteles p
CROSS JOIN ciclo
CROSS JOIN reglas r
WHERE NOT EXISTS (
    SELECT 1 FROM ades_horario_regla e
    WHERE e.plantel_id = p.plantel_id
      AND e.ciclo_escolar_id = ciclo.ciclo_id
      AND e.tipo = r.tipo
      AND e.descripcion = r.descripcion
);

-- ═════════════════════════════════ PREPARATORIA ═════════════════════════════════
WITH ciclo AS (
    SELECT c.id AS ciclo_id, c.nivel_educativo_id AS nivel_id
    FROM ades_ciclos_escolares c
    JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
    WHERE n.nombre_nivel = 'PREPARATORIA' AND c.es_vigente AND c.is_active
    LIMIT 1
),
planteles AS (
    SELECT DISTINCT gr.plantel_id
    FROM ades_grados gr
    JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
    WHERE n.nombre_nivel = 'PREPARATORIA'
),
reglas(tipo, dura, peso, params, descripcion) AS (
    VALUES
    -- Educación Física I: solo lunes a jueves (sin viernes)
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Educación Física I","dias":[1,2,3,4]}'::jsonb,
     'Educación Física I solo de lunes a jueves (sin viernes)'),
    -- Educación Física I: no en días consecutivos para el mismo grupo
    ('dias_no_consecutivos', TRUE, 100,
     '{"materia":"Educación Física I"}'::jsonb,
     'Educación Física I no puede caer en días consecutivos'),
    -- Matemáticas I: solo por la mañana (materia dura, mejor rendimiento matutino)
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Matemáticas I","modo":"antes_de","hora":"12:00"}'::jsonb,
     'Matemáticas I solo por la mañana (antes de las 12:00)'),
    -- Informática I: solo miércoles y jueves (aula de cómputo compartida)
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Informática I","dias":[3,4]}'::jsonb,
     'Informática I solo miércoles y jueves'),
    -- Orientación Vocacional I: solo en la tarde (desde las 12:00)
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Orientación Vocacional I","modo":"despues_de","hora":"12:00"}'::jsonb,
     'Orientación Vocacional I solo por la tarde (a partir de las 12:00)')
)
INSERT INTO ades_horario_regla
    (plantel_id, ciclo_escolar_id, nivel_educativo_id, tipo, dura, peso, activa, params, descripcion, is_active)
SELECT p.plantel_id, ciclo.ciclo_id, ciclo.nivel_id, r.tipo, r.dura, r.peso, TRUE, r.params, r.descripcion, TRUE
FROM planteles p
CROSS JOIN ciclo
CROSS JOIN reglas r
WHERE NOT EXISTS (
    SELECT 1 FROM ades_horario_regla e
    WHERE e.plantel_id = p.plantel_id
      AND e.ciclo_escolar_id = ciclo.ciclo_id
      AND e.tipo = r.tipo
      AND e.descripcion = r.descripcion
);

COMMIT;

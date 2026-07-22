-- 164_seed_reglas_horario_ixtapan_prepa.sql
-- Asegura que todos los planteles (incluyendo Ixtapan de la Sal) tengan reglas muestra
-- de horario para PREPARATORIA en sus ciclos activos correspondientes.

BEGIN;

-- ═════════════════════════════════ PREPARATORIA ═════════════════════════════════
WITH ciclo AS (
    SELECT c.id AS ciclo_id, c.nivel_educativo_id AS nivel_id
    FROM ades_ciclos_escolares c
    JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
    WHERE n.nombre_nivel = 'PREPARATORIA' AND c.es_vigente AND c.is_active
    LIMIT 1
),
planteles AS (
    SELECT id AS plantel_id FROM ades_planteles WHERE is_active = TRUE
),
reglas(tipo, dura, peso, params, descripcion) AS (
    VALUES
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Educación Física I","dias":[1,2,3,4]}'::jsonb,
     'Educación Física I solo de lunes a jueves (sin viernes)'),
    ('dias_no_consecutivos', TRUE, 100,
     '{"materia":"Educación Física I"}'::jsonb,
     'Educación Física I no puede caer en días consecutivos'),
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Matemáticas I","modo":"antes_de","hora":"12:00"}'::jsonb,
     'Matemáticas I solo por la mañana (antes de las 12:00)'),
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Informática I","dias":[3,4]}'::jsonb,
     'Informática I solo miércoles y jueves'),
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

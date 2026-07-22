-- 161_seed_reglas_horario_muestra_primaria.sql
-- Siembra las reglas de horario "de muestra" para Primaria (documentadas en
-- docs/horarios-integracion/PROMPT-motor-horarios.md §7 — caso Primaria Nevadi),
-- para que el módulo de Horarios arranque mostrando reglas reales que el motor
-- Timefold sí interpreta (tipos de mx.ades...config.TipoReglaHorario), en vez de
-- una lista vacía.
--
-- Alcance: los 3 planteles que ofrecen Primaria (Metepec, Tenancingo, Ixtapan de
-- la Sal) × ciclo Primaria vigente. Las reglas son institucionales por plantel.
--
-- Idempotente: el WHERE NOT EXISTS evita duplicar si la migración se re-corre.
-- Las columnas de auditoría (ref/row_version/timestamps/usuario) las gestiona el
-- trigger ades_horario_regla_audit_biu; aquí solo se dan las columnas de negocio.
-- 'materia' en params debe calzar EXACTAMENTE con ades_materias.nombre_materia,
-- porque el ConstraintProvider compara contra lesson.materiaNombre.

BEGIN;

WITH ciclo AS (
    SELECT c.id AS ciclo_id, c.nivel_educativo_id AS nivel_id
    FROM ades_ciclos_escolares c
    JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
    WHERE n.nombre_nivel = 'PRIMARIA' AND c.es_vigente AND c.is_active
    LIMIT 1
),
planteles AS (
    SELECT DISTINCT gr.plantel_id
    FROM ades_grados gr
    JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
    WHERE n.nombre_nivel = 'PRIMARIA'
),
reglas(tipo, dura, peso, params, descripcion) AS (
    VALUES
    -- Educación Física: solo lunes a jueves (sin viernes)
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Educación Física","dias":[1,2,3,4]}'::jsonb,
     'Educación Física solo de lunes a jueves (sin viernes)'),
    -- Educación Física: no en días consecutivos para el mismo grupo
    ('dias_no_consecutivos', TRUE, 100,
     '{"materia":"Educación Física"}'::jsonb,
     'Educación Física no puede caer en días consecutivos'),
    -- Computación: solo miércoles y jueves
    ('dias_permitidos', TRUE, 100,
     '{"materia":"Informática y Pensamiento Computacional","dias":[3,4]}'::jsonb,
     'Informática y Pensamiento Computacional solo miércoles y jueves'),
    -- Proyectos: solo en la tarde (desde las 12:00)
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Proyectos","modo":"despues_de","hora":"12:00"}'::jsonb,
     'Proyectos solo por la tarde (a partir de las 12:00)'),
    -- Saberes y Pensamiento Científico (matemáticas/ciencias NEM): solo por la mañana
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Saberes y Pensamiento Científico","modo":"antes_de","hora":"12:00"}'::jsonb,
     'Saberes y Pensamiento Científico solo por la mañana (antes de las 12:00)'),
    -- Lenguajes (lecto-escritura NEM): solo por la mañana
    ('ventana_horaria', TRUE, 100,
     '{"materia":"Lenguajes","modo":"antes_de","hora":"12:00"}'::jsonb,
     'Lenguajes solo por la mañana (antes de las 12:00)'),
    -- Fábrica de Lectura: sesión fraccionada (un bloque de 50 min y uno de 30 min)
    ('materia_fraccionada_30min', TRUE, 100,
     '{"materia":"Fábrica de Lectura"}'::jsonb,
     'Fábrica de Lectura en sesiones fraccionadas (50 min + 30 min)')
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

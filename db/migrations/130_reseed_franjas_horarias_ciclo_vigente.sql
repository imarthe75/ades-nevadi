-- =============================================================================
-- ADES — Migración 130: Re-seed de franjas horarias para los ciclos vigentes
--
-- Motivo: la migración 068 sembró franjas horarias con ciclo_escolar_id
-- hardcodeado como literal UUID. Tras la migración de servidor del 2026-07-10
-- los ciclos escolares se regeneraron con IDs nuevos (uuidv7 no determinístico),
-- dejando las 166 franjas existentes huérfanas — apuntan a ciclos que ya no
-- existen. Resultado: el generador de horarios (Timefold) no encuentra ninguna
-- franja aplicable para el ciclo vigente real y "resuelve" instantáneamente con
-- 0 lecciones asignadas (value range vacío), sin generar ningún horario.
--
-- A diferencia de 068, esta resuelve el ciclo vigente dinámicamente por nivel
-- educativo (es_vigente = TRUE), así que sigue funcionando aunque los ciclos
-- se regeneren de nuevo en el futuro.
-- =============================================================================
BEGIN;

-- Limpia solo franjas huérfanas (ciclo_escolar_id que ya no existe) — no toca
-- franjas de ciclos vigentes reales si ya existieran (re-run seguro).
DELETE FROM ades_horario_franjas f
WHERE NOT EXISTS (
    SELECT 1 FROM ades_ciclos_escolares ce WHERE ce.id = f.ciclo_escolar_id
);

-- PRIMARIA y SECUNDARIA: Lunes-Jueves 10 franjas (07:00-16:00 con recesos),
-- Viernes 8 franjas (07:00-14:00). Turno MATUTINO.
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, ce.id, ne.id, d, h.ini::time, h.fin::time, 'MATUTINO', 'mig130'
FROM ades_niveles_educativos ne
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
CROSS JOIN (VALUES (1), (2), (3), (4)) AS days(d)
CROSS JOIN (VALUES
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:50:00', '14:40:00'), ('14:40:00', '15:30:00'),
  ('15:30:00', '16:00:00')
) AS h(ini, fin)
WHERE ne.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
ON CONFLICT DO NOTHING;

INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, ce.id, ne.id, 5, h.ini::time, h.fin::time, 'MATUTINO', 'mig130'
FROM ades_niveles_educativos ne
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
CROSS JOIN (VALUES
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:20:00', '14:00:00')
) AS h(ini, fin)
WHERE ne.nombre_nivel IN ('PRIMARIA', 'SECUNDARIA')
ON CONFLICT DO NOTHING;

-- PREPARATORIA (UAEMEX): Lunes-Viernes 7 franjas de 1h (07:00-14:30).
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, ce.id, ne.id, d, h.ini::time, h.fin::time, 'MATUTINO', 'mig130'
FROM ades_niveles_educativos ne
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS days(d)
CROSS JOIN (VALUES
  ('07:00:00', '08:00:00'), ('08:00:00', '09:00:00'), ('09:00:00', '10:00:00'),
  ('10:30:00', '11:30:00'), ('11:30:00', '12:30:00'), ('12:30:00', '13:30:00'),
  ('13:30:00', '14:30:00')
) AS h(ini, fin)
WHERE ne.nombre_nivel = 'PREPARATORIA'
ON CONFLICT DO NOTHING;

COMMIT;

DO $$
DECLARE v_franjas INT;
BEGIN
    SELECT COUNT(*) INTO v_franjas FROM ades_horario_franjas;
    RAISE NOTICE '=== MIGRACIÓN 130 — Franjas horarias re-sembradas: % ===', v_franjas;
END $$;

BEGIN;

-- 1. Limpiar franjas horarias
DELETE FROM ades_horario_franjas;

-- ==========================================
-- 2. PRIMARIA
-- Ciclo: 2026-2027 (019e8f74-d148-7c7c-94de-f1500e73faed)
-- Nivel: PRIMARIA (019e8f74-d13f-7052-9890-b128df7ea199)
-- ==========================================

-- Lunes a Jueves (dia_semana: 1, 2, 3, 4)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d148-7c7c-94de-f1500e73faed'::uuid, '019e8f74-d13f-7052-9890-b128df7ea199'::uuid, d, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES (1), (2), (3), (4)) AS days(d)
CROSS JOIN (VALUES 
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:50:00', '14:40:00'), ('14:40:00', '15:30:00'),
  ('15:30:00', '16:00:00')
) AS h(ini, fin);

-- Viernes (dia_semana: 5)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d148-7c7c-94de-f1500e73faed'::uuid, '019e8f74-d13f-7052-9890-b128df7ea199'::uuid, 5, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES 
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:20:00', '14:00:00')
) AS h(ini, fin);


-- ==========================================
-- 3. SECUNDARIA
-- Ciclo: 2026-2027 (019e8f74-d149-735b-823a-0f253f33474c)
-- Nivel: SECUNDARIA (019e8f74-d13f-77e5-aeb8-e859b106072c)
-- ==========================================

-- Lunes a Jueves (dia_semana: 1, 2, 3, 4)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, '019e8f74-d13f-77e5-aeb8-e859b106072c'::uuid, d, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES (1), (2), (3), (4)) AS days(d)
CROSS JOIN (VALUES 
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:50:00', '14:40:00'), ('14:40:00', '15:30:00'),
  ('15:30:00', '16:00:00')
) AS h(ini, fin);

-- Viernes (dia_semana: 5)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, '019e8f74-d13f-77e5-aeb8-e859b106072c'::uuid, 5, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES 
  ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
  ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
  ('12:30:00', '13:20:00'), ('13:20:00', '14:00:00')
) AS h(ini, fin);


-- ==========================================
-- 4. PREPARATORIA
-- Nivel: PREPARATORIA (019e8f74-d13f-788e-8ed6-99c4825b22c8)
-- Ciclos: 26B (019e8f74-d149-746d-9e06-a1662deff096) y 27A (019e8f74-d149-741a-af72-6750fcf45ff9)
-- ==========================================

-- Ciclo 26B - Lunes a Viernes (dia_semana: 1, 2, 3, 4, 5)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d149-746d-9e06-a1662deff096'::uuid, '019e8f74-d13f-788e-8ed6-99c4825b22c8'::uuid, d, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES (1), (2), (3), (4), (5)) AS days(d)
CROSS JOIN (VALUES 
  ('07:00:00', '08:00:00'), ('08:00:00', '09:00:00'), ('09:00:00', '10:00:00'),
  ('10:30:00', '11:30:00'), ('11:30:00', '12:30:00'), ('12:30:00', '13:30:00'),
  ('13:30:00', '14:30:00')
) AS h(ini, fin);

-- Ciclo 27A - Lunes a Viernes (dia_semana: 1, 2, 3, 4, 5)
INSERT INTO ades_horario_franjas (plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
SELECT NULL, '019e8f74-d149-741a-af72-6750fcf45ff9'::uuid, '019e8f74-d13f-788e-8ed6-99c4825b22c8'::uuid, d, h.ini::time, h.fin::time, 'MATUTINO', 'setup'
FROM (VALUES (1), (2), (3), (4), (5)) AS days(d)
CROSS JOIN (VALUES 
  ('07:00:00', '08:00:00'), ('08:00:00', '09:00:00'), ('09:00:00', '10:00:00'),
  ('10:30:00', '11:30:00'), ('11:30:00', '12:30:00'), ('12:30:00', '13:30:00'),
  ('13:30:00', '14:30:00')
) AS h(ini, fin);

COMMIT;

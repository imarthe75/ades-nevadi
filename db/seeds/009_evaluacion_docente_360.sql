-- =============================================================
-- Seed 009: Evaluación Docente 360° — Datos de ejemplo correctos
-- Escala 1-5, tipos DIRECTOR/COORDINADOR/PAR/AUTO, status ENVIADA.
-- Reemplaza registros previos (escala 7-10 incorrecta, tipo AUTOEVALUACION).
-- evaluador_id usa siempre IDs válidos de ades_usuarios.
-- =============================================================

BEGIN;

DELETE FROM ades_eval_docente_criterios;
DELETE FROM ades_evaluacion_docente;

-- Helper
CREATE OR REPLACE PROCEDURE _seed_eval360(
  p_prof UUID, p_ciclo UUID, p_evaluador UUID,
  p_tipo TEXT, p_fecha DATE,
  c1 INT, c2 INT, c3 INT, c4 INT, c5 INT, c6 INT, c7 INT
) LANGUAGE plpgsql AS $$
DECLARE v_eid UUID := gen_random_uuid();
BEGIN
  INSERT INTO ades_evaluacion_docente
    (id, profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador,
     estatus, usuario_creacion, usuario_modificacion, fecha_evaluacion)
  VALUES (v_eid, p_prof, p_ciclo, p_evaluador, p_tipo,
          'ENVIADA', 'seed_009', 'seed_009', p_fecha);

  INSERT INTO ades_eval_docente_criterios (evaluacion_id, criterio_id, calificacion) VALUES
    (v_eid,'019e90f9-a43a-723f-b519-7d887386c315'::uuid,c1),  -- Clima aula    (10%)
    (v_eid,'019e90f9-a439-7876-be14-e0b2ae5282c3'::uuid,c2),  -- Puntualidad   (15%)
    (v_eid,'019e90f9-a43a-707e-a14c-84aefa0a2aa6'::uuid,c3),  -- Dominio       (20%)
    (v_eid,'019e90f9-a43a-7208-86f1-da3b88ae43ab'::uuid,c4),  -- Eval. formativa(15%)
    (v_eid,'019e90f9-a43a-7181-8161-c92b726cf35d'::uuid,c5),  -- Planeación    (15%)
    (v_eid,'019e90f9-a43a-71cd-88bf-a849165e6d25'::uuid,c6),  -- Estrategias   (15%)
    (v_eid,'019e90f9-a43a-7277-839a-fb5a8cd8c7a0'::uuid,c7); -- Atenc. riesgo (10%)

  UPDATE ades_evaluacion_docente
  SET calificacion_global = (
    SELECT ROUND(SUM(edc.calificacion::numeric * cr.peso_porcentual) / SUM(cr.peso_porcentual), 2)
    FROM ades_eval_docente_criterios edc
    JOIN ades_criterios_eval_docente cr ON cr.id = edc.criterio_id
    WHERE edc.evaluacion_id = v_eid
  ) WHERE id = v_eid;
END; $$;

-- ────────────────────────────────────────────────────────────
-- Evaluadores (todos son ades_usuarios válidos):
--   admin     = 019e8f74-d289-7a58-a1de-27bfaedbf601
--   coo_me    = 9c0089ed-ae86-4a93-9fa1-d67ba8b72e3d  (Coord. Admin Metepec)
--   coor_me   = df396927-81ca-47bd-ab4b-c9351d1c9a48  (Coord. Acad. Metepec)
--   u_chavez  = c97e655b-7c9e-49c0-b699-1712c38a989c  (Chávez — docente/usuario)
--   u_fierro  = a0e1817f-a11c-40a2-bf76-e7d32a2b54c2  (Fierro — docente/usuario)
--
-- Ciclo SEP 2026-2027: 019e8f74-d148-7c7c-94de-f1500e73faed
--
-- Columnas (escala 1-5):
--   c1=Clima, c2=Puntualidad, c3=Dominio, c4=EvalFormativa,
--   c5=Planeación, c6=Estrategias, c7=AtenciónEnRiesgo
-- ────────────────────────────────────────────────────────────

-- Ciclo SEP Metepec
DO $$ BEGIN
  PERFORM set_config('seed009.ciclo',
    '019e8f74-d148-7c7c-94de-f1500e73faed', TRUE);
END $$;

-- ── Ramos Homero — Buen docente (prom ~4.1) ──────────────────
CALL _seed_eval360('bf620aef-08bd-459d-a2dd-7d7ce0969770','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-15', 4,4,4,4,4,4,4);
CALL _seed_eval360('bf620aef-08bd-459d-a2dd-7d7ce0969770','019e8f74-d148-7c7c-94de-f1500e73faed','9c0089ed-ae86-4a93-9fa1-d67ba8b72e3d','COORDINADOR','2026-04-08', 3,4,4,3,4,3,4);
CALL _seed_eval360('bf620aef-08bd-459d-a2dd-7d7ce0969770','019e8f74-d148-7c7c-94de-f1500e73faed','c97e655b-7c9e-49c0-b699-1712c38a989c','PAR',        '2026-05-02', 4,4,4,4,4,4,4);
CALL _seed_eval360('bf620aef-08bd-459d-a2dd-7d7ce0969770','019e8f74-d148-7c7c-94de-f1500e73faed','5362ee4e-72fa-4794-98c9-744041a1d9cf','AUTO',       '2026-05-15', 4,5,4,4,5,4,5);

-- ── Chávez Francisco — Docente estrella (prom ~4.9) ──────────
CALL _seed_eval360('261239be-ffee-408e-8d4d-929ec425a2b7','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-16', 5,5,5,5,5,5,5);
CALL _seed_eval360('261239be-ffee-408e-8d4d-929ec425a2b7','019e8f74-d148-7c7c-94de-f1500e73faed','df396927-81ca-47bd-ab4b-c9351d1c9a48','COORDINADOR','2026-04-09', 5,5,5,4,5,5,4);
CALL _seed_eval360('261239be-ffee-408e-8d4d-929ec425a2b7','019e8f74-d148-7c7c-94de-f1500e73faed','a0e1817f-a11c-40a2-bf76-e7d32a2b54c2','PAR',        '2026-05-03', 5,5,5,5,5,5,5);
CALL _seed_eval360('261239be-ffee-408e-8d4d-929ec425a2b7','019e8f74-d148-7c7c-94de-f1500e73faed','c97e655b-7c9e-49c0-b699-1712c38a989c','AUTO',       '2026-05-16', 5,5,5,4,5,5,4);

-- ── Gallardo Esperanza — Regular en mejora (prom ~3.5) ───────
CALL _seed_eval360('3dcca360-9002-4028-aa59-77c039b9de73','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-17', 3,4,3,3,4,3,3);
CALL _seed_eval360('3dcca360-9002-4028-aa59-77c039b9de73','019e8f74-d148-7c7c-94de-f1500e73faed','9c0089ed-ae86-4a93-9fa1-d67ba8b72e3d','COORDINADOR','2026-04-10', 4,4,4,4,4,4,4);
CALL _seed_eval360('3dcca360-9002-4028-aa59-77c039b9de73','019e8f74-d148-7c7c-94de-f1500e73faed','5362ee4e-72fa-4794-98c9-744041a1d9cf','PAR',        '2026-05-04', 3,3,3,3,3,3,3);
CALL _seed_eval360('3dcca360-9002-4028-aa59-77c039b9de73','019e8f74-d148-7c7c-94de-f1500e73faed','253b889b-e63b-40f9-a09f-80bb8cb83237','AUTO',       '2026-05-17', 4,4,4,4,4,4,4);

-- ── Yáñez Paola — Excelente (prom ~4.7) ─────────────────────
CALL _seed_eval360('7b044ce4-6e7d-4ee8-bad3-c8d4c465acac','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-18', 5,5,5,4,5,5,5);
CALL _seed_eval360('7b044ce4-6e7d-4ee8-bad3-c8d4c465acac','019e8f74-d148-7c7c-94de-f1500e73faed','df396927-81ca-47bd-ab4b-c9351d1c9a48','COORDINADOR','2026-04-11', 4,5,5,4,5,4,5);
CALL _seed_eval360('7b044ce4-6e7d-4ee8-bad3-c8d4c465acac','019e8f74-d148-7c7c-94de-f1500e73faed','c97e655b-7c9e-49c0-b699-1712c38a989c','PAR',        '2026-05-05', 5,4,5,5,4,5,4);
CALL _seed_eval360('7b044ce4-6e7d-4ee8-bad3-c8d4c465acac','019e8f74-d148-7c7c-94de-f1500e73faed','d25862a1-1d5a-4436-83cb-1d1efc5aa484','AUTO',       '2026-05-18', 4,5,5,4,5,5,4);

-- ── Mancilla Fernando — Promedio (prom ~4.0) ─────────────────
CALL _seed_eval360('0b8b7a42-49d5-4621-900b-027661eac518','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-19', 4,4,4,4,4,4,4);
CALL _seed_eval360('0b8b7a42-49d5-4621-900b-027661eac518','019e8f74-d148-7c7c-94de-f1500e73faed','9c0089ed-ae86-4a93-9fa1-d67ba8b72e3d','COORDINADOR','2026-04-12', 4,4,4,4,4,4,4);
CALL _seed_eval360('0b8b7a42-49d5-4621-900b-027661eac518','019e8f74-d148-7c7c-94de-f1500e73faed','a0e1817f-a11c-40a2-bf76-e7d32a2b54c2','PAR',        '2026-05-06', 3,4,3,3,4,3,3);
CALL _seed_eval360('0b8b7a42-49d5-4621-900b-027661eac518','019e8f74-d148-7c7c-94de-f1500e73faed','94c4fbe7-ccdf-42be-88f9-811beae020b8','AUTO',       '2026-05-19', 5,4,5,4,5,5,4);

-- ── Fierro Camilo — Bueno constante (prom ~4.2) ──────────────
CALL _seed_eval360('8e7b34e3-d07a-4ba2-9ee2-5dae2f643cc7','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-20', 4,4,4,4,4,4,4);
CALL _seed_eval360('8e7b34e3-d07a-4ba2-9ee2-5dae2f643cc7','019e8f74-d148-7c7c-94de-f1500e73faed','df396927-81ca-47bd-ab4b-c9351d1c9a48','COORDINADOR','2026-04-14', 4,4,4,4,4,4,4);
CALL _seed_eval360('8e7b34e3-d07a-4ba2-9ee2-5dae2f643cc7','019e8f74-d148-7c7c-94de-f1500e73faed','5362ee4e-72fa-4794-98c9-744041a1d9cf','PAR',        '2026-05-07', 3,4,3,3,4,3,3);
CALL _seed_eval360('8e7b34e3-d07a-4ba2-9ee2-5dae2f643cc7','019e8f74-d148-7c7c-94de-f1500e73faed','a0e1817f-a11c-40a2-bf76-e7d32a2b54c2','AUTO',       '2026-05-20', 5,4,5,4,5,5,4);

-- ── Quiroz Wendy — Necesita mejora (prom ~2.6) ───────────────
CALL _seed_eval360('c9c9e89a-0031-407a-9232-55af9a6fb80c','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-21', 2,3,2,2,3,2,2);
CALL _seed_eval360('c9c9e89a-0031-407a-9232-55af9a6fb80c','019e8f74-d148-7c7c-94de-f1500e73faed','9c0089ed-ae86-4a93-9fa1-d67ba8b72e3d','COORDINADOR','2026-04-15', 3,4,3,3,4,3,3);
CALL _seed_eval360('c9c9e89a-0031-407a-9232-55af9a6fb80c','019e8f74-d148-7c7c-94de-f1500e73faed','c97e655b-7c9e-49c0-b699-1712c38a989c','PAR',        '2026-05-08', 2,2,2,2,2,2,2);
CALL _seed_eval360('c9c9e89a-0031-407a-9232-55af9a6fb80c','019e8f74-d148-7c7c-94de-f1500e73faed','9a451e17-ecff-4f0f-9a10-4432523660f3','AUTO',       '2026-05-21', 4,4,3,4,4,3,4);

-- ── Quintero Esperanza — Muy buena (prom ~4.5) ───────────────
CALL _seed_eval360('05930383-1e53-48a5-9eca-8b9b23ab9ed4','019e8f74-d148-7c7c-94de-f1500e73faed','019e8f74-d289-7a58-a1de-27bfaedbf601','DIRECTOR',   '2026-03-22', 4,5,5,4,5,4,5);
CALL _seed_eval360('05930383-1e53-48a5-9eca-8b9b23ab9ed4','019e8f74-d148-7c7c-94de-f1500e73faed','df396927-81ca-47bd-ab4b-c9351d1c9a48','COORDINADOR','2026-04-16', 5,4,5,4,5,5,4);
CALL _seed_eval360('05930383-1e53-48a5-9eca-8b9b23ab9ed4','019e8f74-d148-7c7c-94de-f1500e73faed','a0e1817f-a11c-40a2-bf76-e7d32a2b54c2','PAR',        '2026-05-09', 4,4,4,4,5,4,4);
CALL _seed_eval360('05930383-1e53-48a5-9eca-8b9b23ab9ed4','019e8f74-d148-7c7c-94de-f1500e73faed','472a6572-8147-47d5-aae5-c43ff65e46e0','AUTO',       '2026-05-22', 5,5,5,4,5,5,4);

DROP PROCEDURE _seed_eval360;

COMMIT;

-- Resumen de verificación
SELECT
  p.apellido_paterno || ' ' || p.nombre AS docente,
  pl.nombre_plantel,
  COUNT(*) AS evals,
  STRING_AGG(DISTINCT ed.tipo_evaluador, '/' ORDER BY ed.tipo_evaluador) AS tipos,
  ROUND(AVG(ed.calificacion_global), 2) AS prom_global
FROM ades_evaluacion_docente ed
JOIN ades_profesores pr ON pr.id = ed.profesor_id
JOIN ades_personas p ON p.id = pr.persona_id
JOIN ades_planteles pl ON pl.id = pr.plantel_id
GROUP BY p.apellido_paterno, p.nombre, pl.nombre_plantel
ORDER BY prom_global DESC;

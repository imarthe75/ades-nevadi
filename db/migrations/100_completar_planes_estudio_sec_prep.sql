-- =============================================================================
-- Migración 100 — Completar ades_materias_plan para Secundaria y Preparatoria
--
-- Contexto: auditoría QA 2026-07-02 encontró que ades_materias_plan (tabla
-- declarativa "qué materia se imparte en qué grado") está desincronizada de
-- la realidad del gradebook (ades_calificaciones_periodo, que SÍ tiene datos
-- reales y completos para la mayoría de grados/materias). Causa raíz probable:
-- la migración 015 nunca especificó ciclo_escolar_id (NOT NULL sin default)
-- en sus INSERT de asignación, por lo que solo una carga manual posterior
-- (Metepec/1er semestre prep) quedó completa.
--
-- Este script usa como fuente de verdad el propio gradebook: se consultó
-- qué combinaciones (nivel, grado, materia) tienen calificaciones reales y
-- se replica exactamente ese patrón aquí, más los 2 semestres de
-- Preparatoria (5° y 6°) que aún no tienen calificaciones pero sí están
-- definidos en el plan CBU 2024 (migración 015) — quedan documentados como
-- planeados pero sin actividad de gradebook todavía.
--
-- Primaria NO se toca: ya está completa (14 materias × 18 grados, 2808
-- calificaciones por materia, ver mig 044).
--
-- HALLAZGO ADICIONAL: las 11 materias de Secundaria (NEM 4 campos + 3
-- Nevadi + Biología/Física/Química por grado) y las 101 materias de
-- Preparatoria (CBU 2024 completo) que SÍ tienen calificaciones reales en
-- ades_calificaciones_periodo estaban con is_active=FALSE — invisibles
-- para cualquier selector de materia en la UI. Se reactivan aquí antes de
-- poblar ades_materias_plan (que solo considera materias activas).
-- No se tocan las 27 materias "clásicas" de Secundaria (SEC-MAT-1, SEC-ESP,
-- etc.) que están activas pero sin datos — catálogo paralelo sin uso,
-- posible candidato a limpieza futura, fuera del alcance de este fix.
-- =============================================================================

UPDATE ades_materias SET is_active = TRUE
WHERE nivel_educativo_id = (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'PREPARATORIA')
  AND is_active = FALSE;

UPDATE ades_materias SET is_active = TRUE
WHERE clave_materia IN ('SEC-LEN','SEC-SPC','SEC-ENS','SEC-DHC','SEC-PRY',
                        'SEC-BIO','SEC-FIS','SEC-QUI',
                        'NVI-SEC-INF','NVI-SEC-ING','NVI-SEC-ORI')
  AND nivel_educativo_id = (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA')
  AND is_active = FALSE;

-- Duplicado accidental de SEC-PRY (creado 2026-07-01, "Proyectos", 0 calificaciones)
-- vs. el original (creado 2026-06-05, "Proyectos Escolares", 1404 calificaciones
-- reales). Se desactiva el duplicado vacío para no confundir el selector de materias.
UPDATE ades_materias SET is_active = FALSE
WHERE id = '019f1c05-f36b-74c3-942c-d315964feac5' AND clave_materia = 'SEC-PRY';

-- HALLAZGO ADICIONAL #2: para Secundaria, ades_materias_plan YA tenía las 81
-- filas correctas (8 materias uniformes × 9 grados + 1 materia de ciencia ×
-- 3 grados) para el ciclo vigente — pero también estaban is_active=FALSE.
-- Se reactivan antes de intentar insertar (los INSERT de abajo con
-- ON CONFLICT DO NOTHING no habrían creado nada nuevo de todas formas).
UPDATE ades_materias_plan mp
SET is_active = TRUE
FROM ades_materias m, ades_grados gr, ades_niveles_educativos n
WHERE mp.materia_id = m.id AND mp.grado_id = gr.id AND gr.nivel_educativo_id = n.id
  AND n.nombre_nivel = 'SECUNDARIA'
  AND m.clave_materia IN ('SEC-LEN','SEC-SPC','SEC-ENS','SEC-DHC','SEC-PRY',
                          'SEC-BIO','SEC-FIS','SEC-QUI',
                          'NVI-SEC-INF','NVI-SEC-ING','NVI-SEC-ORI')
  AND mp.is_active = FALSE
  AND mp.ciclo_escolar_id = (SELECT ce.id FROM ades_ciclos_escolares ce WHERE ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE);

-- ── SECUNDARIA — NEM 4 campos + 3 materias Nevadi (todos los grados) ──────────
-- + 1 materia de ciencia específica por grado (patrón confirmado en gradebook:
--   1°→Biología, 2°→Física, 3°→Química)

INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id
WHERE m.clave_materia IN ('SEC-LEN','SEC-SPC','SEC-ENS','SEC-DHC','SEC-PRY',
                          'NVI-SEC-INF','NVI-SEC-ING','NVI-SEC-ORI')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 1
WHERE m.clave_materia = 'SEC-BIO' AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 2
WHERE m.clave_materia = 'SEC-FIS' AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 3
WHERE m.clave_materia = 'SEC-QUI' AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- ── PREPARATORIA — CBU 2024 UAEMEX por semestre (todos los planteles) ────────
-- Semestre 1
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 1
WHERE m.clave_materia IN ('CBU-LC1','CBU-ING1','CBU-CD','CBU-PM1','CBU-QUI1',
                          'CBU-HUM1','CBU-CS1','CBU-RAS1','CBU-AFD1',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- Semestre 2
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 2
WHERE m.clave_materia IN ('CBU-LC2','CBU-ING2','CBU-PM2','CBU-FIS1','CBU-HUM2',
                          'CBU-CS2','CBU-DPE','CBU-RAS2','CBU-AFD2',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- Semestre 3
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 3
WHERE m.clave_materia IN ('CBU-LC3','CBU-ING3','CBU-PM3','CBU-BIO1','CBU-HUM3',
                          'CBU-MIT1','CBU-DS','CBU-RAS3','CBU-AFD3',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- Semestre 4
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 4
WHERE m.clave_materia IN ('CBU-LIT','CBU-ING4','CBU-TSM1','CBU-CH1','CBU-QUI2',
                          'CBU-CADS','CBU-MIT2','CBU-OV','CBU-AFD4',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- Semestre 5 (sin calificaciones aún — plan CBU 2024 documentado, pendiente de actividad)
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 5
WHERE m.clave_materia IN ('CBU-TSM2','CBU-CH2','CBU-FIS2','CBU-GEO',
                          'CBU-AEA1','CBU-CP','CBU-OPT1','CBU-OPT2',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

-- Semestre 6 (sin calificaciones aún — plan CBU 2024 documentado, pendiente de actividad)
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, ce.id, m.horas_semana, (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PREPARATORIA'
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = n.id AND ce.es_vigente = TRUE
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 6
WHERE m.clave_materia IN ('CBU-TSM3','CBU-CH3','CBU-BIO2','CBU-AEA2',
                          'CBU-DE','CBU-PSI','CBU-OPT3','CBU-OPT4',
                          'NVI-PREP-EMP','NVI-PREP-HAD')
  AND m.is_active = TRUE
ON CONFLICT ON CONSTRAINT uq_ades_mat_plan DO NOTHING;

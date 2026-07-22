-- 169_franjas_por_plantel_ciclo_vigente.sql
-- Reestructura las franjas horarias del motor (decisión del usuario 2026-07-22):
--   (a) POR PLANTEL en vez de globales (plantel_id = NULL) — cada plantel es un CCT
--       distinto y el modelo de permisos (verificarPlantel) ya scoping por plantel; y
--   (b) ligadas al CICLO VIGENTE de su nivel, no a ciclos viejos (estaban en 25B /
--       2025-2026, y el grid solo funcionaba porque su query caía a "por nivel").
--
-- Se generan copias por-plantel (una por cada plantel que ofrece el nivel) a partir de la
-- plantilla global vieja, con el ciclo vigente correspondiente. Luego se borran las globales.
-- Verificado 2026-07-22: 0 filas de ades_horario_indisponibilidad referencian estas franjas,
-- así que el borrado de las viejas es seguro. Conteo esperado: Prepa 35×2 + Prim 48×3 +
-- Sec 48×3 = 358 franjas por-plantel.

BEGIN;

INSERT INTO ades_horario_franjas
    (plantel_id, nivel_educativo_id, ciclo_escolar_id, dia_semana, hora_inicio, hora_fin, turno, is_active)
SELECT pl.plantel_id, f.nivel_educativo_id, viv.ciclo_id,
       f.dia_semana, f.hora_inicio, f.hora_fin, f.turno, TRUE
FROM ades_horario_franjas f
-- ciclo vigente del nivel de la franja
JOIN LATERAL (
    SELECT c.id AS ciclo_id
    FROM ades_ciclos_escolares c
    WHERE c.nivel_educativo_id = f.nivel_educativo_id AND c.es_vigente AND c.is_active
    LIMIT 1
) viv ON TRUE
-- planteles que ofrecen ese nivel (vía ades_grados)
JOIN LATERAL (
    SELECT DISTINCT g.plantel_id
    FROM ades_grados g
    WHERE g.nivel_educativo_id = f.nivel_educativo_id
) pl ON TRUE
WHERE f.plantel_id IS NULL;               -- solo a partir de las globales viejas

-- Borrar las franjas globales viejas (ya reemplazadas por las per-plantel del ciclo vigente).
DELETE FROM ades_horario_franjas WHERE plantel_id IS NULL;

COMMIT;

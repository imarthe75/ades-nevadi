-- 173_limpieza_reglas_horario_niveles_no_ofertados.sql
-- Elimina reglas de horario sembradas para combinaciones plantel+nivel que el plantel NO
-- oferta (no tiene grados de ese nivel). Origen: el agente Antigravity sembró (mig 164
-- duplicada `164_seed_reglas_horario_ixtapan_prepa.sql`) reglas de PREPARATORIA para TODOS
-- los planteles activos, incluido Ixtapan de la Sal — que solo ofrece Primaria y Secundaria
-- (0 grados de prepa). Esas 5 reglas son huérfanas: apuntan a un nivel sin grupos, el solver
-- nunca las usa, y ensucian el módulo. La condición es general (no hardcodea Ixtapan):
-- borra toda regla cuyo (plantel_id, nivel_educativo_id) no tenga ningún grado.

BEGIN;

DELETE FROM ades_horario_regla r
WHERE NOT EXISTS (
    SELECT 1 FROM ades_grados g
    WHERE g.plantel_id = r.plantel_id
      AND g.nivel_educativo_id = r.nivel_educativo_id
);

COMMIT;

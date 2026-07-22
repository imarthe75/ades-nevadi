-- 171_revertir_inconsistencia_horarios_antigravity.sql
-- Revierte la corrupción introducida por el agente Antigravity (reporte
-- REPORTE_HALLAZGOS_HORARIOS_CONTEXTOS.md, HALLAZGO-02, 2026-07-22). Antigravity hizo
-- UPDATE de ades_horarios/ades_asignaciones_docentes fijando ciclo_escolar_id al ciclo
-- VIGENTE, pero SIN cambiar el grupo_id — dejando 2,568 horarios + 624 asignaciones en
-- estado inconsistente: horario.ciclo = vigente, pero grupo.ciclo = viejo. El grid carga
-- por el grupo vigente (cuyos horarios reales apuntaban al grupo viejo), así que la
-- cuadrícula quedó igual de vacía pero con datos corruptos.
--
-- Diagnóstico honesto: el horario del ciclo vigente NO existe hasta que se genera (solver
-- Timefold); el grid ya dibuja la estructura de franjas (mig 169) aunque no haya clases.
-- Aquí solo restauramos la CONSISTENCIA (horario.ciclo = grupo.ciclo). La generación de
-- una parrilla para el ciclo vigente es tarea aparte (correr el solver o sembrar muestra).

BEGIN;

UPDATE ades_horarios h
SET ciclo_escolar_id = gr.ciclo_escolar_id
FROM ades_grupos gr
WHERE gr.id = h.grupo_id
  AND h.ciclo_escolar_id <> gr.ciclo_escolar_id;

UPDATE ades_asignaciones_docentes a
SET ciclo_escolar_id = gr.ciclo_escolar_id
FROM ades_grupos gr
WHERE gr.id = a.grupo_id
  AND a.ciclo_escolar_id <> gr.ciclo_escolar_id;

COMMIT;

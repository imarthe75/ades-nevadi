-- =============================================================================
-- 133_check_constraints_calificaciones_tareas_asistencias.sql
-- Auditoría externa 2026-07-14 (6 CU MVP) detectó falta de CHECK constraints en
-- calificaciones/tareas/entregas/asistencias, permitiendo valores fuera de rango
-- o inválidos a nivel de fila (defensa en profundidad detrás de la validación de
-- Spring Boot). Verificado en vivo contra datos reales antes de aplicar:
--   - ades_calificaciones_tareas / ades_calificaciones_evaluaciones / ades_tareas:
--     0 filas violarían el CHECK propuesto por la auditoría.
--   - ades_tareas_entregas.estatus_entrega: la auditoría proponía
--     IN ('PENDIENTE','ENTREGADO','CALIFICADO','TARDE'), valores que NO
--     corresponden al dominio real (ver EstatusEntrega.java: PENDIENTE,
--     ENTREGADA, CALIFICADA, EXCUSA). Con los valores de la auditoría, este
--     CHECK habría reventado inmediatamente 106,130 filas existentes con
--     estatus_entrega = 'CALIFICADA'. Corregido aquí a los valores reales.
--   - ades_asistencias.estatus_asistencia: 0 filas actualmente (tabla vacía),
--     valores de la auditoría coinciden con el dominio ya documentado en
--     001_initial_schema.sql.
-- =============================================================================

BEGIN;

ALTER TABLE ades_calificaciones_tareas
    ADD CONSTRAINT chk_calificacion_rango_tareas
    CHECK (calificacion >= 0 AND calificacion <= 10);

ALTER TABLE ades_calificaciones_evaluaciones
    ADD CONSTRAINT chk_calificacion_rango_evaluaciones
    CHECK (calificacion >= 0 AND calificacion <= 10);

ALTER TABLE ades_tareas
    ADD CONSTRAINT chk_fechas_tarea
    CHECK (fecha_entrega >= fecha_asignacion);

-- Valores reales del dominio (EstatusEntrega.java), no los propuestos por la
-- auditoría externa — ver nota arriba.
ALTER TABLE ades_tareas_entregas
    ADD CONSTRAINT chk_estatus_entrega
    CHECK (estatus_entrega IN ('PENDIENTE', 'ENTREGADA', 'CALIFICADA', 'EXCUSA'));

ALTER TABLE ades_asistencias
    ADD CONSTRAINT chk_estatus_asistencia
    CHECK (estatus_asistencia IN ('PRESENTE', 'AUSENTE', 'TARDE', 'JUSTIFICADO'));

COMMIT;

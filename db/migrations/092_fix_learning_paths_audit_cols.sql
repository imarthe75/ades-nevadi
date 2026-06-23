-- =============================================================================
-- 092_fix_learning_paths_audit_cols.sql
-- ades_learning_paths tiene el trigger de auditoría audit_biu (fn_auditoria_biu)
-- pero le faltan las columnas usuario_creacion/usuario_modificacion que el trigger
-- asigna incondicionalmente -> cualquier INSERT/UPDATE fallaba con
-- 'record "new" has no field "usuario_creacion"'. Se agregan las columnas para
-- alinear la tabla con el patrón de auditoría ADES.
-- =============================================================================
ALTER TABLE ades_learning_paths
  ADD COLUMN IF NOT EXISTS usuario_creacion     TEXT NOT NULL DEFAULT current_user,
  ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT NOT NULL DEFAULT current_user;

-- =============================================================================
-- Migración: 080_ades_app_role.sql
-- Descripción: Crea el rol de base de datos ades_app (no-superusuario) con
--              privilegios granulares para la aplicación; revoca DELETE/UPDATE
--              en auditoria.log_auditoria para proteger el trail de auditoría.
-- Tablas afectadas: (solo permisos) public.*, auditoria.*, memoria.*
-- Dependencias: 038_auditoria_v2.sql, esquemas auditoria y memoria existentes
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- Migración 080 — Rol ades_app (no-superusuario) para la aplicación
-- Fecha: 2026-06-17
-- Hallazgo A: ades_admin es superusuario → REVOKE no tiene efecto
-- Solución: crear ades_app (non-superuser) con privilegios granulares
--
-- NOTA: Esta migración CREA el rol y sus permisos pero NO cambia la conexión
-- de la aplicación. El cambio de POSTGRES_USER=ades_admin → ades_app
-- requiere actualizar .env y reiniciar los servicios.
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Crear rol de aplicación (no superusuario)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ades_app') THEN
    CREATE ROLE ades_app LOGIN PASSWORD 'ades_app_password_CAMBIAR_EN_PROD';
  END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Privilegios sobre el schema public
-- ─────────────────────────────────────────────────────────────────────────────
GRANT USAGE ON SCHEMA public TO ades_app;
GRANT USAGE ON SCHEMA auditoria TO ades_app;
GRANT USAGE ON SCHEMA memoria TO ades_app;

-- SELECT/INSERT/UPDATE/DELETE en todas las tablas públicas (excepto auditoria.log_auditoria)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ades_app;

-- Sequences
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE ON SEQUENCES TO ades_app;

-- memoria schema
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA memoria TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA memoria
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ades_app;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. auditoria schema: solo SELECT e INSERT (nunca DELETE/UPDATE/TRUNCATE)
-- ─────────────────────────────────────────────────────────────────────────────
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA auditoria TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA auditoria
  GRANT SELECT, INSERT ON TABLES TO ades_app;

-- Verificar explícitamente que NO tiene DELETE en log_auditoria
REVOKE DELETE, UPDATE, TRUNCATE ON auditoria.log_auditoria FROM ades_app;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Funciones necesarias
-- ─────────────────────────────────────────────────────────────────────────────
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ades_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA auditoria TO ades_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA memoria TO ades_app;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Verificación
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  v_puede_delete_audit BOOLEAN;
  v_puede_insert_audit BOOLEAN;
  v_puede_select_audit BOOLEAN;
  v_puede_delete_public BOOLEAN;
BEGIN
  SELECT has_table_privilege('ades_app', 'auditoria.log_auditoria', 'DELETE') INTO v_puede_delete_audit;
  SELECT has_table_privilege('ades_app', 'auditoria.log_auditoria', 'INSERT') INTO v_puede_insert_audit;
  SELECT has_table_privilege('ades_app', 'auditoria.log_auditoria', 'SELECT') INTO v_puede_select_audit;
  SELECT has_table_privilege('ades_app', 'public.ades_estudiantes', 'DELETE') INTO v_puede_delete_public;

  RAISE NOTICE '=== 080 ades_app role — Verificación ===';
  RAISE NOTICE 'log_auditoria DELETE: % (esperado: false)', v_puede_delete_audit;
  RAISE NOTICE 'log_auditoria INSERT: % (esperado: true)',  v_puede_insert_audit;
  RAISE NOTICE 'log_auditoria SELECT: % (esperado: true)',  v_puede_select_audit;
  RAISE NOTICE 'ades_estudiantes DELETE: % (esperado: true)', v_puede_delete_public;

  IF v_puede_delete_audit THEN
    RAISE WARNING 'ades_app AÚN puede hacer DELETE en log_auditoria — revisar';
  ELSE
    RAISE NOTICE 'HALLAZGO A RESUELTO: ades_app no puede borrar log_auditoria ✓';
  END IF;
END $$;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────────
-- INSTRUCCIONES PARA ACTIVAR (ejecutar manualmente en producción):
--
-- 1. Cambiar contraseña de ades_app:
--    ALTER ROLE ades_app PASSWORD 'nueva_contraseña_segura';
--
-- 2. Actualizar .env:
--    POSTGRES_USER=ades_app
--    POSTGRES_PASSWORD=nueva_contraseña_segura
--
-- 3. Reiniciar servicios:
--    docker compose restart ades-api ades-bff pgbouncer
--
-- NOTA: ades_admin (superusuario) sigue existiendo para tareas DBA.
-- Para máxima seguridad, conectar como ades_admin solo desde la CLI,
-- nunca desde la aplicación.
-- ─────────────────────────────────────────────────────────────────────────────

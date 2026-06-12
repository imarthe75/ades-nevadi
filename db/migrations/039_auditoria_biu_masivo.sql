/*
 * by Im@rthe
 * Fecha: 2026-06-11
 * Archivo: 039_auditoria_biu_masivo.sql
 *
 * Descripcion:
 * Aplica audit_biu a las 88 tablas ades_* elegibles que no lo tenian al momento
 * de ejecutar 038_auditoria_v2.sql. Tambien registra la funcion asignar_biu()
 * que pudo no existir en la BD si 038 se aplico antes de esta revision.
 *
 * Prerequisito: 038_auditoria_v2.sql aplicado (schema auditoria, funciones, log_auditoria).
 *
 * Para produccion: ejecutar 039_auditoria_aiud_produccion.sql (pendiente).
 * Ese script activa audit_aiud en todas las tablas via asignar_triggers().
 */

-- Asegurar que asignar_biu existe (puede faltar si 038 se aplico antes del parche)
CREATE OR REPLACE FUNCTION auditoria.asignar_biu(p_tabla TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = p_tabla::regclass
    ) THEN
        EXECUTE format(
            'CREATE TRIGGER audit_biu
             BEFORE INSERT OR UPDATE ON %s
             FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu()',
            p_tabla
        );
        RAISE NOTICE 'audit_biu creado en %', p_tabla;
    END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.asignar_biu(TEXT) IS
    'Aplica solo el trigger audit_biu (BEFORE INSERT OR UPDATE) a la tabla indicada. Obligatorio en DEV y PROD. Para activar tambien audit_aiud en produccion, usar asignar_triggers().';

-- Aplicacion masiva: audit_biu en todas las tablas ades_* con columnas canonicas
DO $$
DECLARE
    v_tabla TEXT;
    v_count INTEGER := 0;
    v_skip  INTEGER := 0;
BEGIN
    FOR v_tabla IN
        SELECT 'public.' || t.tablename
        FROM pg_tables t
        WHERE t.schemaname = 'public'
          AND t.tablename LIKE 'ades_%'
          AND EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = 'public'
                AND c.table_name = t.tablename
                AND c.column_name = 'ref'
          )
          AND EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = 'public'
                AND c.table_name = t.tablename
                AND c.column_name = 'row_version'
          )
          AND EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = 'public'
                AND c.table_name = t.tablename
                AND c.column_name = 'fecha_creacion'
          )
        ORDER BY t.tablename
    LOOP
        BEGIN
            PERFORM auditoria.asignar_biu(v_tabla);
            v_count := v_count + 1;
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'No se pudo asignar audit_biu a %: %', v_tabla, SQLERRM;
            v_skip := v_skip + 1;
        END;
    END LOOP;

    RAISE NOTICE '=== audit_biu aplicado en % tablas, % omitidas ===', v_count, v_skip;
END $$;

-- Verificacion post-migracion
DO $$
DECLARE
    v_sin_biu INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_sin_biu
    FROM pg_tables t
    WHERE t.schemaname = 'public'
      AND t.tablename LIKE 'ades_%'
      AND EXISTS (
          SELECT 1 FROM information_schema.columns c
          WHERE c.table_schema='public' AND c.table_name=t.tablename AND c.column_name='ref'
      )
      AND NOT EXISTS (
          SELECT 1 FROM pg_trigger tr
          WHERE tr.tgname = 'audit_biu' AND tr.tgrelid = ('public.' || t.tablename)::regclass
      );

    IF v_sin_biu > 0 THEN
        RAISE WARNING '% tablas elegibles siguen sin audit_biu — revisar con: SELECT * FROM auditoria.reporte_cobertura();', v_sin_biu;
    ELSE
        RAISE NOTICE 'OK: todas las tablas elegibles tienen audit_biu activo.';
    END IF;
END $$;

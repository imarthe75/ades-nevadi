/*
 * Archivo: 145_fix_asignar_triggers_quoting.sql
 * Fecha: 2026-07-15
 *
 * Bug encontrado al probar fn_reconciliar_tabla(): auditoria.asignar_triggers() usa %I en
 * ambos CREATE TRIGGER (audit_biu Y audit_aiud), que entrecomilla el argumento COMPLETO como
 * un solo identificador — 'public.ades_suplencias' se vuelve "public.ades_suplencias" (una
 * tabla, inexistente, literalmente con un punto en el nombre) en vez de schema.tabla.
 * Confirmado con el error real: "relation \"public.ades_rubricas\" does not exist".
 *
 * auditoria.asignar_biu() (función hermana, usada exitosamente en la migración de Suplencia)
 * ya usa %s correctamente — este fix alinea asignar_triggers() al mismo patrón.
 *
 * Impacto si no se corrige: el comando documentado en CLAUDE.md y en el plan de remediación
 * para activar la auditoría completa en el go-live
 * (SELECT auditoria.asignar_triggers('public.ades_<tabla>');) habría fallado en cada tabla.
 */
CREATE OR REPLACE FUNCTION auditoria.asignar_triggers(p_tabla text)
RETURNS void AS $$
BEGIN
    -- audit_biu: BEFORE INSERT OR UPDATE
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

    -- audit_aiud: AFTER INSERT OR UPDATE OR DELETE (ACTIVO por defecto)
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_aiud'
          AND tgrelid = p_tabla::regclass
    ) THEN
        EXECUTE format(
            'CREATE TRIGGER audit_aiud
             AFTER INSERT OR UPDATE OR DELETE ON %s
             FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_aiud()',
            p_tabla
        );
        RAISE NOTICE 'audit_aiud creado en %', p_tabla;
    END IF;
END;
$$ LANGUAGE plpgsql;

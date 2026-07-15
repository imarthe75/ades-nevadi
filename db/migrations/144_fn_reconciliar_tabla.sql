/*
 * Archivo: 144_fn_reconciliar_tabla.sql
 * Fecha: 2026-07-15
 *
 * Hueco detectado por el usuario al revisar la explicación del ledger hash-encadenado:
 * auditoria.fn_verificar_cadena() solo detecta manipulación DENTRO del propio log
 * (que hash_actual cuadre con hash_anterior+datos). No detecta manipulación DIRECTA de una
 * tabla de negocio que deje el log intacto pero desactualizado (ej. un UPDATE ejecutado con
 * acceso directo a psql, sin pasar por la app). Esta función cierra esa amenaza: compara el
 * estado VIVO de cada fila contra el último executednewdata registrado para su ref, usando
 * to_jsonb() (agnóstico al esquema, igual que el trigger — agregar/quitar columnas no rompe
 * esta función).
 *
 * Requiere que auditoria.asignar_triggers() (audit_aiud) esté activo en la tabla — sin eso,
 * no hay executednewdata contra qué comparar y la función simplemente no devuelve filas para
 * esa tabla. Uso: SELECT * FROM auditoria.fn_reconciliar_tabla('public.ades_personas') WHERE
 * NOT coincide; -- filas con posible manipulación directa
 */
CREATE OR REPLACE FUNCTION auditoria.fn_reconciliar_tabla(p_tabla regclass)
RETURNS TABLE(ref uuid, coincide boolean, ultima_revision timestamptz) AS $$
DECLARE
    v_tabla_calificada text := p_tabla::text;
    v_tabla_simple     text;
    v_sql              text;
BEGIN
    SELECT c.relname INTO v_tabla_simple FROM pg_class c WHERE c.oid = p_tabla;

    v_sql := format($f$
        SELECT t.ref,
               (to_jsonb(t) = l.executednewdata) AS coincide,
               l.recorddatetime AS ultima_revision
        FROM %s t
        JOIN LATERAL (
            SELECT executednewdata, recorddatetime
            FROM auditoria.log_auditoria la
            WHERE la.uuid_ref = t.ref AND la.tablename = %L
            ORDER BY la.log_seq DESC
            LIMIT 1
        ) l ON true
    $f$, v_tabla_calificada, v_tabla_simple);

    RETURN QUERY EXECUTE v_sql;
END;
$$ LANGUAGE plpgsql STABLE;

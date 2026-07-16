/*
 * by Im@rthe
 * Fecha: 2026-07-16
 * Archivo: 148_index_log_auditoria_log_seq.sql
 *
 * Descripcion:
 * Agrega el indice faltante sobre auditoria.log_auditoria.log_seq.
 * fn_auditoria_aiud() (137/139) ejecuta "ORDER BY log_seq DESC LIMIT 1" de forma
 * sincrona dentro del trigger AFTER INSERT/UPDATE/DELETE de las ~57 tablas
 * auditadas, para encontrar el ultimo hash de la cadena. log_seq se agrego como
 * BIGSERIAL sin PK/UNIQUE/indice propio (su unico respaldo era la secuencia), por
 * lo que esa consulta hacia un full scan + sort de toda la tabla en cada mutacion
 * del sistema — un cuello de botella que empeora con el crecimiento del ledger y
 * serializa efectivamente las escrituras concurrentes contra una sola lectura
 * global. fn_verificar_cadena() (137) y fn_reconciliar_tabla() (144) tambien
 * recorren la tabla ordenando por log_seq y se benefician del mismo indice.
 *
 * Hallazgo: code-review 2026-07-16 sobre HEAD~5..HEAD (hardening del ledger).
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_log_seq
    ON auditoria.log_auditoria (log_seq DESC);

/*
 * by Im@rthe
 * Fecha: 2026-07-15
 * Archivo: 137_harden_ledger.sql
 *
 * Descripcion:
 * Endurecimiento del ledger de auditoria en PostgreSQL.
 * 1. Apaga los triggers audit_aiud de prueba activos.
 * 2. Limpia la tabla auditoria.log_auditoria.
 * 3. Migra la funcion fn_auditoria_aiud a SHA-256 con encadenamiento global real.
 * 4. Crea la funcion fn_verificar_cadena para auditoria de integridad.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ==========================================
-- 1. Apagar triggers audit_aiud activos de prueba
-- ==========================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'audit_aiud' AND tgrelid = 'public.ades_horario_corrida'::regclass) THEN
        ALTER TABLE public.ades_horario_corrida DISABLE TRIGGER audit_aiud;
        RAISE NOTICE 'Trigger audit_aiud deshabilitado en public.ades_horario_corrida';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'audit_aiud' AND tgrelid = 'public.ades_webhook_logs'::regclass) THEN
        ALTER TABLE public.ades_webhook_logs DISABLE TRIGGER audit_aiud;
        RAISE NOTICE 'Trigger audit_aiud deshabilitado en public.ades_webhook_logs';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'audit_aiud' AND tgrelid = 'public.ades_webhooks'::regclass) THEN
        ALTER TABLE public.ades_webhooks DISABLE TRIGGER audit_aiud;
        RAISE NOTICE 'Trigger audit_aiud deshabilitado en public.ades_webhooks';
    END IF;
END $$;

-- ==========================================
-- 2. Limpiar la tabla de auditoria
-- ==========================================
ALTER TABLE auditoria.log_auditoria ADD COLUMN IF NOT EXISTS log_seq BIGSERIAL;
TRUNCATE TABLE auditoria.log_auditoria;

-- ==========================================
-- 3. Redefinicion de fn_auditoria_aiud con SHA-256 y encadenamiento global
-- ==========================================
CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_aiud()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_data       TEXT;
    v_new_data       TEXT;
    v_hash_nuevo     TEXT;
    v_hash_original  TEXT;
    v_usuario        TEXT;
    v_uuid_ref       UUID;
    v_recorddatetime TIMESTAMPTZ;
BEGIN
    v_recorddatetime := NOW();

    -- Buscar el ultimo hash_nuevo global registrado en el log de auditoria
    SELECT l.hash_nuevo
    INTO v_hash_original
    FROM auditoria.log_auditoria l
    ORDER BY l.log_seq DESC
    LIMIT 1;

    -- Si es el primer registro, el hash_original es 'GENESIS'
    IF v_hash_original IS NULL THEN
        v_hash_original := 'GENESIS';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        v_old_data   := ROW(OLD.*);
        v_new_data   := ROW(NEW.*);
        v_uuid_ref   := OLD.ref;

        -- Determinar usuario ejecutor
        BEGIN
            v_usuario := NEW.usuario_modificacion;
        EXCEPTION WHEN undefined_column THEN
            v_usuario := SESSION_USER;
        END;

        -- Hash SHA-256 encadenado: hash_original + metadata + datos
        v_hash_nuevo := encode(digest(
            v_hash_original || TG_TABLE_SCHEMA || TG_TABLE_NAME || 'U' ||
            COALESCE(v_old_data, '') || COALESCE(v_new_data, '') ||
            COALESCE(v_usuario, '') || v_recorddatetime::text,
            'sha256'
        ), 'hex');

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             originaldata, executednewdata, executedsql,
             hash_nuevo, hash_original, uuid_ref, recorddatetime)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario, 'U',
             v_old_data, v_new_data, current_query(),
             v_hash_nuevo, v_hash_original, v_uuid_ref, v_recorddatetime);

        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        v_old_data := ROW(OLD.*);
        v_uuid_ref := OLD.ref;

        -- Determinar usuario ejecutor
        v_usuario := SESSION_USER;

        -- Hash SHA-256 encadenado: hash_original + metadata + datos
        v_hash_nuevo := encode(digest(
            v_hash_original || TG_TABLE_SCHEMA || TG_TABLE_NAME || 'D' ||
            COALESCE(v_old_data, '') || '' ||
            COALESCE(v_usuario, '') || v_recorddatetime::text,
            'sha256'
        ), 'hex');

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             originaldata, executedsql,
             hash_nuevo, hash_original, uuid_ref, recorddatetime)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario, 'D',
             v_old_data, current_query(),
             v_hash_nuevo, v_hash_original, v_uuid_ref, v_recorddatetime);

        RETURN OLD;

    ELSIF TG_OP = 'INSERT' THEN
        v_new_data := ROW(NEW.*);
        v_uuid_ref := NEW.ref;

        -- Determinar usuario ejecutor
        BEGIN
            v_usuario := NEW.usuario_creacion;
        EXCEPTION WHEN undefined_column THEN
            v_usuario := SESSION_USER;
        END;

        -- Hash SHA-256 encadenado: hash_original + metadata + datos
        v_hash_nuevo := encode(digest(
            v_hash_original || TG_TABLE_SCHEMA || TG_TABLE_NAME || 'I' ||
            '' || COALESCE(v_new_data, '') ||
            COALESCE(v_usuario, '') || v_recorddatetime::text,
            'sha256'
        ), 'hex');

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             executednewdata, executedsql,
             hash_nuevo, hash_original, uuid_ref, recorddatetime)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario, 'I',
             v_new_data, current_query(),
             v_hash_nuevo, v_hash_original, v_uuid_ref, v_recorddatetime);

        RETURN NEW;

    ELSE
        RAISE WARNING '[auditoria.fn_auditoria_aiud] Accion no reconocida: % en tabla % a las %',
            TG_OP, TG_TABLE_NAME, NOW();
        RETURN NULL;
    END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.fn_auditoria_aiud() IS
    'Trigger AFTER INSERT OR UPDATE OR DELETE. Registra cada evento DML en auditoria.log_auditoria con hash SHA-256 encadenado globalmente para asegurar la integridad de la bitacora completa.';

-- ==========================================
-- 4. Funcion de verificacion de integridad
-- ==========================================
CREATE OR REPLACE FUNCTION auditoria.fn_verificar_cadena(
    p_tablename TEXT DEFAULT NULL,
    p_uuid_ref UUID DEFAULT NULL
)
RETURNS TABLE (
    registro_alterado_id UUID,
    razon_inconsistencia TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH ordenado AS (
        SELECT
            id,
            schemaname,
            tablename,
            username,
            dmlaction,
            originaldata,
            executednewdata,
            uuid_ref,
            hash_nuevo,
            hash_original,
            recorddatetime,
            LAG(hash_nuevo) OVER (ORDER BY log_seq ASC) AS expected_hash_original
        FROM auditoria.log_auditoria
    ),
    recomputo AS (
        SELECT
            id,
            tablename,
            uuid_ref,
            hash_nuevo,
            hash_original,
            expected_hash_original,
            encode(digest(
                COALESCE(hash_original, 'GENESIS') ||
                schemaname || tablename || dmlaction ||
                COALESCE(originaldata, '') || COALESCE(executednewdata, '') ||
                COALESCE(username, '') || recorddatetime::text,
                'sha256'
            ), 'hex') AS recalculated_hash_nuevo
        FROM ordenado
    ),
    inconsistencias AS (
        SELECT
            id AS v_id,
            tablename AS v_tablename,
            uuid_ref AS v_uuid_ref,
            CASE
                WHEN hash_nuevo <> recalculated_hash_nuevo THEN 'HASH_NUEVO_INVALIDO'
                WHEN expected_hash_original IS NOT NULL AND hash_original <> expected_hash_original THEN 'CADENA_ROTA'
                WHEN expected_hash_original IS NULL AND hash_original <> 'GENESIS' THEN 'GENESIS_INVALIDO'
                ELSE 'DESCONOCIDO'
            END AS v_razon
        FROM recomputo
        WHERE hash_nuevo <> recalculated_hash_nuevo
           OR (expected_hash_original IS NOT NULL AND hash_original <> expected_hash_original)
           OR (expected_hash_original IS NULL AND hash_original <> 'GENESIS')
    )
    SELECT v_id, v_razon
    FROM inconsistencias
    WHERE (p_tablename IS NULL OR v_tablename = p_tablename)
      AND (p_uuid_ref IS NULL OR v_uuid_ref = p_uuid_ref);
END;
$$;

COMMENT ON FUNCTION auditoria.fn_verificar_cadena(TEXT, UUID) IS
    'Funcion para verificar la integridad del ledger de auditoria. Recorre toda la cadena globalmente, recalculando hashes y validando la continuidad de los eslabones.';

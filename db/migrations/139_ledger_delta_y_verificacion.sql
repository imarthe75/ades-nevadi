/*
 * by Im@rthe
 * Fecha: 2026-07-15
 * Archivo: 139_ledger_delta_y_verificacion.sql
 *
 * Descripcion:
 * Actualizacion del ledger de auditoria.
 * 1. Agrega las columnas hash_anterior y changed_fields a la tabla log_auditoria.
 * 2. Convierte las columnas originaldata y executednewdata a tipo JSONB.
 * 3. Redefine la funcion de trigger auditoria.fn_auditoria_aiud para usar JSONB,
 *    calcular deltas y encadenar hashes SHA-256 usando clock_timestamp.
 * 4. Redefine la funcion de verificacion auditoria.fn_verificar_cadena para
 *    recorrer secuencialmente el log y reportar registros alterados.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ==========================================
-- 1. Modificaciones a la tabla de auditoria
-- ==========================================
DO $$
BEGIN
    -- Agregar columna hash_anterior si no existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auditoria' 
          AND table_name = 'log_auditoria' 
          AND column_name = 'hash_anterior'
    ) THEN
        ALTER TABLE auditoria.log_auditoria ADD COLUMN hash_anterior TEXT;
        RAISE NOTICE 'Columna hash_anterior agregada a auditoria.log_auditoria';
    END IF;

    -- Agregar columna changed_fields si no existe
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auditoria' 
          AND table_name = 'log_auditoria' 
          AND column_name = 'changed_fields'
    ) THEN
        ALTER TABLE auditoria.log_auditoria ADD COLUMN changed_fields JSONB;
        RAISE NOTICE 'Columna changed_fields agregada a auditoria.log_auditoria';
    END IF;

    -- Convertir originaldata a JSONB si es necesario
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auditoria' 
          AND table_name = 'log_auditoria' 
          AND column_name = 'originaldata' 
          AND data_type = 'text'
    ) THEN
        ALTER TABLE auditoria.log_auditoria ALTER COLUMN originaldata TYPE JSONB USING originaldata::jsonb;
        RAISE NOTICE 'Columna originaldata convertida a JSONB';
    END IF;

    -- Convertir executednewdata a JSONB si es necesario
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auditoria' 
          AND table_name = 'log_auditoria' 
          AND column_name = 'executednewdata' 
          AND data_type = 'text'
    ) THEN
        ALTER TABLE auditoria.log_auditoria ALTER COLUMN executednewdata TYPE JSONB USING executednewdata::jsonb;
        RAISE NOTICE 'Columna executednewdata convertida a JSONB';
    END IF;
END $$;

-- ==========================================
-- 2. Redefinicion de la funcion de trigger fn_auditoria_aiud
-- ==========================================
CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_aiud()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_jsonb      JSONB;
    v_new_jsonb      JSONB;
    v_delta          JSONB;
    v_hash_anterior  TEXT;
    v_hash_nuevo     TEXT;
    v_usuario        TEXT;
    v_uuid_ref       UUID;
    v_recorddatetime TIMESTAMPTZ;
BEGIN
    -- 1. Capturar marca de tiempo unica para esta entrada
    v_recorddatetime := clock_timestamp();

    -- 2. Convertir registros a JSONB de forma segura
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        v_old_jsonb := to_jsonb(OLD);
        v_uuid_ref  := (v_old_jsonb ->> 'ref')::uuid;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        v_new_jsonb := to_jsonb(NEW);
        IF v_uuid_ref IS NULL THEN
            v_uuid_ref := (v_new_jsonb ->> 'ref')::uuid;
        END IF;
    END IF;

    -- 3. Determinar usuario responsable
    IF TG_OP = 'INSERT' THEN
        v_usuario := coalesce(v_new_jsonb ->> 'usuario_creacion', session_user);
    ELSIF TG_OP = 'UPDATE' THEN
        v_usuario := coalesce(v_new_jsonb ->> 'usuario_modificacion', session_user);
    ELSE
        v_usuario := session_user;
    END IF;

    -- 4. Obtener el hash del ultimo registro de la bitacora de forma segura usando log_seq
    SELECT hash_nuevo INTO v_hash_anterior
    FROM auditoria.log_auditoria
    ORDER BY log_seq DESC
    LIMIT 1;

    v_hash_anterior := coalesce(v_hash_anterior, 'GENESIS');

    -- 5. Calcular delta si es UPDATE
    IF TG_OP = 'UPDATE' THEN
        SELECT jsonb_object_agg(n.key, n.value) INTO v_delta
        FROM jsonb_each(v_new_jsonb) n
        LEFT JOIN jsonb_each(v_old_jsonb) o ON n.key = o.key
        WHERE n.value IS DISTINCT FROM o.value;
    END IF;

    -- 6. Calcular el Hash Encadenado Real (SHA-256)
    v_hash_nuevo := encode(digest(
        v_hash_anterior ||
        TG_TABLE_SCHEMA ||
        TG_TABLE_NAME ||
        TG_OP ||
        coalesce(v_old_jsonb::text, '') ||
        coalesce(v_new_jsonb::text, '') ||
        v_usuario ||
        v_recorddatetime::text,
        'sha256'
    ), 'hex');

    -- 7. Registrar en la bitacora centralizada
    INSERT INTO auditoria.log_auditoria (
        schemaname, tablename, username, dmlaction,
        originaldata, executednewdata, changed_fields,
        hash_original, hash_nuevo, hash_anterior,
        uuid_ref, executedsql, recorddatetime
    ) VALUES (
        TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario,
        CASE WHEN TG_OP = 'INSERT' THEN 'I' WHEN TG_OP = 'UPDATE' THEN 'U' ELSE 'D' END,
        v_old_jsonb, v_new_jsonb, v_delta,
        CASE WHEN v_old_jsonb IS NOT NULL THEN encode(digest(v_old_jsonb::text, 'sha256'), 'hex') ELSE NULL END,
        v_hash_nuevo, v_hash_anterior,
        v_uuid_ref, current_query(), v_recorddatetime
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.fn_auditoria_aiud() IS
    'Trigger AFTER INSERT OR UPDATE OR DELETE. Registra cada evento DML en auditoria.log_auditoria con datos en formato JSONB y hash SHA-256 encadenado globalmente.';

-- ==========================================
-- 3. Redefinicion de la funcion de verificacion de integridad
-- ==========================================
DROP FUNCTION IF EXISTS auditoria.fn_verificar_cadena(TEXT, UUID);

CREATE OR REPLACE FUNCTION auditoria.fn_verificar_cadena()
RETURNS TABLE (
    registro_alterado BIGINT,
    hash_esperado TEXT,
    hash_encontrado TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    r RECORD;
    v_hash_acumulado TEXT := 'GENESIS';
    v_hash_calculado TEXT;
BEGIN
    FOR r IN (
        SELECT log_seq, schemaname, tablename, dmlaction,
               originaldata, executednewdata, username,
               recorddatetime, hash_nuevo, hash_anterior
        FROM auditoria.log_auditoria
        ORDER BY log_seq ASC
    ) LOOP
        -- Verificar si el eslabon anterior coincide con el acumulado
        IF r.hash_anterior <> v_hash_acumulado THEN
            registro_alterado := r.log_seq;
            hash_esperado := v_hash_acumulado;
            hash_encontrado := r.hash_anterior;
            RETURN NEXT;
            RETURN;
        END IF;

        -- Recomputar el hash actual usando el acumulado
        v_hash_calculado := encode(digest(
            v_hash_acumulado ||
            r.schemaname ||
            r.tablename ||
            CASE r.dmlaction WHEN 'I' THEN 'INSERT' WHEN 'U' THEN 'UPDATE' ELSE 'DELETE' END ||
            coalesce(r.originaldata::text, '') ||
            coalesce(r.executednewdata::text, '') ||
            r.username ||
            r.recorddatetime::text,
            'sha256'
        ), 'hex');

        -- Validar contra el hash guardado en base de datos
        IF r.hash_nuevo <> v_hash_calculado THEN
            registro_alterado := r.log_seq;
            hash_esperado := v_hash_calculado;
            hash_encontrado := r.hash_nuevo;
            RETURN NEXT;
            RETURN;
        END IF;

        -- Avanzar en la cadena
        v_hash_acumulado := r.hash_nuevo;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION auditoria.fn_verificar_cadena() IS
    'Funcion para verificar la integridad del ledger de auditoria. Recorre la cadena de log_seq, recalculando y validando la continuidad de los eslabones.';

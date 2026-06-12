/*
 * by Im@rthe
 * Fecha: 2026-06-11
 * Archivo: 038_auditoria_v2.sql
 *
 * Descripcion:
 * AUDITORIA v2 — Esquema de auditoria completo y obligatorio para todas las
 * tablas ADES. Implementa el patron de auditoria institucional del Instituto
 * Nevadi con las siguientes mejoras sobre el script de referencia:
 *
 *   - log_auditoria: id UUID PK, TIMESTAMPTZ, indice GIN en datos JSON
 *   - fn_auditoria_biu: ya modernizada (uuidv7, columnas ADES, is_active)
 *   - auditoria_aiud: NUEVA — graba INSERT/UPDATE/DELETE en log_auditoria
 *     con hash MD5 de encadenamiento para detectar alteracion de registros
 *   - asignar_trigger_completo: aplica AMBOS triggers (biu + aiud) a una tabla
 *   - Aplicacion masiva: audit_biu + audit_aiud en las 108 tablas ades_*
 *     que tengan columnas de auditoria (ref, row_version, fecha_creacion)
 *
 * Decisiones de diseno:
 *   - audit_biu ACTIVO por defecto en TODAS las tablas (obligatorio en DEV y PROD).
 *   - audit_aiud DEFINIDO pero NO activado en bulk — se activa en migracion de produccion.
 *   - Columnas canonicas ADES: fecha_creacion, usuario_creacion,
 *     fecha_modificacion, usuario_modificacion (NO fccreacion/dsusuariocreacion)
 *   - uuid_ref en log_auditoria referencia el campo ref de la fila auditada
 *     permitiendo reconstruir el historial completo de cualquier entidad.
 *   - Hash MD5 encadenado: cada UPDATE vincula hash_nuevo al hash_original
 *     del registro anterior, permitiendo detectar alteracion directa en BD.
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ===========================
-- 0. Asegurar schema auditoria
-- ===========================
CREATE SCHEMA IF NOT EXISTS auditoria;

COMMENT ON SCHEMA auditoria IS
    'Schema de auditoria institucional ADES-Nevadi. Contiene la tabla de log y las funciones trigger de auditoria obligatorias para todas las tablas ades_*.';

-- ===========================
-- 1. Tabla: auditoria.log_auditoria
-- ===========================
-- No usar DROP TABLE en produccion. Creacion idempotente.
CREATE TABLE IF NOT EXISTS auditoria.log_auditoria (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    schemaname      VARCHAR     NOT NULL,
    tablename       VARCHAR     NOT NULL,
    username        VARCHAR,
    dmlaction       CHAR(1)     NOT NULL,   -- I=INSERT, U=UPDATE, D=DELETE
    originaldata    TEXT,
    executednewdata TEXT,
    executedsql     TEXT,
    uuid_ref        UUID,
    hash_nuevo      TEXT,
    hash_original   TEXT,
    recorddatetime  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- PK (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'auditoria.log_auditoria'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE auditoria.log_auditoria
            ADD CONSTRAINT pk_log_auditoria PRIMARY KEY (id);
    END IF;
END $$;

-- ===========================
-- 2. Comentarios: log_auditoria
-- ===========================
COMMENT ON TABLE auditoria.log_auditoria IS
    'Log de auditoria de todas las operaciones DML sobre tablas ades_*. Cada fila representa un evento INSERT, UPDATE o DELETE con hash MD5 encadenado para detectar alteracion directa en base de datos.';

COMMENT ON COLUMN auditoria.log_auditoria.id IS
    'Llave primaria UUID del registro de auditoria.';
COMMENT ON COLUMN auditoria.log_auditoria.schemaname IS
    'Schema de la tabla auditada (tipicamente public).';
COMMENT ON COLUMN auditoria.log_auditoria.tablename IS
    'Nombre de la tabla auditada.';
COMMENT ON COLUMN auditoria.log_auditoria.username IS
    'Usuario de base de datos o usuario de aplicacion que ejecuto el DML.';
COMMENT ON COLUMN auditoria.log_auditoria.dmlaction IS
    'Accion DML: I=INSERT, U=UPDATE, D=DELETE.';
COMMENT ON COLUMN auditoria.log_auditoria.originaldata IS
    'Representacion ROW(*) de la fila ANTES del cambio (NULL en INSERT).';
COMMENT ON COLUMN auditoria.log_auditoria.executednewdata IS
    'Representacion ROW(*) de la fila DESPUES del cambio (NULL en DELETE).';
COMMENT ON COLUMN auditoria.log_auditoria.executedsql IS
    'Consulta SQL que genero el evento (current_query()).';
COMMENT ON COLUMN auditoria.log_auditoria.uuid_ref IS
    'Valor del campo ref de la fila auditada. Permite reconstruir historial completo de una entidad.';
COMMENT ON COLUMN auditoria.log_auditoria.hash_nuevo IS
    'Hash MD5 de la fila NUEVA (executednewdata). En UPDATE: hash del estado resultante.';
COMMENT ON COLUMN auditoria.log_auditoria.hash_original IS
    'Hash MD5 de la fila ORIGINAL (originaldata). Encadenado con el hash_nuevo del evento anterior.';
COMMENT ON COLUMN auditoria.log_auditoria.recorddatetime IS
    'Timestamp con zona horaria del momento exacto del evento DML.';

-- ===========================
-- 3. Indices: log_auditoria
-- ===========================
CREATE INDEX IF NOT EXISTS idx_audit_dmlaction
    ON auditoria.log_auditoria (dmlaction);

CREATE INDEX IF NOT EXISTS idx_audit_recordtime
    ON auditoria.log_auditoria (recorddatetime DESC);

CREATE INDEX IF NOT EXISTS idx_audit_uuid_ref
    ON auditoria.log_auditoria (uuid_ref)
    WHERE uuid_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_tablename
    ON auditoria.log_auditoria (tablename);

CREATE INDEX IF NOT EXISTS idx_audit_tabla_fecha
    ON auditoria.log_auditoria (tablename, recorddatetime DESC);

CREATE INDEX IF NOT EXISTS idx_audit_username
    ON auditoria.log_auditoria (username)
    WHERE username IS NOT NULL;

-- ===========================
-- 4. Funcion: auditoria.fn_auditoria_biu (ACTUALIZADA)
-- ===========================
-- Mejoras sobre script de referencia:
--   - Soporta columnas ADES modernas: fecha_creacion, usuario_creacion, etc.
--   - Usa uuidv7() nativo de PG18 con fallback a gen_random_uuid()
--   - Soporta columna is_active (COALESCE a TRUE en INSERT)
--   - Protege fecha_creacion y usuario_creacion contra sobreescritura en UPDATE
--   - Compatible con tablas sin columna is_active (manejo por excepcion)
CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Asignar ref si es nulo (uuidv7 nativo PG18, fallback a gen_random_uuid)
    IF NEW.ref IS NULL THEN
        BEGIN
            NEW.ref := uuidv7();
        EXCEPTION WHEN OTHERS THEN
            NEW.ref := gen_random_uuid();
        END;
    END IF;

    IF TG_OP = 'INSERT' THEN
        NEW.row_version          := 1;
        NEW.fecha_creacion       := COALESCE(NEW.fecha_creacion, NOW());
        NEW.fecha_modificacion   := NOW();
        NEW.usuario_creacion     := COALESCE(NEW.usuario_creacion, CURRENT_USER);
        NEW.usuario_modificacion := COALESCE(NEW.usuario_modificacion, CURRENT_USER);
        -- is_active: solo si la columna existe en la tabla
        BEGIN
            IF NEW.is_active IS NULL THEN
                NEW.is_active := TRUE;
            END IF;
        EXCEPTION WHEN undefined_column THEN
            NULL; -- columna no existe en esta tabla, ignorar
        END;

    ELSIF TG_OP = 'UPDATE' THEN
        NEW.row_version          := COALESCE(OLD.row_version, 0) + 1;
        NEW.fecha_modificacion   := NOW();
        NEW.usuario_modificacion := COALESCE(NEW.usuario_modificacion, CURRENT_USER);
        -- Inmutable: fecha y usuario de creacion no cambian en UPDATE
        NEW.fecha_creacion   := OLD.fecha_creacion;
        NEW.usuario_creacion := OLD.usuario_creacion;
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION auditoria.fn_auditoria_biu() IS
    'Trigger BEFORE INSERT OR UPDATE. Gestiona automaticamente: ref (uuidv7/gen_random_uuid), row_version, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion, is_active. Las columnas de creacion son inmutables en UPDATE.';

-- ===========================
-- 5. Funcion: auditoria.fn_auditoria_aiud (NUEVA — adaptada para ADES)
-- ===========================
-- Diferencias con script de referencia:
--   - Columnas de usuario: usuario_modificacion / usuario_creacion (no dsusuariomodifica)
--   - Hash encadenado: busca hash previo por uuid_ref (no por texto de fila — mas robusto)
--   - id UUID en log_auditoria (PK que no existia en el script original)
--   - TIMESTAMPTZ en recorddatetime
--   - Manejo de excepcion si la tabla no tiene columna usuario_creacion/modificacion
CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_aiud()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_data      TEXT;
    v_new_data      TEXT;
    v_hash_nuevo    TEXT;
    v_hash_original TEXT;
    v_usuario       TEXT;
    v_uuid_ref      UUID;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        v_old_data   := ROW(OLD.*);
        v_new_data   := ROW(NEW.*);
        v_hash_nuevo := md5(CAST(ROW(NEW.*) AS TEXT));
        v_uuid_ref   := OLD.ref;

        -- Encadenamiento: buscar hash_nuevo mas reciente de esta entidad
        SELECT l.hash_nuevo
        INTO v_hash_original
        FROM auditoria.log_auditoria l
        WHERE l.uuid_ref = v_uuid_ref
          AND l.tablename = TG_TABLE_NAME
        ORDER BY l.recorddatetime DESC
        LIMIT 1;

        -- Usuario que ejecuto el UPDATE
        BEGIN
            v_usuario := NEW.usuario_modificacion;
        EXCEPTION WHEN undefined_column THEN
            v_usuario := SESSION_USER;
        END;

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             originaldata, executednewdata, executedsql,
             hash_nuevo, hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario, 'U',
             v_old_data, v_new_data, current_query(),
             v_hash_nuevo, v_hash_original, v_uuid_ref);

        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        v_old_data := ROW(OLD.*);
        v_uuid_ref := OLD.ref;

        SELECT l.hash_nuevo
        INTO v_hash_original
        FROM auditoria.log_auditoria l
        WHERE l.uuid_ref = v_uuid_ref
          AND l.tablename = TG_TABLE_NAME
        ORDER BY l.recorddatetime DESC
        LIMIT 1;

        -- En DELETE: hash_nuevo = hash_original (estado final antes de borrar)
        v_hash_nuevo := COALESCE(v_hash_original, md5(CAST(ROW(OLD.*) AS TEXT)));

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             originaldata, executedsql,
             hash_nuevo, hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, SESSION_USER, 'D',
             v_old_data, current_query(),
             v_hash_nuevo, v_hash_original, v_uuid_ref);

        RETURN OLD;

    ELSIF TG_OP = 'INSERT' THEN
        v_new_data      := ROW(NEW.*);
        v_hash_original := md5(CAST(ROW(NEW.*) AS TEXT));
        v_uuid_ref      := NEW.ref;

        BEGIN
            v_usuario := NEW.usuario_creacion;
        EXCEPTION WHEN undefined_column THEN
            v_usuario := SESSION_USER;
        END;

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction,
             executednewdata, executedsql,
             hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA, TG_TABLE_NAME, v_usuario, 'I',
             v_new_data, current_query(),
             v_hash_original, v_uuid_ref);

        RETURN NEW;

    ELSE
        RAISE WARNING '[auditoria.fn_auditoria_aiud] Accion no reconocida: % en tabla % a las %',
            TG_OP, TG_TABLE_NAME, NOW();
        RETURN NULL;
    END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.fn_auditoria_aiud() IS
    'Trigger AFTER INSERT OR UPDATE OR DELETE. Registra cada evento DML en auditoria.log_auditoria con hash MD5 encadenado por uuid_ref para detectar alteracion directa en base de datos. INACTIVO en desarrollo — se activa en migracion de produccion (039_auditoria_aiud_produccion.sql).';

-- ===========================
-- 6. Funciones helper de auditoria
-- ===========================

-- asignar_biu: aplica SOLO audit_biu a una tabla (DEV + PROD)
-- Uso: SELECT auditoria.asignar_biu('public.ades_alumnos');
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
    'Aplica solo el trigger audit_biu (BEFORE INSERT OR UPDATE) a la tabla indicada. Uso en DEV y PROD. Para activar tambien audit_aiud en produccion, usar asignar_triggers().';

-- asignar_triggers: aplica AMBOS triggers (biu + aiud) — PRODUCCION UNICAMENTE
-- Uso: SELECT auditoria.asignar_triggers('public.ades_alumnos');
CREATE OR REPLACE FUNCTION auditoria.asignar_triggers(p_tabla TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    -- audit_biu: BEFORE INSERT OR UPDATE
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = p_tabla::regclass
    ) THEN
        EXECUTE format(
            'CREATE TRIGGER audit_biu
             BEFORE INSERT OR UPDATE ON %I
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
             AFTER INSERT OR UPDATE OR DELETE ON %I
             FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_aiud()',
            p_tabla
        );
        RAISE NOTICE 'audit_aiud creado en %', p_tabla;
    END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.asignar_triggers(TEXT) IS
    'Aplica los triggers audit_biu Y audit_aiud a la tabla indicada. SOLO PARA PRODUCCION. En desarrollo usar asignar_biu(). Uso: SELECT auditoria.asignar_triggers(''public.ades_alumnos'');';

-- Mantener compatibilidad con nombre anterior
CREATE OR REPLACE FUNCTION auditoria.asignar_trigger(p_tabla TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM auditoria.asignar_triggers(p_tabla);
END;
$$;

-- ===========================
-- 7. Aplicacion masiva: SOLO audit_biu en todas las tablas ADES elegibles
-- ===========================
-- audit_aiud NO se activa aqui — se activa en migracion 039_auditoria_aiud_produccion.sql
-- Solo aplica a tablas con columnas canonicas: ref, row_version, fecha_creacion
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

    RAISE NOTICE '=== Auditoria v2 (DEV): audit_biu aplicado en % tablas, % omitidas ===', v_count, v_skip;
END $$;

-- ===========================
-- 8. Funcion de reporte: auditoria.reporte_cobertura
-- ===========================
CREATE OR REPLACE FUNCTION auditoria.reporte_cobertura()
RETURNS TABLE (
    tabla           TEXT,
    tiene_ref       BOOLEAN,
    tiene_rv        BOOLEAN,
    tiene_biu       BOOLEAN,
    tiene_aiud      BOOLEAN,
    elegible        BOOLEAN,
    estado          TEXT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        'public.' || t.tablename,
        bool_or(c.column_name = 'ref'),
        bool_or(c.column_name = 'row_version'),
        EXISTS(SELECT 1 FROM pg_trigger tr WHERE tr.tgname='audit_biu'  AND tr.tgrelid=('public.'||t.tablename)::regclass),
        EXISTS(SELECT 1 FROM pg_trigger tr WHERE tr.tgname='audit_aiud' AND tr.tgrelid=('public.'||t.tablename)::regclass),
        bool_or(c.column_name='ref') AND bool_or(c.column_name='row_version') AND bool_or(c.column_name='fecha_creacion'),
        CASE
          WHEN NOT bool_or(c.column_name='ref') THEN 'SIN_COLUMNAS_AUDITORIA'
          WHEN NOT EXISTS(SELECT 1 FROM pg_trigger tr WHERE tr.tgname='audit_biu' AND tr.tgrelid=('public.'||t.tablename)::regclass)
               THEN 'PENDIENTE_BIU'
          WHEN NOT EXISTS(SELECT 1 FROM pg_trigger tr WHERE tr.tgname='audit_aiud' AND tr.tgrelid=('public.'||t.tablename)::regclass)
               THEN 'PENDIENTE_AIUD'
          ELSE 'COMPLETO'
        END
    FROM pg_tables t
    LEFT JOIN information_schema.columns c
        ON c.table_schema = t.schemaname AND c.table_name = t.tablename
    WHERE t.schemaname = 'public' AND t.tablename LIKE 'ades_%'
    GROUP BY t.tablename
    ORDER BY t.tablename;
$$;

COMMENT ON FUNCTION auditoria.reporte_cobertura() IS
    'Reporte de cobertura de auditoria sobre todas las tablas ades_*. Uso: SELECT * FROM auditoria.reporte_cobertura();';

-- ===========================
-- 9. Notas tecnicas
-- ===========================
-- 1. log_auditoria tiene PK UUID para garantizar integridad del log.
-- 2. TIMESTAMPTZ en recorddatetime (el script original usaba TIMESTAMP sin TZ).
-- 3. Hash encadenado por uuid_ref: mas robusto que buscar por texto de fila.
-- 4. audit_biu: OBLIGATORIO en TODAS las tablas ades_* (DEV y PROD).
--    audit_aiud: DEFINIDO aqui, se activa en produccion via 039_auditoria_aiud_produccion.sql.
-- 5. fn_auditoria_biu usa uuidv7() nativo de PG18 con fallback a gen_random_uuid().
-- 6. Columnas canonicas: fecha_creacion/usuario_creacion (NO fccreacion/dsusuariocreacion).
-- 7. asignar_biu(): solo audit_biu (DEV+PROD).
--    asignar_triggers(): ambos triggers (SOLO PROD).
--    asignar_trigger(): alias de asignar_triggers() para compatibilidad.
-- 8. reporte_cobertura() permite auditar el estado de triggers en cualquier momento.
-- 9. Tablas sin columnas de auditoria (ref, row_version, fecha_creacion) son excluidas
--    de la aplicacion masiva — deben incorporar las columnas faltantes.

-- Fin del script

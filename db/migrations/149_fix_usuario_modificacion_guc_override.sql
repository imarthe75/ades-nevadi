/*
 * by Im@rthe
 * Fecha: 2026-07-16
 * Archivo: 149_fix_usuario_modificacion_guc_override.sql
 *
 * Descripcion:
 * Corrige un efecto secundario de 141_fix_usuario_auditoria_guc.sql: en el rama
 * UPDATE de fn_auditoria_biu(), el GUC de sesion (app.current_user) tenia prioridad
 * ABSOLUTA sobre NEW.usuario_modificacion, incluso cuando un adaptador JdbcTemplate
 * fija ese campo explicitamente a un valor distinto (ej.
 * ExpedienteWriteService#marcarConstanciaEntregada hace
 * "SET usuario_modificacion = 'sistema'"). Dentro de una request HTTP autenticada,
 * AuditSessionInterceptor ya dejo el GUC con el usuario real, asi que ese 'sistema'
 * explicito quedaba silenciosamente sobreescrito por el usuario de la request —
 * justo el caso que la migracion 141 NO necesitaba resolver asi (el problema
 * original era que updatable=false deja NEW == OLD, no que un valor explicito
 * distinto deba ignorarse).
 *
 * Fix: distinguir "valor congelado por updatable=false" (NEW == OLD, se debe leer
 * el GUC) de "valor explicito distinto al anterior" (NEW != OLD, se respeta tal
 * cual) via NULLIF(NEW.usuario_modificacion, OLD.usuario_modificacion) — el mismo
 * patron que 141 ya usa para el DEFAULT CURRENT_USER de columna en el INSERT.
 *
 * Hallazgo: code-review 2026-07-16 sobre HEAD~5..HEAD.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS trigger AS $$
BEGIN
    BEGIN
        IF NEW.ref IS NULL THEN
            BEGIN
                NEW.ref := uuidv7();
            EXCEPTION WHEN OTHERS THEN
                NEW.ref := gen_random_uuid();
            END;
        END IF;
    EXCEPTION WHEN undefined_column THEN
        NULL;
    END;

    IF TG_OP = 'INSERT' THEN
        NEW.row_version          := 1;
        NEW.fecha_creacion       := COALESCE(NEW.fecha_creacion, NOW());
        NEW.fecha_modificacion   := NOW();
        NEW.usuario_creacion     := COALESCE(NULLIF(NEW.usuario_creacion, CURRENT_USER), NULLIF(current_setting('app.current_user', true), ''), CURRENT_USER);
        NEW.usuario_modificacion := COALESCE(NULLIF(NEW.usuario_modificacion, CURRENT_USER), NULLIF(current_setting('app.current_user', true), ''), CURRENT_USER);
        BEGIN
            IF NEW.is_active IS NULL THEN
                NEW.is_active := TRUE;
            END IF;
        EXCEPTION WHEN undefined_column THEN
            NULL;
        END;

    ELSIF TG_OP = 'UPDATE' THEN
        NEW.row_version          := COALESCE(OLD.row_version, 0) + 1;
        NEW.fecha_modificacion   := NOW();
        -- Prioridad: (1) valor explicito distinto al anterior (ej. JdbcTemplate fijando
        -- 'sistema' a proposito) vía NULLIF(NEW, OLD) — si son iguales (caso
        -- updatable=false, JPA nunca envia la columna y Postgres repite OLD) se
        -- descarta y se cae al GUC; (2) usuario real de la sesion (app.current_user);
        -- (3) NEW tal cual llegue (cubre el caso raro de GUC vacio con valor explicito
        -- igual a OLD); (4) rol de conexion como ultimo recurso.
        NEW.usuario_modificacion := COALESCE(
            NULLIF(NEW.usuario_modificacion, OLD.usuario_modificacion),
            NULLIF(current_setting('app.current_user', true), ''),
            NEW.usuario_modificacion,
            CURRENT_USER
        );
        NEW.fecha_creacion   := OLD.fecha_creacion;
        NEW.usuario_creacion := OLD.usuario_creacion;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- MIGRACION 122: auditoria.fn_auditoria_biu() — columna ref opcional
-- =============================================================================
-- Objetivo: la función de trigger asumía que TODA tabla con audit_biu/trg_aud_biu
--           tiene columna `ref` (regla 3 CLAUDE.md), pero varias entidades JPA
--           extienden AdesAuditEntity (sin ref) en vez de AdesBaseEntity (con ref)
--           y de todas formas tienen el trigger asignado. La función ya
--           protegía `is_active` de forma análoga con EXCEPTION WHEN
--           undefined_column — se replica el mismo patrón para `ref`.
-- Fecha: 2026-07-10
-- =============================================================================

CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    -- Asignar ref si es nulo (uuidv7 nativo PG18, fallback a gen_random_uuid)
    -- Solo si la columna existe en la tabla (algunas entidades no la mapean).
    BEGIN
        IF NEW.ref IS NULL THEN
            BEGIN
                NEW.ref := uuidv7();
            EXCEPTION WHEN OTHERS THEN
                NEW.ref := gen_random_uuid();
            END;
        END IF;
    EXCEPTION WHEN undefined_column THEN
        NULL; -- columna no existe en esta tabla, ignorar
    END;

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
$function$;

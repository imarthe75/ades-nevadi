/*
 * Archivo: 141_fix_usuario_auditoria_guc.sql
 * Fecha: 2026-07-15
 * Ambiente: desarrollo (host único = producción de infraestructura)
 *
 * INCIDENTE (encontrado al revisar el fix de Suplencia, 2026-07-15): las 57 entidades JPA
 * que extienden mx.ades.common.AdesAuditEntity/AdesBaseEntity mapean usuario_creacion y
 * usuario_modificacion como insertable=false/updatable=false (para no violar la Regla
 * Mandatoria #5, "no asignar manualmente"). Pero fn_auditoria_biu() solo hacía
 * COALESCE(NEW.usuario_*, CURRENT_USER) — y CURRENT_USER es el ROL de conexión a BD
 * (ej. 'ades_admin'), el mismo para TODA la aplicación en un modelo de connection pooling,
 * nunca el usuario real. Verificado en vivo:
 *   - INSERT: usuario_creacion quedaba SIEMPRE en 'ades_admin', nunca el usuario real.
 *   - UPDATE: usuario_modificacion quedaba CONGELADO PARA SIEMPRE en el valor de creación,
 *     porque con updatable=false Postgres no incluye la columna en el UPDATE, por lo que
 *     NEW.usuario_modificacion llega igual a OLD.usuario_modificacion (no NULL), y el
 *     COALESCE nunca alcanzaba el fallback.
 * Ya existía media solución sin conectar: mx.ades.config.AuditSessionInterceptor (aspecto
 * @Before) propaga la identidad real a la sesión de Postgres vía
 * SELECT set_config('app.current_user', <sub>, true) — pero fn_auditoria_biu() nunca leía
 * ese GUC (solo CURRENT_USER), así que el mecanismo era código muerto. Esta migración
 * conecta ambos lados. El interceptor también se corrige por separado (usaba jwt.getSubject(),
 * un UUID opaco, en vez de auth.getName() que ya resuelve a preferred_username — la misma
 * convención que usa el resto del código, ej. AdesUserService/RubricaController).
 *
 * Alcance: afecta a las 57 entidades vía AdesAuditEntity — un solo cambio de función en vez
 * de tocar 57 clases Java. No requiere cambios de esquema (ninguna columna se agrega/quita).
 *
 * SEGUNDA CAUSA RAÍZ (encontrada al verificar el fix en vivo): 121 tablas tienen
 * DEFAULT CURRENT_USER a nivel de columna en usuario_creacion/usuario_modificacion (de una
 * migración temprana, antes de que existiera esta lógica de trigger). Postgres aplica el
 * DEFAULT de columna ANTES de que corra un trigger BEFORE INSERT, así que NEW.usuario_creacion
 * NUNCA llega NULL en un INSERT que omite la columna (como hace JPA con insertable=false) —
 * ya llega poblado con 'ades_admin' (el rol), y el COALESCE nunca alcanzaba el GUC. Se trata
 * como "no provisto explícitamente" cualquier valor que coincida exactamente con CURRENT_USER
 * (además de NULL), vía NULLIF(NEW.usuario_creacion, CURRENT_USER) — funciona igual para las
 * tablas con y sin ese DEFAULT, sin tener que auditar/alterar 121 columnas.
 */

CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS trigger AS $$
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
        -- Prioridad: (1) valor explícito ya seteado por el adaptador (JdbcTemplate, sigue
        -- funcionando exactamente igual que antes), (2) usuario real de la sesión propagado
        -- por AuditSessionInterceptor vía app.current_user, (3) rol de conexión a BD como
        -- último recurso (jobs en background, migraciones, psql directo).
        -- NULLIF(..., CURRENT_USER) descarta el DEFAULT CURRENT_USER de columna (121 tablas)
        -- que de otro modo llegaría ya poblado en NEW y taparía el COALESCE.
        NEW.usuario_creacion     := COALESCE(NULLIF(NEW.usuario_creacion, CURRENT_USER), NULLIF(current_setting('app.current_user', true), ''), CURRENT_USER);
        NEW.usuario_modificacion := COALESCE(NULLIF(NEW.usuario_modificacion, CURRENT_USER), NULLIF(current_setting('app.current_user', true), ''), CURRENT_USER);
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
        -- usuario_modificacion: el GUC de sesión va PRIMERO (a diferencia de INSERT). En
        -- entidades con updatable=false, NEW.usuario_modificacion == OLD.usuario_modificacion
        -- siempre (Postgres no distingue "no tocado" de "reafirmado al mismo valor"), así que
        -- confiar en NEW primero dejaría el campo congelado para siempre. Con el GUC primero,
        -- toda actualización dentro de una request autenticada queda correctamente atribuida.
        NEW.usuario_modificacion := COALESCE(NULLIF(current_setting('app.current_user', true), ''), NEW.usuario_modificacion, CURRENT_USER);
        -- Inmutable: fecha y usuario de creacion no cambian en UPDATE
        NEW.fecha_creacion   := OLD.fecha_creacion;
        NEW.usuario_creacion := OLD.usuario_creacion;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

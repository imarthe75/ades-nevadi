-- =============================================================================
-- Migración: 20260612_0001_ades_nevadi.sql
-- Descripción: FASE 34 — Crea las tablas ades_webhooks (registro de endpoints
--              receptores de eventos) y ades_webhook_logs (historial de intentos
--              de entrega con payload, status HTTP y resultado). Incluye PKs,
--              FK con CASCADE DELETE, triggers audit_biu; audit_aiud creado pero
--              deshabilitado (activar en producción).
-- Tablas afectadas: ades_webhooks, ades_webhook_logs
-- Dependencias: auditoria.fn_auditoria_biu(), auditoria.fn_auditoria_aiud()
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

/*
 * by Im@rthe
 * Fecha: 2026-06-12
 * Archivo: 20260612_0001_ades_nevadi.sql
 *
 * Descripcion:
 * FASE 34 - Integraciones SEP y Documentacion ZIP.
 * Crea las tablas public.ades_webhooks y public.ades_webhook_logs para
 * el almacenamiento de webhooks salientes y el registro de telemetria
 * e intentos de entrega de notificaciones.
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ===========================
-- 1. Creacion de tabla y columnas
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_webhooks ();

ALTER TABLE public.ades_webhooks
    ADD COLUMN IF NOT EXISTS id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS url                 VARCHAR(500) NOT NULL,
    ADD COLUMN IF NOT EXISTS event_type          VARCHAR(50) NOT NULL,
    ADD COLUMN IF NOT EXISTS secret_token        VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Columnas de auditoria
    ADD COLUMN IF NOT EXISTS ref                UUID        NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS row_version        INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_creacion   TEXT        NULL,
    ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT      NULL;

CREATE TABLE IF NOT EXISTS public.ades_webhook_logs ();

ALTER TABLE public.ades_webhook_logs
    ADD COLUMN IF NOT EXISTS id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS webhook_id          UUID        NOT NULL,
    ADD COLUMN IF NOT EXISTS event_type          VARCHAR(50) NOT NULL,
    ADD COLUMN IF NOT EXISTS payload             JSONB       NOT NULL,
    ADD COLUMN IF NOT EXISTS status_code          INTEGER     NULL,
    ADD COLUMN IF NOT EXISTS response_body       TEXT        NULL,
    ADD COLUMN IF NOT EXISTS intentos             INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS exitoso             BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fecha_envio          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Columnas de auditoria
    ADD COLUMN IF NOT EXISTS ref                UUID        NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS row_version        INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_creacion   TEXT        NULL,
    ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT      NULL;

-- ===========================
-- 2. Llaves primarias
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_webhooks'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_webhooks
            ADD CONSTRAINT pk_ades_webhooks PRIMARY KEY (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_webhook_logs'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_webhook_logs
            ADD CONSTRAINT pk_ades_webhook_logs PRIMARY KEY (id);
    END IF;
END $$;

-- ===========================
-- 3. Llaves foraneas
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_webhook_logs'::regclass
          AND contype = 'f'
          AND conname = 'fk_webhook_logs_webhook'
    ) THEN
        ALTER TABLE public.ades_webhook_logs
            ADD CONSTRAINT fk_webhook_logs_webhook
            FOREIGN KEY (webhook_id)
            REFERENCES public.ades_webhooks(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- ===========================
-- 4. Comentarios
-- ===========================
COMMENT ON TABLE public.ades_webhooks IS 'Registro de webhooks activos e integraciones salientes (FASE 34).';
COMMENT ON COLUMN public.ades_webhooks.id IS 'Llave primaria UUID.';
COMMENT ON COLUMN public.ades_webhooks.url IS 'URL del servicio receptor.';
COMMENT ON COLUMN public.ades_webhooks.event_type IS 'Tipo de evento de negocio (ej. ALUMNO_INSCRITO).';
COMMENT ON COLUMN public.ades_webhooks.secret_token IS 'Token secreto para firmar con HMAC-SHA256.';
COMMENT ON COLUMN public.ades_webhooks.is_active IS 'Indica si el webhook esta activo.';

COMMENT ON TABLE public.ades_webhook_logs IS 'Historico e intentos de envio de eventos via webhooks.';
COMMENT ON COLUMN public.ades_webhook_logs.id IS 'Llave primaria UUID.';
COMMENT ON COLUMN public.ades_webhook_logs.webhook_id IS 'Referencia al webhook emisor.';
COMMENT ON COLUMN public.ades_webhook_logs.event_type IS 'Tipo de evento enviado.';
COMMENT ON COLUMN public.ades_webhook_logs.payload IS 'Cuerpo JSON del evento enviado.';
COMMENT ON COLUMN public.ades_webhook_logs.status_code IS 'Status HTTP devuelto por el receptor.';
COMMENT ON COLUMN public.ades_webhook_logs.response_body IS 'Cuerpo de la respuesta del servidor receptor.';
COMMENT ON COLUMN public.ades_webhook_logs.intentos IS 'Numero de intentos realizados.';
COMMENT ON COLUMN public.ades_webhook_logs.exitoso IS 'Indica si la entrega fue completada (HTTP 2xx).';
COMMENT ON COLUMN public.ades_webhook_logs.fecha_envio IS 'Fecha y hora del envio.';

-- ===========================
-- 5. Insercion o actualizacion de datos
-- ===========================
-- No aplica insercion de catalogos base para esta fase.

-- ===========================
-- 6. Triggers de auditoria
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = 'public.ades_webhooks'::regclass
    ) THEN
        CREATE TRIGGER audit_biu
        BEFORE INSERT OR UPDATE ON public.ades_webhooks
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'audit_aiud'
          AND tgrelid = 'public.ades_webhooks'::regclass
    ) THEN
        CREATE TRIGGER audit_aiud
        AFTER INSERT OR UPDATE OR DELETE ON public.ades_webhooks
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_aiud();

        ALTER TABLE public.ades_webhooks DISABLE TRIGGER audit_aiud;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = 'public.ades_webhook_logs'::regclass
    ) THEN
        CREATE TRIGGER audit_biu
        BEFORE INSERT OR UPDATE ON public.ades_webhook_logs
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'audit_aiud'
          AND tgrelid = 'public.ades_webhook_logs'::regclass
    ) THEN
        CREATE TRIGGER audit_aiud
        AFTER INSERT OR UPDATE OR DELETE ON public.ades_webhook_logs
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_aiud();

        ALTER TABLE public.ades_webhook_logs DISABLE TRIGGER audit_aiud;
    END IF;
END $$;

-- ===========================
-- 7. Comentarios tecnicos
-- ===========================
-- 1. Script de migracion idempotente.
-- 2. Tablas vacias primero, columnas agregadas por ALTER TABLE.
-- 3. PKs son UUID (gen_random_uuid()).
-- 4. Llaves foraneas a UUID y cascade delete en logs.
-- 5. Trigger audit_biu activo en ambas tablas; audit_aiud deshabilitado.

-- Fin del script

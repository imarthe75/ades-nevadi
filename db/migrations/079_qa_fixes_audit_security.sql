-- =============================================================================
-- Migración 079 — QA Fixes: Seguridad + Auditoría
-- Fecha: 2026-06-16
-- Issues: QA-001 (SEC-10), QA-002 (triggers dupl.), QA-003 (naming), QA-004 (columnas)
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- QA-001 / SEC-10: Proteger auditoria.log_auditoria de ades_admin
-- ─────────────────────────────────────────────────────────────────────────────
REVOKE DELETE, UPDATE, TRUNCATE ON auditoria.log_auditoria FROM ades_admin;

-- Crear rol auditoria_admin para acceso completo (no asignar a servicios de app)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='auditoria_admin') THEN
    CREATE ROLE auditoria_admin;
  END IF;
END $$;
GRANT ALL ON auditoria.log_auditoria TO auditoria_admin;

-- ─────────────────────────────────────────────────────────────────────────────
-- QA-002: Eliminar triggers trg_aud_biu duplicados (70 tablas ya tienen audit_biu)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname AS tbl
    FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    JOIN pg_namespace n ON c.relnamespace = n.oid
    WHERE n.nspname = 'public'
      AND NOT t.tgisinternal
      AND t.tgname = 'trg_aud_biu'
      -- solo borrar si TAMBIÉN tiene audit_biu
      AND EXISTS (
        SELECT 1 FROM pg_trigger t2
        WHERE t2.tgrelid = t.tgrelid
          AND NOT t2.tgisinternal
          AND t2.tgname = 'audit_biu'
      )
    ORDER BY c.relname
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_aud_biu ON public.%I', r.tbl);
  END LOOP;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- QA-003: Renombrar trg_aud_biu → audit_biu donde no existe audit_biu
-- (ades_log_autenticacion, ades_reinscripcion_ciclo)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname AS tbl
    FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    JOIN pg_namespace n ON c.relnamespace = n.oid
    WHERE n.nspname = 'public'
      AND NOT t.tgisinternal
      AND t.tgname = 'trg_aud_biu'
      AND NOT EXISTS (
        SELECT 1 FROM pg_trigger t2
        WHERE t2.tgrelid = t.tgrelid
          AND NOT t2.tgisinternal
          AND t2.tgname = 'audit_biu'
      )
  LOOP
    EXECUTE format(
      'ALTER TRIGGER trg_aud_biu ON public.%I RENAME TO audit_biu',
      r.tbl
    );
  END LOOP;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- QA-004: Agregar columnas de auditoría faltantes en 16 tablas
-- ─────────────────────────────────────────────────────────────────────────────

-- Helper: añade columna solo si no existe
CREATE OR REPLACE FUNCTION pg_temp.add_col_if_missing(
  p_table TEXT, p_col TEXT, p_def TEXT
) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = p_table
      AND column_name  = p_col
  ) THEN
    EXECUTE format('ALTER TABLE public.%I ADD COLUMN %I %s', p_table, p_col, p_def);
  END IF;
END $$;

-- ── ades_ai_conversaciones ───────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_ai_conversaciones','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_ai_conversaciones','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_ai_conversaciones','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_ai_conversaciones','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_ai_conversaciones','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_badge_otorgados ─────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','fecha_creacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_badge_otorgados','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_badges ──────────────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_badges','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_badges','fecha_creacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_badges','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_badges','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_badges','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_calificaciones_historico ────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','row_version',
  'INTEGER NOT NULL DEFAULT 1');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','fecha_creacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_calificaciones_historico','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_cierre_periodo_log ──────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_cierre_periodo_log','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_cierre_periodo_log','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_cierre_periodo_log','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_coordinaciones_area ─────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_coordinaciones_area','ref',
  'UUID NOT NULL DEFAULT uuidv7()');

-- ── ades_cuotas_concepto ─────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_cuotas_concepto','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_cuotas_concepto','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_cuotas_concepto','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_disponibilidad_aula ─────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_disponibilidad_aula','ref',
  'UUID NOT NULL DEFAULT uuidv7()');

-- ── ades_documentos_tipo ─────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_documentos_tipo','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_documentos_tipo','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_documentos_tipo','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_encuesta_respuestas ─────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','row_version',
  'INTEGER NOT NULL DEFAULT 1');
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_encuesta_respuestas','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_lp_progreso ─────────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','row_version',
  'INTEGER NOT NULL DEFAULT 1');
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_lp_progreso','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_notificaciones_sistema ──────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','row_version',
  'INTEGER NOT NULL DEFAULT 1');
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','is_active',
  'BOOLEAN NOT NULL DEFAULT TRUE');
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','usuario_creacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');
SELECT pg_temp.add_col_if_missing('ades_notificaciones_sistema','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_periodos_inscripcion ────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_periodos_inscripcion','ref',
  'UUID NOT NULL DEFAULT uuidv7()');
SELECT pg_temp.add_col_if_missing('ades_periodos_inscripcion','fecha_modificacion',
  'TIMESTAMPTZ NOT NULL DEFAULT now()');
SELECT pg_temp.add_col_if_missing('ades_periodos_inscripcion','usuario_modificacion',
  'TEXT NOT NULL DEFAULT CURRENT_USER');

-- ── ades_planes_mejora ───────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_planes_mejora','ref',
  'UUID NOT NULL DEFAULT uuidv7()');

-- ── ades_sanciones_disciplinarias ────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_sanciones_disciplinarias','ref',
  'UUID NOT NULL DEFAULT uuidv7()');

-- ── ades_seguimiento_plan ────────────────────────────────────────────────────
SELECT pg_temp.add_col_if_missing('ades_seguimiento_plan','ref',
  'UUID NOT NULL DEFAULT uuidv7()');

-- ─────────────────────────────────────────────────────────────────────────────
-- Aplicar audit_biu a las tablas que ahora tienen todas las columnas
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_biu('public.ades_ai_conversaciones');
SELECT auditoria.asignar_biu('public.ades_badge_otorgados');
SELECT auditoria.asignar_biu('public.ades_badges');
SELECT auditoria.asignar_biu('public.ades_calificaciones_historico');
SELECT auditoria.asignar_biu('public.ades_cierre_periodo_log');
SELECT auditoria.asignar_biu('public.ades_coordinaciones_area');
SELECT auditoria.asignar_biu('public.ades_cuotas_concepto');
SELECT auditoria.asignar_biu('public.ades_disponibilidad_aula');
SELECT auditoria.asignar_biu('public.ades_documentos_tipo');
SELECT auditoria.asignar_biu('public.ades_encuesta_respuestas');
SELECT auditoria.asignar_biu('public.ades_lp_progreso');
SELECT auditoria.asignar_biu('public.ades_notificaciones_sistema');
SELECT auditoria.asignar_biu('public.ades_periodos_inscripcion');
SELECT auditoria.asignar_biu('public.ades_planes_mejora');
SELECT auditoria.asignar_biu('public.ades_sanciones_disciplinarias');
SELECT auditoria.asignar_biu('public.ades_seguimiento_plan');

-- ─────────────────────────────────────────────────────────────────────────────
-- Unique index en ref para las tablas que lo requieren
-- ─────────────────────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS ades_ai_conv_ref_key
  ON public.ades_ai_conversaciones(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_badge_ot_ref_key
  ON public.ades_badge_otorgados(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_badges_ref_key
  ON public.ades_badges(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_cal_hist_ref_key
  ON public.ades_calificaciones_historico(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_cierre_log_ref_key
  ON public.ades_cierre_periodo_log(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_coord_area_ref_key
  ON public.ades_coordinaciones_area(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_cuotas_conc_ref_key
  ON public.ades_cuotas_concepto(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_disp_aula_ref_key
  ON public.ades_disponibilidad_aula(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_doc_tipo_ref_key
  ON public.ades_documentos_tipo(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_enc_resp_ref_key
  ON public.ades_encuesta_respuestas(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_lp_prog_ref_key
  ON public.ades_lp_progreso(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_notif_sis_ref_key
  ON public.ades_notificaciones_sistema(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_per_insc_ref_key
  ON public.ades_periodos_inscripcion(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_planes_mej_ref_key
  ON public.ades_planes_mejora(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_sanciones_ref_key
  ON public.ades_sanciones_disciplinarias(ref);
CREATE UNIQUE INDEX IF NOT EXISTS ades_seg_plan_ref_key
  ON public.ades_seguimiento_plan(ref);

-- ─────────────────────────────────────────────────────────────────────────────
-- Verificación final
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  v_sin_biu   INTEGER;
  v_sin_col   INTEGER;
  v_dual      INTEGER;
BEGIN
  SELECT count(*) INTO v_sin_biu
  FROM auditoria.reporte_cobertura()
  WHERE elegible = true AND tiene_biu = false;

  SELECT count(*) INTO v_sin_col
  FROM auditoria.reporte_cobertura()
  WHERE estado = 'SIN_COLUMNAS_AUDITORIA';

  SELECT count(*) INTO v_dual
  FROM (
    SELECT c.relname
    FROM pg_trigger t JOIN pg_class c ON t.tgrelid=c.oid
    JOIN pg_namespace n ON c.relnamespace=n.oid
    WHERE n.nspname='public' AND NOT t.tgisinternal
      AND t.tgname IN ('audit_biu','trg_aud_biu')
    GROUP BY c.relname HAVING count(*) > 1
  ) x;

  RAISE NOTICE '=== 079 QA Fixes — Verificación ===';
  RAISE NOTICE 'Tablas sin BIU (elegibles):   %  (esperado: 0)', v_sin_biu;
  RAISE NOTICE 'Tablas sin columnas auditoría: %  (esperado: 1 — audit_log)', v_sin_col;
  RAISE NOTICE 'Tablas con trigger duplicado:  %  (esperado: 0)', v_dual;
END $$;

COMMIT;

COMMENT ON TABLE auditoria.log_auditoria IS
  'Tabla de log inmutable. Solo rol auditoria_admin puede eliminar registros.';

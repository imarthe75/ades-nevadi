-- ============================================================
-- Migración 006 — Badges/Gamificación
-- 2026-06-04
-- ============================================================

-- ── Catálogo de insignias ────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_badges (
    id              UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    descripcion     TEXT,
    icono           VARCHAR(60)  NOT NULL DEFAULT 'pi-star',
    color           VARCHAR(20)  NOT NULL DEFAULT '#D02030',
    tipo            VARCHAR(30)  NOT NULL CHECK (tipo IN ('ASISTENCIA','ACADEMICO','CONDUCTA','PARTICIPACION','ESPECIAL')),
    criterio_tipo   VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' CHECK (criterio_tipo IN ('AUTOMATICO','MANUAL')),
    criterio_metrica VARCHAR(60),   -- pct_asistencia | promedio_general | sin_reportes_conducta
    criterio_valor  NUMERIC(6,2),  -- 90.0 para "≥ 90 % asistencia"
    plantel_id      UUID         REFERENCES ades_planteles(id),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INT          NOT NULL DEFAULT 1
);

COMMENT ON TABLE  ades_badges IS 'Catálogo de insignias/gamificación del sistema';
COMMENT ON COLUMN ades_badges.criterio_metrica IS 'Métrica evaluada automáticamente: pct_asistencia, promedio_general, sin_reportes_conducta';
COMMENT ON COLUMN ades_badges.criterio_valor   IS 'Umbral mínimo para otorgar el badge automático';

-- ── Insignias otorgadas a alumnos ────────────────────────────
CREATE TABLE IF NOT EXISTS ades_badge_otorgados (
    id              UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    badge_id        UUID        NOT NULL REFERENCES ades_badges(id) ON DELETE CASCADE,
    estudiante_id   UUID        NOT NULL REFERENCES ades_estudiantes(id) ON DELETE CASCADE,
    ciclo_id        UUID                 REFERENCES ades_ciclos_escolares(id),
    motivo          TEXT,
    otorgado_por    UUID                 REFERENCES ades_usuarios(id),
    fecha_otorgado  TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version     INT         NOT NULL DEFAULT 1,
    UNIQUE (badge_id, estudiante_id, ciclo_id)
);

COMMENT ON TABLE ades_badge_otorgados IS 'Registro de insignias otorgadas a alumnos por ciclo escolar';

CREATE INDEX IF NOT EXISTS idx_badge_otorgados_estudiante ON ades_badge_otorgados(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_badge_otorgados_badge      ON ades_badge_otorgados(badge_id);
CREATE INDEX IF NOT EXISTS idx_badge_otorgados_ciclo      ON ades_badge_otorgados(ciclo_id);
CREATE INDEX IF NOT EXISTS idx_badges_tipo                ON ades_badges(tipo);

-- ── Seeds: 8 insignias estándar ──────────────────────────────
INSERT INTO ades_badges (nombre, descripcion, icono, color, tipo, criterio_tipo, criterio_metrica, criterio_valor)
VALUES
  ('Asistencia Perfecta',   '100 % de asistencia en el periodo',             'pi-calendar-check', '#27AE60', 'ASISTENCIA',    'AUTOMATICO', 'pct_asistencia',        100.00),
  ('Asistencia Destacada',  '90 % o más de asistencia en el periodo',         'pi-calendar-plus',  '#2ECC71', 'ASISTENCIA',    'AUTOMATICO', 'pct_asistencia',         90.00),
  ('Excelencia Académica',  'Promedio general de 9.0 o superior',             'pi-star-fill',      '#F39C12', 'ACADEMICO',     'AUTOMATICO', 'promedio_general',         9.00),
  ('Buen Rendimiento',      'Promedio general de 8.0 o superior',             'pi-thumbs-up',      '#3498DB', 'ACADEMICO',     'AUTOMATICO', 'promedio_general',         8.00),
  ('Ciudadano Ejemplar',    'Sin reportes de conducta negativos en el ciclo', 'pi-shield',         '#9B59B6', 'CONDUCTA',      'AUTOMATICO', 'sin_reportes_conducta',    0.00),
  ('Líder del Salón',       'Reconocimiento especial del docente',            'pi-users',          '#1ABC9C', 'PARTICIPACION', 'MANUAL',     NULL,                       NULL),
  ('Mérito Especial',       'Logro extraordinario reconocido por dirección',  'pi-trophy',         '#E67E22', 'ESPECIAL',      'MANUAL',     NULL,                       NULL),
  ('Deportista Destacado',  'Participación y logros en actividades deportivas','pi-bolt',          '#E74C3C', 'ESPECIAL',      'MANUAL',     NULL,                       NULL)
ON CONFLICT DO NOTHING;

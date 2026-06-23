-- =============================================================================
-- Migración 089 — NEM Fase 3: Evaluación cualitativa 1°-2° primaria
--
-- 1. Tabla ades_config  → variables de configuración del sistema
-- 2. Seed escalas cualitativas NEM (PRIMARIA 1°-2°)
-- 3. Columna nivel_logro en ades_calificaciones_periodo
-- =============================================================================

-- ── 1. Tabla de configuración del sistema ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_config (
  id                   uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
  clave                text        NOT NULL UNIQUE,
  valor                jsonb       NOT NULL,
  descripcion          text,
  grupo                text        NOT NULL DEFAULT 'general',
  tipo_valor           text        NOT NULL DEFAULT 'text'
                         CHECK (tipo_valor IN ('text','number','boolean','json','enum')),
  es_editable          boolean     NOT NULL DEFAULT TRUE,
  ref                  uuid,
  row_version          integer     NOT NULL DEFAULT 1,
  fecha_creacion       timestamptz NOT NULL DEFAULT now(),
  fecha_modificacion   timestamptz NOT NULL DEFAULT now(),
  usuario_creacion     text        NOT NULL DEFAULT current_user,
  usuario_modificacion text        NOT NULL DEFAULT current_user
);

COMMENT ON TABLE ades_config IS 'Variables de configuración del sistema ADES (editables desde UI admin)';

SELECT auditoria.asignar_biu('public.ades_config');

-- ── 2. Config por defecto — evaluación cualitativa NEM ───────────────────────

INSERT INTO ades_config (clave, valor, descripcion, grupo, tipo_valor) VALUES
  ('EVAL_CUAL_GRADOS_PRIMARIA',
   '[1, 2]'::jsonb,
   'Números de grado de primaria que usan evaluación cualitativa NEM (ej. [1,2])',
   'evaluacion_cualitativa', 'json'),

  ('EVAL_CUAL_MOSTRAR_EQUIVALENCIA',
   'true'::jsonb,
   'Mostrar equivalencia numérica (equiv_num) junto al descriptor cualitativo en la libreta y boleta',
   'evaluacion_cualitativa', 'boolean'),

  ('EVAL_CUAL_APLICAR_TODAS_MATERIAS',
   'true'::jsonb,
   'Aplicar evaluación cualitativa a todas las materias del grado. Si false, solo a las de campo_formativo configurado',
   'evaluacion_cualitativa', 'boolean')

ON CONFLICT (clave) DO NOTHING;

-- ── 3. Seed escala cualitativa NEM 1°-2° primaria ───────────────────────────

INSERT INTO ades_escalas_evaluacion
  (id, nombre, nivel_educativo, descripcion, valores_json, is_active)
VALUES (
  uuidv7(),
  'NEM Primaria 1°-2° — Escala Cualitativa',
  'PRIMARIA',
  'Escala cualitativa para 1er y 2do grado de primaria según Plan NEM SEP 2022 (DGAIR/SIGED)',
  '[
    {
      "nivel": "A",
      "label": "Avanzado",
      "descripcion": "Logra los aprendizajes con autonomía y los aplica en situaciones nuevas",
      "min": 9.0, "max": 10.0, "equiv_num": 10.0,
      "color": "#15803d"
    },
    {
      "nivel": "B",
      "label": "Satisfactorio",
      "descripcion": "Logra los aprendizajes esperados con apoyo mínimo del docente",
      "min": 7.0, "max": 8.9, "equiv_num": 8.0,
      "color": "#0369a1"
    },
    {
      "nivel": "C",
      "label": "En proceso",
      "descripcion": "Está en proceso de lograr los aprendizajes; requiere mayor orientación del docente",
      "min": 6.0, "max": 6.9, "equiv_num": 6.5,
      "color": "#b45309"
    },
    {
      "nivel": "D",
      "label": "Requiere apoyo",
      "descripcion": "Requiere intervención sistemática y personalizada para alcanzar los aprendizajes",
      "min": 0.0, "max": 5.9, "equiv_num": 5.0,
      "color": "#dc2626"
    }
  ]'::jsonb,
  true
)
ON CONFLICT DO NOTHING;

-- ── 4. Agregar nivel_logro a ades_calificaciones_periodo ─────────────────────
-- La tabla está particionada por rango de fecha; ALTER en la raíz propaga a particiones en PG18

ALTER TABLE ades_calificaciones_periodo
  ADD COLUMN IF NOT EXISTS nivel_logro varchar(1)
    CHECK (nivel_logro IN ('A', 'B', 'C', 'D'));

COMMENT ON COLUMN ades_calificaciones_periodo.nivel_logro
  IS 'Descriptor cualitativo NEM: A=Avanzado B=Satisfactorio C=En proceso D=Requiere apoyo';

-- ── 5. Índice para búsquedas por nivel_logro ─────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_cal_periodo_nivel_logro
  ON ades_calificaciones_periodo (nivel_logro)
  WHERE nivel_logro IS NOT NULL;

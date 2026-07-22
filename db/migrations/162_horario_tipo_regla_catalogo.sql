-- 162_horario_tipo_regla_catalogo.sql
-- Catálogo en BD de los tipos de regla que el motor de horarios (Timefold) interpreta.
-- Reemplaza al enum Java embebido (convención ADES: catálogos en base de datos, no
-- enums en código). Es la fuente de verdad de QUÉ tipos son válidos/soportados; cada
-- `codigo` aquí debe tener su método-restricción correspondiente en
-- HorarioConstraintProvider. HorarioReglaController valida el `tipo` de una regla
-- nueva contra esta tabla (rechaza 422 si no existe) y el endpoint GET /reglas/tipos
-- la devuelve para que el frontend marque cada regla como soportada / sin efecto.

BEGIN;

CREATE TABLE IF NOT EXISTS ades_horario_tipo_regla (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(50) NOT NULL UNIQUE,
    dura_por_defecto BOOLEAN NOT NULL DEFAULT TRUE,
    descripcion TEXT NOT NULL,
    params_requeridos JSONB NOT NULL DEFAULT '[]'::jsonb,
    orden INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version INTEGER DEFAULT 0,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion VARCHAR(100),
    fecha_modificacion TIMESTAMP WITH TIME ZONE,
    usuario_modificacion VARCHAR(100),
    ref UUID DEFAULT gen_random_uuid()
);

COMMENT ON TABLE ades_horario_tipo_regla IS
    'Catálogo de tipos de regla soportados por el motor de horarios Timefold. '
    'Cada codigo debe tener su restricción implementada en HorarioConstraintProvider.';

DROP TRIGGER IF EXISTS ades_horario_tipo_regla_audit_biu ON ades_horario_tipo_regla;
CREATE TRIGGER ades_horario_tipo_regla_audit_biu
    BEFORE INSERT OR UPDATE ON ades_horario_tipo_regla
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

-- Seed de los 13 tipos soportados (9 base + 4 nuevos de esta sesión). Idempotente por
-- ON CONFLICT (codigo). params_requeridos son las claves que el ConstraintProvider lee.
INSERT INTO ades_horario_tipo_regla (codigo, dura_por_defecto, descripcion, params_requeridos, orden) VALUES
  ('dias_permitidos',             TRUE,  'Restringe una materia a ciertos días de la semana',                         '["materia","dias"]',           10),
  ('dias_no_consecutivos',        TRUE,  'La misma materia no puede caer en días consecutivos para el grupo',         '["materia"]',                  20),
  ('ventana_horaria',             TRUE,  'Una materia solo puede impartirse antes de / después de cierta hora',       '["materia","modo","hora"]',    30),
  ('bloque_contiguo',             FALSE, 'Premia que las horas de ciertas materias queden contiguas en el día',       '["materias"]',                 40),
  ('max_horas_dia',               TRUE,  'Límite de horas de una materia por día para un grupo',                      '["default"]',                  50),
  ('sincronizar_materia',         TRUE,  'La misma materia debe caer en la misma posición en grupos paralelos',       '["materia"]',                  60),
  ('ventana_horaria_docente',     TRUE,  'Un docente solo puede dar clase antes de / después de cierta hora',         '["profesor_id","modo","hora"]',70),
  ('dias_no_permitidos_docente',  TRUE,  'Un docente no puede dar clase en ciertos días de la semana',                '["profesor_id","dias"]',       80),
  ('materia_fraccionada_30min',   TRUE,  'La materia debe tener un bloque de 50 min y uno de 30 min por semana',      '["materia"]',                  90),
  -- Nuevos (paridad con aSc TimeTables)
  ('distribucion_minima',         FALSE, 'Repartir una materia en al menos N días distintos de la semana (calidad)',   '["materia","min_dias"]',      100),
  ('lecciones_dia_docente',       TRUE,  'Mín/máx de clases por día para un docente',                                 '["profesor_id","min","max"]', 110),
  ('dias_laborables_docente',     TRUE,  'Un docente trabaja a lo más N días por semana',                             '["profesor_id","max_dias"]',  120),
  ('preferencia_horaria_docente', FALSE, 'Días que un docente prefiere evitar (preferencia suave, no prohibición)',    '["profesor_id","evita_dias"]',130)
ON CONFLICT (codigo) DO UPDATE SET
    dura_por_defecto  = EXCLUDED.dura_por_defecto,
    descripcion       = EXCLUDED.descripcion,
    params_requeridos = EXCLUDED.params_requeridos,
    orden             = EXCLUDED.orden,
    is_active         = TRUE;

COMMIT;

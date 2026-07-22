-- 165_horario_turno_catalogo.sql
-- Catálogo en BD de los turnos escolares posibles (convención ADES: catálogos en base
-- de datos, no enums en código). Hasta ahora `turno` era texto libre en varias tablas
-- (ades_horario_franjas, ades_grupos, ades_profesores, etc.) y en la práctica solo se
-- usaba 'MATUTINO'. Este catálogo es la fuente de verdad para poblar el select del
-- diálogo de Franjas Horarias (Administración) y, a futuro, cualquier otro campo turno.
-- El endpoint GET /horario-franjas/turnos lo sirve al frontend.

BEGIN;

CREATE TABLE IF NOT EXISTS ades_horario_turno (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL,
    descripcion TEXT,
    orden INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version INTEGER DEFAULT 0,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion VARCHAR(100),
    fecha_modificacion TIMESTAMP WITH TIME ZONE,
    usuario_modificacion VARCHAR(100),
    ref UUID DEFAULT gen_random_uuid()
);

COMMENT ON TABLE ades_horario_turno IS
    'Catálogo de turnos escolares posibles (Matutino, Vespertino, etc.). Fuente de '
    'verdad para el select de turno en franjas horarias y demás campos turno.';

DROP TRIGGER IF EXISTS ades_horario_turno_audit_biu ON ades_horario_turno;
CREATE TRIGGER ades_horario_turno_audit_biu
    BEFORE INSERT OR UPDATE ON ades_horario_turno
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

-- Seed de los turnos estándar del control escolar mexicano (SEP/UAEMEX). Solo MATUTINO
-- está en uso hoy, pero el catálogo lista todos los que pudieran existir. Idempotente
-- por ON CONFLICT (codigo).
INSERT INTO ades_horario_turno (codigo, nombre, descripcion, orden) VALUES
  ('MATUTINO',        'Matutino',        'Jornada de la mañana',                            10),
  ('VESPERTINO',      'Vespertino',      'Jornada de la tarde',                             20),
  ('NOCTURNO',        'Nocturno',        'Jornada de la noche',                             30),
  ('MIXTO',           'Mixto',           'Combina jornada matutina y vespertina',           40),
  ('DISCONTINUO',     'Discontinuo',     'Jornada con interrupción intermedia',             50),
  ('TIEMPO_COMPLETO', 'Tiempo Completo', 'Jornada ampliada de mañana y tarde continua',     60)
ON CONFLICT (codigo) DO UPDATE SET
    nombre      = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    orden       = EXCLUDED.orden,
    is_active   = TRUE;

COMMIT;

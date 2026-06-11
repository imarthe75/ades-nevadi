-- =============================================================================
-- Migración 013 — Funcionalidad académica completa
--
--   1. ades_bajas              — Bajas / cambios de estatus del alumno
--   2. ades_extraordinarias    — Exámenes extraordinarios / regularización
--   3. ades_documentos_tipo    — Catálogo de documentos requeridos por nivel
--   4. ades_expediente_docs    — Estado de documentos por alumno
--   5. ades_cambios_grupo      — Historial de traslados entre grupos
--   6. ades_constancias        — Constancias y documentos oficiales emitidos
-- =============================================================================

-- ── 1. Bajas de alumnos ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_bajas (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
  inscripcion_id      UUID        REFERENCES ades_inscripciones(id),
  tipo_baja           VARCHAR(30) NOT NULL,  -- TEMPORAL DEFINITIVA TRASLADO DESERCION
  motivo              TEXT,
  fecha_efectiva      DATE        NOT NULL,
  fecha_reingreso     DATE,                  -- para bajas temporales
  plantel_destino     VARCHAR(200),          -- si es traslado
  clave_ct_destino    VARCHAR(20),
  autorizado_por_id   UUID        REFERENCES ades_usuarios(id),
  observaciones       TEXT,
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER     DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_bajas_estudiante ON ades_bajas(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_bajas_fecha      ON ades_bajas(fecha_efectiva DESC);

COMMENT ON TABLE ades_bajas IS
  'Registro de bajas, traslados y cambios de estatus del alumno con autorización y motivo.';

-- ── 2. Exámenes extraordinarios ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_extraordinarias (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
  materia_id          UUID        NOT NULL REFERENCES ades_materias(id),
  ciclo_escolar_id    UUID        NOT NULL REFERENCES ades_ciclos_escolares(id),
  grupo_id            UUID        REFERENCES ades_grupos(id),   -- grupo de origen
  tipo_examen         VARCHAR(30) DEFAULT 'EXTRAORDINARIO',    -- EXTRAORDINARIO REGULARIZACION TITULO
  calificacion_previa NUMERIC(4,2),                            -- calificación normal que no acreditó
  fecha_examen        DATE,
  calificacion        NUMERIC(4,2),                            -- resultado del extraordinario
  acredita            BOOLEAN,
  aplicado_por_id     UUID        REFERENCES ades_usuarios(id),
  observaciones       TEXT,
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER     DEFAULT 1,
  -- Constraint: un alumno no puede tener dos extraordinarios de la misma materia en el mismo ciclo
  UNIQUE (estudiante_id, materia_id, ciclo_escolar_id, tipo_examen)
);

CREATE INDEX IF NOT EXISTS idx_extraordinarias_estudiante
  ON ades_extraordinarias(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_extraordinarias_ciclo
  ON ades_extraordinarias(ciclo_escolar_id, materia_id);

COMMENT ON TABLE ades_extraordinarias IS
  'Exámenes extraordinarios y de regularización. SEP limita a 3 por ciclo en secundaria/preparatoria.';

-- ── 3. Catálogo de documentos requeridos ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_documentos_tipo (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  nombre_documento    VARCHAR(150) NOT NULL,
  descripcion         TEXT,
  nivel_educativo_id  UUID        REFERENCES ades_niveles_educativos(id),  -- NULL = aplica a todos
  obligatorio         BOOLEAN     DEFAULT TRUE,
  aplica_inscripcion  BOOLEAN     DEFAULT TRUE,   -- se pide al inscribirse
  aplica_egreso       BOOLEAN     DEFAULT FALSE,  -- se pide al egresar
  orden               SMALLINT    DEFAULT 1,
  -- Auditoría
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  row_version         INTEGER     DEFAULT 1
);

-- Documentos estándar SEP (aplican a todos los niveles)
INSERT INTO ades_documentos_tipo (nombre_documento, descripcion, obligatorio, aplica_inscripcion, orden)
VALUES
  ('Acta de Nacimiento',         'Original o copia certificada', TRUE,  TRUE,  1),
  ('CURP impresa',               'Hoja de la CURP actualizada del RENAPO', TRUE, TRUE, 2),
  ('Fotografías recientes',      '3 fotografías tamaño infantil en blanco y negro', TRUE, TRUE, 3),
  ('Comprobante de domicilio',   'Recibo de agua, luz o teléfono reciente (< 3 meses)', TRUE, TRUE, 4),
  ('Certificado de estudios',    'Certificado del nivel anterior', TRUE, TRUE, 5),
  ('Cartilla de vacunación',     'Cartilla nacional de vacunación al día', FALSE, TRUE, 6),
  ('CURP del padre/tutor',       'Para menores de edad', FALSE, TRUE, 7),
  ('Comprobante de traslado',    'Para alumnos que se inscriben por traslado', FALSE, TRUE, 8)
ON CONFLICT DO NOTHING;

-- ── 4. Estado de documentos por alumno ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_expediente_docs (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
  documento_tipo_id   UUID        NOT NULL REFERENCES ades_documentos_tipo(id),
  ciclo_escolar_id    UUID        REFERENCES ades_ciclos_escolares(id),  -- ciclo de inscripción
  estatus             VARCHAR(20) DEFAULT 'PENDIENTE', -- PENDIENTE ENTREGADO INCOMPLETO RECHAZADO EXENTO
  fecha_entrega       DATE,
  fecha_vencimiento   DATE,                           -- para docs con vigencia (comprobante domicilio)
  archivo_id          UUID        REFERENCES ades_archivos(id),  -- archivo digital adjunto
  observaciones       VARCHAR(500),
  verificado_por_id   UUID        REFERENCES ades_usuarios(id),
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER     DEFAULT 1,
  UNIQUE (estudiante_id, documento_tipo_id, ciclo_escolar_id)
);

CREATE INDEX IF NOT EXISTS idx_expdocs_estudiante
  ON ades_expediente_docs(estudiante_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_expdocs_pendientes
  ON ades_expediente_docs(estatus) WHERE estatus IN ('PENDIENTE', 'INCOMPLETO');

COMMENT ON TABLE ades_expediente_docs IS
  'Estado de cada documento requerido del expediente por alumno y ciclo.';

-- ── 5. Historial de cambios de grupo ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_cambios_grupo (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  inscripcion_id      UUID        NOT NULL REFERENCES ades_inscripciones(id),
  estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
  grupo_origen_id     UUID        NOT NULL REFERENCES ades_grupos(id),
  grupo_destino_id    UUID        NOT NULL REFERENCES ades_grupos(id),
  fecha_cambio        DATE        NOT NULL,
  motivo              VARCHAR(200),
  autorizado_por_id   UUID        REFERENCES ades_usuarios(id),
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  row_version         INTEGER     DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_cambios_grupo_estudiante
  ON ades_cambios_grupo(estudiante_id);

-- ── 6. Constancias y documentos oficiales emitidos ───────────────────────────
CREATE TABLE IF NOT EXISTS ades_constancias (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
  tipo_constancia     VARCHAR(50) NOT NULL,  -- ESTUDIOS CALIFICACIONES CONDUCTA TRASLADO BAJA INSCRIPCION
  folio               VARCHAR(50) UNIQUE,
  ciclo_escolar_id    UUID        REFERENCES ades_ciclos_escolares(id),
  fecha_emision       DATE        DEFAULT CURRENT_DATE,
  fecha_vencimiento   DATE,
  solicitada_por      VARCHAR(200),          -- nombre de quien la solicita
  proposito           VARCHAR(200),          -- para qué trámite
  emitida_por_id      UUID        REFERENCES ades_usuarios(id),
  archivo_id          UUID        REFERENCES ades_archivos(id),  -- PDF generado
  entregada           BOOLEAN     DEFAULT FALSE,
  fecha_entrega       DATE,
  observaciones       TEXT,
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER     DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_constancias_estudiante
  ON ades_constancias(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_constancias_tipo
  ON ades_constancias(tipo_constancia, fecha_emision DESC);

COMMENT ON TABLE ades_constancias IS
  'Constancias y documentos oficiales emitidos por la escuela: de estudio, de calificaciones, traslado, etc.';

-- Secuencia para folios de constancias por año
CREATE SEQUENCE IF NOT EXISTS seq_constancia_folio START 1;

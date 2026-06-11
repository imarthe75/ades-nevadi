-- =============================================================================
-- Migración 014 — Administración y cumplimiento
--
--   1. ades_cuotas_concepto    — Catálogo de conceptos de cobro
--   2. ades_cuotas_pagos       — Registro de pagos por alumno
--   3. ades_solicitudes_trámites — Trámites administrativos con flujo
--   4. ades_audit_log          — Log de auditoría de todas las mutaciones
--   5. ades_periodos_inscripcion — Ventanas de inscripción por ciclo/nivel
--   6. Mejoras a tablas existentes
-- =============================================================================

-- ── 1. Catálogo de conceptos de cobro ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_cuotas_concepto (
  id                  UUID          PRIMARY KEY DEFAULT uuidv7(),
  nombre_concepto     VARCHAR(100)  NOT NULL,
  descripcion         TEXT,
  monto_sugerido      NUMERIC(10,2),
  periodicidad        VARCHAR(20)   DEFAULT 'MENSUAL',  -- UNICA MENSUAL BIMESTRAL ANUAL
  nivel_educativo_id  UUID          REFERENCES ades_niveles_educativos(id),  -- NULL = todos
  plantel_id          UUID          REFERENCES ades_planteles(id),           -- NULL = todos
  obligatorio         BOOLEAN       DEFAULT FALSE,
  aplica_ciclo        BOOLEAN       DEFAULT TRUE,
  -- Auditoría
  is_active           BOOLEAN       DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ   DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  row_version         INTEGER       DEFAULT 1
);

-- Conceptos estándar
INSERT INTO ades_cuotas_concepto (nombre_concepto, descripcion, periodicidad, obligatorio) VALUES
  ('Inscripción',         'Cuota de inscripción al ciclo escolar',     'UNICA',    FALSE),
  ('Cuota de mantenimiento', 'Cuota mensual para mantenimiento del plantel', 'MENSUAL', FALSE),
  ('Material didáctico',  'Material para actividades del ciclo',       'UNICA',    FALSE),
  ('Seguro escolar',      'Póliza de seguro de accidentes escolares',  'ANUAL',    FALSE),
  ('Evento graduación',   'Aportación para ceremonia de graduación',   'UNICA',    FALSE)
ON CONFLICT DO NOTHING;

-- ── 2. Registro de pagos ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_cuotas_pagos (
  id                  UUID          PRIMARY KEY DEFAULT uuidv7(),
  estudiante_id       UUID          NOT NULL REFERENCES ades_estudiantes(id),
  concepto_id         UUID          NOT NULL REFERENCES ades_cuotas_concepto(id),
  ciclo_escolar_id    UUID          REFERENCES ades_ciclos_escolares(id),
  -- Importes
  monto_cobrado       NUMERIC(10,2) NOT NULL,
  monto_pagado        NUMERIC(10,2) DEFAULT 0,
  descuento           NUMERIC(10,2) DEFAULT 0,
  motivo_descuento    VARCHAR(200),
  saldo_pendiente     NUMERIC(10,2) GENERATED ALWAYS AS
                        (monto_cobrado - descuento - monto_pagado) STORED,
  -- Fechas
  fecha_vencimiento   DATE,
  fecha_pago          DATE,
  -- Pago
  estatus             VARCHAR(20)   DEFAULT 'PENDIENTE',  -- PENDIENTE PARCIAL PAGADO CANCELADO EXENTO
  forma_pago          VARCHAR(30),   -- EFECTIVO TRANSFERENCIA TARJETA CHEQUE
  referencia_pago     VARCHAR(100),  -- número de referencia bancaria / recibo
  recibo_id           UUID          REFERENCES ades_archivos(id),
  registrado_por_id   UUID          REFERENCES ades_usuarios(id),
  -- Auditoría
  ref                 UUID          UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN       DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ   DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ   DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER       DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_pagos_estudiante
  ON ades_cuotas_pagos(estudiante_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_pagos_estatus
  ON ades_cuotas_pagos(estatus, fecha_vencimiento)
  WHERE estatus IN ('PENDIENTE', 'PARCIAL');
CREATE INDEX IF NOT EXISTS idx_pagos_ciclo
  ON ades_cuotas_pagos(ciclo_escolar_id, concepto_id);

COMMENT ON TABLE ades_cuotas_pagos IS
  'Registro de cobros y pagos escolares por alumno. No es sistema de facturación — solo control interno.';

-- ── 3. Solicitudes y trámites administrativos ────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_solicitudes_tramites (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  -- Solicitante
  usuario_id          UUID        NOT NULL REFERENCES ades_usuarios(id),
  estudiante_id       UUID        REFERENCES ades_estudiantes(id),  -- NULL si es trámite general
  -- Trámite
  tipo_tramite        VARCHAR(50) NOT NULL,
  -- CONSTANCIA_ESTUDIOS CONSTANCIA_CALIFICACIONES CERTIFICADO TRASLADO BAJA_TEMPORAL BAJA_DEFINITIVA
  -- REINSCRIPCION CAMBIO_GRUPO CORRECCION_DATOS DUPLICADO_CREDENCIAL OTRO
  descripcion         TEXT,
  datos_adicionales   JSONB,       -- campos específicos del trámite
  -- Flujo
  estatus             VARCHAR(30) DEFAULT 'RECIBIDA',
  -- RECIBIDA EN_PROCESO APROBADA RECHAZADA ENTREGADA CANCELADA
  prioridad           VARCHAR(10) DEFAULT 'NORMAL',   -- URGENTE NORMAL BAJA
  fecha_limite        DATE,
  -- Asignación
  asignado_a_id       UUID        REFERENCES ades_usuarios(id),
  -- Resolución
  fecha_resolucion    TIMESTAMPTZ,
  resolucion          TEXT,
  resuelto_por_id     UUID        REFERENCES ades_usuarios(id),
  archivo_resultado_id UUID       REFERENCES ades_archivos(id),
  -- Auditoría
  ref                 UUID        UNIQUE DEFAULT uuidv7(),
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  fecha_modificacion      TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  usuario_modificacion VARCHAR(150),
  row_version         INTEGER     DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_tramites_usuario
  ON ades_solicitudes_tramites(usuario_id, estatus);
CREATE INDEX IF NOT EXISTS idx_tramites_estatus
  ON ades_solicitudes_tramites(estatus, prioridad, fecha_limite)
  WHERE estatus IN ('RECIBIDA','EN_PROCESO');

COMMENT ON TABLE ades_solicitudes_tramites IS
  'Flujo de trámites administrativos: constancias, certificados, bajas, cambios de grupo.
   Cada trámite tiene ciclo de vida: RECIBIDA→EN_PROCESO→APROBADA→ENTREGADA.';

-- ── 4. Log de auditoría ────────────────────────────────────────────────────────
-- Nota: sin particionamiento (escala suficiente para Instituto Nevadi).
-- Si se requiere purga por antigüedad, usar pg_partman en el futuro.
CREATE TABLE IF NOT EXISTS ades_audit_log (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  usuario_id          UUID,               -- NULL si es operación de sistema
  nombre_usuario      VARCHAR(150),
  ip_origen           INET,
  accion              VARCHAR(10) NOT NULL, -- INSERT UPDATE DELETE LOGIN LOGOUT EXPORT
  entidad             VARCHAR(100) NOT NULL,
  entidad_id          UUID,
  payload_anterior    JSONB,
  payload_nuevo       JSONB,
  campos_modificados  TEXT[],
  endpoint            VARCHAR(200),
  metodo_http         VARCHAR(10),
  codigo_respuesta    SMALLINT,
  duracion_ms         INTEGER,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_usuario
  ON ades_audit_log(usuario_id, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entidad
  ON ades_audit_log(entidad, entidad_id, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_audit_accion
  ON ades_audit_log(accion, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_audit_fecha
  ON ades_audit_log(fecha_creacion DESC);

COMMENT ON TABLE ades_audit_log IS
  'Log inmutable de mutaciones del sistema. No usar UPDATE/DELETE. Retención: 5 años mínimo (SEP).';

-- ── 5. Períodos de inscripción ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_periodos_inscripcion (
  id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
  ciclo_escolar_id    UUID        NOT NULL REFERENCES ades_ciclos_escolares(id),
  plantel_id          UUID        REFERENCES ades_planteles(id),  -- NULL = todos los planteles
  nombre_periodo      VARCHAR(100) DEFAULT 'Inscripción ordinaria',
  fecha_inicio        DATE        NOT NULL,
  fecha_fin           DATE        NOT NULL,
  tipo                VARCHAR(30) DEFAULT 'ORDINARIA',  -- ORDINARIA EXTRAORDINARIA REINSCRIPCION
  cupo_maximo         INTEGER,                           -- NULL = sin límite
  activo              BOOLEAN     DEFAULT TRUE,
  -- Auditoría
  is_active           BOOLEAN     DEFAULT TRUE,
  fecha_creacion          TIMESTAMPTZ DEFAULT NOW(),
  usuario_creacion    VARCHAR(150),
  row_version         INTEGER     DEFAULT 1,
  CONSTRAINT chk_periodo_fechas CHECK (fecha_fin >= fecha_inicio)
);

-- ── 6. Mejoras a tablas existentes ────────────────────────────────────────────

-- Tabla de reportes de conducta: agregar tipo de falta SEP
ALTER TABLE ades_reportes_conducta
  ADD COLUMN IF NOT EXISTS tipo_falta_sep    VARCHAR(30),  -- LEVE GRAVE MUY_GRAVE (Reglamento SEP)
  ADD COLUMN IF NOT EXISTS requiere_citatorio BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS citatorio_enviado  BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS fecha_citatorio    DATE;

-- Tabla de promociones: agregar promedio y materias reprobadas del ciclo
ALTER TABLE ades_promociones_pendientes
  ADD COLUMN IF NOT EXISTS promedio_ciclo      NUMERIC(4,2),
  ADD COLUMN IF NOT EXISTS materias_reprobadas SMALLINT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS tipo_resolucion     VARCHAR(30);  -- PROMOVIDO CONDICIONADO REPROBADO NO_ACREDITADO

-- Tabla de clases: marcar si fue efectivamente impartida vs. cancelada
ALTER TABLE ades_clases
  ADD COLUMN IF NOT EXISTS impartida         BOOLEAN DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS motivo_cancelacion VARCHAR(100);

-- Grupos: turno como NOT NULL con default (ya existe el campo, solo asegurar)
-- (no modificar si ya tiene datos)

-- =============================================================================
-- Migración 011 — Datos complementarios por función/rol
--   · ades_personas:     contacto, estado civil, lugar de nacimiento
--   · ades_estudiantes:  NSS, tipo sangre, salud, procedencia, beca, etnia
--   · ades_profesores:   RFC, NSS, cédula, nómina, escolaridad
--   · ades_contactos_emergencia: tutores y contactos de alumnos/personas
-- =============================================================================

-- ── 1. Persona — campos de contacto y civiles ─────────────────────────────────
ALTER TABLE ades_personas
  ADD COLUMN IF NOT EXISTS telefono              VARCHAR(15),
  ADD COLUMN IF NOT EXISTS email_personal        VARCHAR(255),
  ADD COLUMN IF NOT EXISTS estado_civil          VARCHAR(20),   -- SOLTERO CASADO UNION_LIBRE DIVORCIADO VIUDO
  ADD COLUMN IF NOT EXISTS municipio_nacimiento  VARCHAR(100),
  ADD COLUMN IF NOT EXISTS estado_nacimiento     VARCHAR(100),
  ADD COLUMN IF NOT EXISTS nacionalidad          VARCHAR(50) DEFAULT 'MEXICANA',
  ADD COLUMN IF NOT EXISTS foto_url              VARCHAR(500);

-- ── 2. Estudiante — datos académicos, salud y socioeconómicos ────────────────
ALTER TABLE ades_estudiantes
  ADD COLUMN IF NOT EXISTS nss                   VARCHAR(11),   -- Número de Seguro Social (IMSS/ISSSTE)
  ADD COLUMN IF NOT EXISTS tipo_sangre           VARCHAR(5),    -- A+ A- B+ B- O+ O- AB+ AB-
  ADD COLUMN IF NOT EXISTS alergias              TEXT,
  ADD COLUMN IF NOT EXISTS discapacidad          TEXT,          -- descripción libre o NULL
  ADD COLUMN IF NOT EXISTS escuela_procedencia   VARCHAR(200),
  ADD COLUMN IF NOT EXISTS clave_ct_procedencia  VARCHAR(20),   -- CCT de escuela anterior
  ADD COLUMN IF NOT EXISTS promedio_procedencia  NUMERIC(4,2),  -- promedio de ingreso (0-10)
  ADD COLUMN IF NOT EXISTS beca_tipo             VARCHAR(100),  -- PRONABES BECA_MANUTENCIÓN SEIEM etc.
  ADD COLUMN IF NOT EXISTS beca_monto            NUMERIC(10,2),
  ADD COLUMN IF NOT EXISTS nivel_socioeconomico  VARCHAR(20),   -- BAJO MEDIO_BAJO MEDIO MEDIO_ALTO ALTO
  ADD COLUMN IF NOT EXISTS etnia                 VARCHAR(100),  -- mestizo, nahua, etc.
  ADD COLUMN IF NOT EXISTS lengua_indigena       VARCHAR(100);

-- ── 3. Profesor — datos laborales y de nómina ────────────────────────────────
ALTER TABLE ades_profesores
  ADD COLUMN IF NOT EXISTS rfc                   VARCHAR(13),
  ADD COLUMN IF NOT EXISTS nss                   VARCHAR(11),   -- ISSSTE / IMSS
  ADD COLUMN IF NOT EXISTS cedula_profesional    VARCHAR(20),
  ADD COLUMN IF NOT EXISTS especialidad          VARCHAR(100),
  ADD COLUMN IF NOT EXISTS nivel_estudios        VARCHAR(30),   -- BACHILLERATO LICENCIATURA MAESTRIA DOCTORADO
  ADD COLUMN IF NOT EXISTS fecha_ingreso_inst    DATE,          -- antigüedad en el instituto
  ADD COLUMN IF NOT EXISTS clabe                 VARCHAR(18),   -- CLABE interbancaria (nómina)
  ADD COLUMN IF NOT EXISTS banco                 VARCHAR(100),
  ADD COLUMN IF NOT EXISTS turno                 VARCHAR(20);   -- MATUTINO VESPERTINO NOCTURNO MIXTO

-- ── 4. Tabla de contactos de emergencia / tutores ─────────────────────────────
CREATE TABLE IF NOT EXISTS ades_contactos_emergencia (
  id                UUID        PRIMARY KEY DEFAULT uuidv7(),
  persona_id        UUID        NOT NULL REFERENCES ades_personas(id) ON DELETE CASCADE,
  -- Datos del contacto
  nombre_completo   VARCHAR(200) NOT NULL,
  parentesco        VARCHAR(50),           -- PADRE MADRE TUTOR ABUELO TIO HERMANO OTRO
  telefono          VARCHAR(15),
  telefono_alt      VARCHAR(15),
  email             VARCHAR(255),
  -- Rol del contacto
  es_tutor_legal    BOOLEAN     DEFAULT FALSE,
  es_contacto_prim  BOOLEAN     DEFAULT FALSE,  -- contacto de emergencia principal
  -- Datos adicionales del tutor (para expediente SEP)
  ocupacion         VARCHAR(100),
  nivel_estudios    VARCHAR(30),
  rfc               VARCHAR(13),
  -- Auditoría
  is_active         BOOLEAN     DEFAULT TRUE,
  created_by        UUID,
  updated_by        UUID,
  fecha_creacion        TIMESTAMPTZ DEFAULT NOW(),
  fcactualizacion   TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contactos_persona
  ON ades_contactos_emergencia(persona_id) WHERE is_active = TRUE;

-- ── 5. Índices de búsqueda ────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_estudiantes_nss
  ON ades_estudiantes(nss) WHERE nss IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_profesores_rfc
  ON ades_profesores(rfc) WHERE rfc IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_profesores_nss
  ON ades_profesores(nss) WHERE nss IS NOT NULL;

-- =============================================================================
-- Migración 051: Extensión de direcciones + contactos de persona
-- Reutiliza: ades_codigos_postales, ades_localidades, ades_municipios,
--            ades_estados, ades_paises, ades_tipos_asentamiento (ya populados)
-- =============================================================================
BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Extender ades_direcciones con campos faltantes
--    La tabla ya existe con: calle, numero_exterior, numero_interior, referencia,
--    localidad_id, codigo_postal_id, entidad_tipo, entidad_id
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE ades_direcciones
  ADD COLUMN IF NOT EXISTS tipo_direccion  VARCHAR(30)      NOT NULL DEFAULT 'PRINCIPAL',
  ADD COLUMN IF NOT EXISTS es_principal    BOOLEAN          NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS tipo_via        VARCHAR(30),
  ADD COLUMN IF NOT EXISTS entre_calle_1   VARCHAR(200),
  ADD COLUMN IF NOT EXISTS entre_calle_2   VARCHAR(200),
  ADD COLUMN IF NOT EXISTS latitud         DECIMAL(10, 8),
  ADD COLUMN IF NOT EXISTS longitud        DECIMAL(11, 8),
  ADD COLUMN IF NOT EXISTS precision_gps   VARCHAR(20);

COMMENT ON COLUMN ades_direcciones.tipo_direccion IS 'PRINCIPAL, TRABAJO, TEMPORAL, ANTERIOR, CORRESPONDENCIA';
COMMENT ON COLUMN ades_direcciones.es_principal   IS 'Una sola dirección activa por entidad puede ser principal';
COMMENT ON COLUMN ades_direcciones.tipo_via       IS 'CALLE, AVENIDA, BOULEVARD, CALZADA, PRIVADA, CERRADA, ANDADOR, CARRETERA, CAMINO, OTRO';
COMMENT ON COLUMN ades_direcciones.entre_calle_1  IS 'Primera calle de referencia (entre calles)';
COMMENT ON COLUMN ades_direcciones.entre_calle_2  IS 'Segunda calle de referencia (entre calles)';
COMMENT ON COLUMN ades_direcciones.latitud        IS 'Latitud GPS WGS84 (para mapa)';
COMMENT ON COLUMN ades_direcciones.longitud       IS 'Longitud GPS WGS84 (para mapa)';
COMMENT ON COLUMN ades_direcciones.precision_gps  IS 'EXACTA, APROXIMADA, MANUAL';

-- Índice: solo una dirección principal activa por entidad
CREATE UNIQUE INDEX IF NOT EXISTS idx_dirs_principal_uniq
    ON ades_direcciones(entidad_tipo, entidad_id)
    WHERE es_principal = TRUE AND is_active = TRUE;

-- Índice de búsqueda por persona
CREATE INDEX IF NOT EXISTS idx_dirs_entidad_tipo_id
    ON ades_direcciones(entidad_tipo, entidad_id)
    WHERE is_active = TRUE;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Medios de contacto de persona (múltiples teléfonos / emails por persona)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_persona_contactos (
    id          UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    persona_id  UUID         NOT NULL REFERENCES ades_personas(id),
    -- Clasificación
    medio       VARCHAR(30)  NOT NULL,   -- CELULAR, FIJO, WHATSAPP, EMAIL, TELEGRAM, FAX, OTRO
    tipo        VARCHAR(30)  NOT NULL DEFAULT 'PERSONAL',
                                         -- PERSONAL, TRABAJO, FAMILIAR, INSTITUCIONAL, EMERGENCIA
    valor       VARCHAR(255) NOT NULL,   -- número o dirección de correo
    etiqueta    VARCHAR(50),             -- ej: "Celular mamá", "Trabajo turno"
    es_principal BOOLEAN     NOT NULL DEFAULT FALSE,
    orden       SMALLINT     NOT NULL DEFAULT 1,
    verificado  BOOLEAN      NOT NULL DEFAULT FALSE,
    notas       VARCHAR(200),
    -- Auditoría estándar
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    ref                  UUID         NOT NULL DEFAULT uuidv7(),
    row_version          INTEGER      NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_modificacion   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    usuario_creacion     TEXT         NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion TEXT         NOT NULL DEFAULT CURRENT_USER,
    CONSTRAINT chk_pc_medio CHECK (medio IN (
        'CELULAR','FIJO','WHATSAPP','EMAIL','TELEGRAM','FAX','OTRO')),
    CONSTRAINT chk_pc_tipo CHECK (tipo IN (
        'PERSONAL','TRABAJO','FAMILIAR','INSTITUCIONAL','EMERGENCIA'))
);

COMMENT ON TABLE  ades_persona_contactos             IS 'Medios de contacto múltiples por persona: teléfonos, emails, mensajería';
COMMENT ON COLUMN ades_persona_contactos.medio       IS 'CELULAR, FIJO, WHATSAPP, EMAIL, TELEGRAM, FAX, OTRO';
COMMENT ON COLUMN ades_persona_contactos.tipo        IS 'PERSONAL, TRABAJO, FAMILIAR, INSTITUCIONAL, EMERGENCIA';
COMMENT ON COLUMN ades_persona_contactos.valor       IS 'Número de 10 dígitos o dirección de correo electrónico';
COMMENT ON COLUMN ades_persona_contactos.etiqueta    IS 'Etiqueta libre, ej: Celular mamá, WhatsApp trabajo';
COMMENT ON COLUMN ades_persona_contactos.es_principal IS 'Contacto principal para este medio (uno por tipo de medio)';
COMMENT ON COLUMN ades_persona_contactos.orden       IS 'Orden de visualización dentro del mismo medio';

CREATE INDEX IF NOT EXISTS idx_pc_persona
    ON ades_persona_contactos(persona_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_pc_medio
    ON ades_persona_contactos(persona_id, medio)
    WHERE is_active = TRUE;

-- Audit triggers
SELECT auditoria.asignar_biu('public.ades_persona_contactos');

-- Re-aplicar por si se añadieron columnas a ades_direcciones después del trigger original
SELECT auditoria.asignar_biu('public.ades_direcciones');

COMMIT;

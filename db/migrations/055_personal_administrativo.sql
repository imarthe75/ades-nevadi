-- 055_personal_administrativo.sql
-- Tabla de empleados no-docentes: directivos, coordinadores, secretarías,
-- prefectos, personal de apoyo administrativo y académico.
-- El personal de salud ya tiene ades_personal_salud; se extiende con campos faltantes.

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. TABLA PRINCIPAL: personal administrativo (no-docente, no-salud)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ades_personal_administrativo (
    id                      UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    persona_id              UUID        NOT NULL REFERENCES ades_personas(id),
    plantel_id              UUID        NOT NULL REFERENCES ades_planteles(id),

    -- Datos laborales
    numero_empleado         VARCHAR(30),
    tipo_rol                VARCHAR(60) NOT NULL,
    -- Valores esperados: DIRECTOR, SUBDIRECTOR, COORDINADOR_ACADEMICO,
    -- COORDINADOR_ADMINISTRATIVO, COORDINADOR_RH, COORDINADOR_AREA,
    -- SECRETARIA_ACADEMICA, APOYO_ADMINISTRATIVO, APOYO_ACADEMICO, PREFECTO, ORIENTADOR, TUTOR
    area                    VARCHAR(120),
    tipo_contrato           VARCHAR(40),  -- BASE, CONTRATO, EVENTUAL, CONFIANZA
    nivel_estudios          VARCHAR(60),
    cedula_profesional      VARCHAR(50),
    especialidad            VARCHAR(120),
    turno                   VARCHAR(20),  -- MATUTINO, VESPERTINO, MIXTO

    -- Datos bancarios / nómina
    rfc                     VARCHAR(20),
    nss                     VARCHAR(20),
    clabe                   VARCHAR(18),
    banco                   VARCHAR(60),

    -- Fechas
    fecha_ingreso_inst      DATE,
    fecha_fin_contrato      DATE,

    -- Estado
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,

    -- Auditoría
    ref                     UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    row_version             INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion        VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion    VARCHAR(150) NOT NULL DEFAULT current_user
);

COMMENT ON TABLE public.ades_personal_administrativo
    IS 'Personal no-docente y no-sanitario: directivos, coordinadores, secretarías, prefectos, apoyo.';
COMMENT ON COLUMN public.ades_personal_administrativo.tipo_rol
    IS 'Rol organizacional: DIRECTOR, SUBDIRECTOR, COORDINADOR_*, SECRETARIA_ACADEMICA, PREFECTO, ORIENTADOR, TUTOR, APOYO_*';

-- Índices
CREATE INDEX IF NOT EXISTS idx_padm_plantel  ON ades_personal_administrativo (plantel_id);
CREATE INDEX IF NOT EXISTS idx_padm_persona  ON ades_personal_administrativo (persona_id);
CREATE INDEX IF NOT EXISTS idx_padm_tipo_rol ON ades_personal_administrativo (tipo_rol);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. EXTENDER ades_personal_salud con campos faltantes para perfil completo
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.ades_personal_salud
    ADD COLUMN IF NOT EXISTS numero_empleado    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tipo_contrato      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS nivel_estudios     VARCHAR(60),
    ADD COLUMN IF NOT EXISTS turno              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS rfc                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nss                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS clabe              VARCHAR(18),
    ADD COLUMN IF NOT EXISTS banco              VARCHAR(60),
    ADD COLUMN IF NOT EXISTS fecha_ingreso_inst DATE,
    ADD COLUMN IF NOT EXISTS fecha_fin_contrato DATE;

COMMENT ON TABLE public.ades_personal_salud
    IS 'Personal médico/sanitario del plantel (médico escolar, enfermería).';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. TRIGGERS DE AUDITORÍA
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_biu('public.ades_personal_administrativo');

COMMIT;

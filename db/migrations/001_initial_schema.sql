-- =============================================================================
-- ADES — Administración Escolar Instituto Nevadi
-- DDL PostgreSQL 16 — Migración 001: Esquema inicial completo
-- Convertido desde Oracle + gaps corregidos + tablas nuevas
-- =============================================================================

-- Extensiones requeridas
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";       -- pgvector para memoria semántica
CREATE EXTENSION IF NOT EXISTS "pg_trgm";      -- búsqueda de texto aproximado
CREATE EXTENSION IF NOT EXISTS "unaccent";     -- búsqueda sin acentos

-- Esquema de auditoría (del framework base)
CREATE SCHEMA IF NOT EXISTS auditoria;

-- =============================================================================
-- FUNCIÓN DE AUDITORÍA UNIVERSAL
-- Aplica a todas las tablas del esquema ades
-- =============================================================================
CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.fccreacion      := NOW();
        NEW.fcmodificacion  := NOW();
        NEW.usuario_creacion     := current_user;
        NEW.usuario_modificacion := current_user;
        NEW.ref             := COALESCE(NEW.ref, gen_random_uuid());
        NEW.row_version     := 1;
        NEW.is_active       := COALESCE(NEW.is_active, TRUE);
    ELSIF TG_OP = 'UPDATE' THEN
        NEW.fcmodificacion       := NOW();
        NEW.usuario_modificacion := current_user;
        NEW.row_version          := COALESCE(OLD.row_version, 0) + 1;
        -- No permitir cambiar fccreacion ni usuario_creacion
        NEW.fccreacion      := OLD.fccreacion;
        NEW.usuario_creacion     := OLD.usuario_creacion;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Función auxiliar para asignar el trigger a cualquier tabla
CREATE OR REPLACE FUNCTION auditoria.asignar_trigger(p_tabla TEXT)
RETURNS VOID AS $$
BEGIN
    EXECUTE format(
        'CREATE OR REPLACE TRIGGER trg_aud_biu
         BEFORE INSERT OR UPDATE ON %I
         FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu()',
        p_tabla
    );
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- COLUMNAS DE AUDITORÍA (macro para consistencia)
-- Todas las tablas incluyen estas columnas al final
-- =============================================================================
-- ref              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE
-- is_active        BOOLEAN     NOT NULL DEFAULT TRUE
-- fccreacion       TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- fcmodificacion   TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user
-- usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user
-- row_version      INTEGER     NOT NULL DEFAULT 1

-- =============================================================================
-- 1. ESTATUS (catálogo general — valores por entidad)
-- =============================================================================
CREATE TABLE ades_estatus (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entidad              VARCHAR(100) NOT NULL,  -- tabla/entidad a la que aplica (o 'GLOBAL')
    nombre_estatus       VARCHAR(50)  NOT NULL,
    descripcion          VARCHAR(255),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_estatus_entidad_nombre UNIQUE (entidad, nombre_estatus)
);
COMMENT ON TABLE  ades_estatus IS 'Catálogo de estatus por entidad del sistema.';
COMMENT ON COLUMN ades_estatus.entidad IS 'Nombre de la tabla/entidad a la que aplica, o GLOBAL para estatus transversales.';
SELECT auditoria.asignar_trigger('ades_estatus');

-- =============================================================================
-- 2. CATÁLOGO GEOGRÁFICO (SEPOMEX)
-- =============================================================================
CREATE TABLE ades_paises (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_pais           VARCHAR(10)  NOT NULL UNIQUE,
    nombre_pais          VARCHAR(100) NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_paises IS 'Catálogo de países.';
SELECT auditoria.asignar_trigger('ades_paises');

CREATE TABLE ades_estados (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_estado         VARCHAR(10)  NOT NULL,
    nombre_estado        VARCHAR(100) NOT NULL,
    pais_id              BIGINT       NOT NULL REFERENCES ades_paises(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_estados_clave_pais UNIQUE (clave_estado, pais_id)
);
COMMENT ON TABLE ades_estados IS 'Catálogo de estados/provincias.';
SELECT auditoria.asignar_trigger('ades_estados');

CREATE TABLE ades_municipios (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_municipio      VARCHAR(10)  NOT NULL,
    nombre_municipio     VARCHAR(100) NOT NULL,
    estado_id            BIGINT       NOT NULL REFERENCES ades_estados(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_municipios_clave_estado UNIQUE (clave_municipio, estado_id)
);
SELECT auditoria.asignar_trigger('ades_municipios');

CREATE TABLE ades_tipos_asentamiento (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_tipo           VARCHAR(10)  NOT NULL UNIQUE,
    nombre_tipo          VARCHAR(100) NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_tipos_asentamiento');

CREATE TABLE ades_localidades (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_localidad         VARCHAR(200) NOT NULL,
    municipio_id             BIGINT       NOT NULL REFERENCES ades_municipios(id),
    tipo_asentamiento_id     BIGINT       REFERENCES ades_tipos_asentamiento(id),
    ref                      UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion         VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version              INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_localidades_municipio ON ades_localidades(municipio_id);
CREATE INDEX idx_ades_localidades_nombre ON ades_localidades USING gin(nombre_localidad gin_trgm_ops);
SELECT auditoria.asignar_trigger('ades_localidades');

CREATE TABLE ades_codigos_postales (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_postal        VARCHAR(10)  NOT NULL,
    localidad_id         BIGINT       NOT NULL REFERENCES ades_localidades(id),
    municipio_id         BIGINT       NOT NULL REFERENCES ades_municipios(id),
    estado_id            BIGINT       NOT NULL REFERENCES ades_estados(id),
    tipo_asentamiento_id BIGINT       REFERENCES ades_tipos_asentamiento(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_cp_codigo ON ades_codigos_postales(codigo_postal);
SELECT auditoria.asignar_trigger('ades_codigos_postales');

-- =============================================================================
-- 3. CONTACTO UNIVERSAL (direcciones, teléfonos, correos)
-- Patrón polimórfico: entidad_tipo + entidad_id
-- =============================================================================
CREATE TABLE ades_direcciones (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    calle                VARCHAR(200),
    numero_exterior      VARCHAR(20),
    numero_interior      VARCHAR(20),
    referencia           VARCHAR(255),
    localidad_id         BIGINT       REFERENCES ades_localidades(id),
    codigo_postal_id     BIGINT       REFERENCES ades_codigos_postales(id),
    entidad_tipo         VARCHAR(50)  NOT NULL, -- PLANTEL, PERSONA, ESCUELA
    entidad_id           BIGINT       NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_dir_entidad ON ades_direcciones(entidad_tipo, entidad_id);
SELECT auditoria.asignar_trigger('ades_direcciones');

CREATE TABLE ades_telefonos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_telefono      VARCHAR(20)  NOT NULL,
    tipo_telefono        VARCHAR(20)  DEFAULT 'PRINCIPAL', -- PRINCIPAL, CELULAR, FAX
    entidad_tipo         VARCHAR(50)  NOT NULL,
    entidad_id           BIGINT       NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_tel_entidad ON ades_telefonos(entidad_tipo, entidad_id);
SELECT auditoria.asignar_trigger('ades_telefonos');

CREATE TABLE ades_correos_electronicos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    direccion_email      VARCHAR(255) NOT NULL,
    tipo_correo          VARCHAR(20)  DEFAULT 'PRINCIPAL',
    entidad_tipo         VARCHAR(50)  NOT NULL,
    entidad_id           BIGINT       NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_email_entidad ON ades_correos_electronicos(entidad_tipo, entidad_id);
SELECT auditoria.asignar_trigger('ades_correos_electronicos');

-- =============================================================================
-- 4. ARCHIVOS (almacenamiento MinIO/S3)
-- =============================================================================
CREATE TABLE ades_archivos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_original      VARCHAR(255) NOT NULL,
    nombre_almacenado    VARCHAR(255) NOT NULL, -- clave en MinIO
    bucket               VARCHAR(100) NOT NULL DEFAULT 'ades-archivos',
    mime_type            VARCHAR(100),
    tamanio_bytes        BIGINT,
    entidad_tipo         VARCHAR(50)  NOT NULL, -- TAREA_ENTREGA, EXPEDIENTE_MEDICO, etc.
    entidad_id           BIGINT       NOT NULL,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_archivos_entidad ON ades_archivos(entidad_tipo, entidad_id);
SELECT auditoria.asignar_trigger('ades_archivos');

-- =============================================================================
-- 5. IDENTIDAD INSTITUCIONAL
-- =============================================================================
CREATE TABLE ades_identidad_institucional (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_elemento        VARCHAR(50)  NOT NULL, -- LOGO, SLOGAN, MASCOTA, HIMNO, COLOR_PRIMARIO
    texto_elemento       TEXT,
    url_archivo          VARCHAR(500),          -- ruta en MinIO para imágenes
    color_hex            VARCHAR(7),            -- para tipo COLOR_*
    -- Alcance: puede ser global, por escuela, por plantel, o por nivel en plantel
    escuela_id           BIGINT,
    plantel_id           BIGINT,
    nivel_educativo_id   BIGINT,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE  ades_identidad_institucional IS 'Elementos de identidad visual: logos, eslóganes, colores por escuela/plantel/nivel.';
SELECT auditoria.asignar_trigger('ades_identidad_institucional');

CREATE TABLE ades_historico_identidad (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identidad_id             BIGINT       NOT NULL REFERENCES ades_identidad_institucional(id),
    valor_anterior           TEXT,
    url_archivo_anterior     VARCHAR(500),
    motivo_cambio            VARCHAR(500),
    ref                      UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion         VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version              INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_historico_identidad');

-- =============================================================================
-- 6. ESTRUCTURA INSTITUCIONAL (Escuelas → Planteles)
-- =============================================================================
CREATE TABLE ades_escuelas (
    id                               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_escuela                   VARCHAR(200) NOT NULL,
    sitio_web                        VARCHAR(500),
    identidad_institucional_logo_id  BIGINT REFERENCES ades_identidad_institucional(id),
    identidad_institucional_slogan_id BIGINT REFERENCES ades_identidad_institucional(id),
    estatus_id                       BIGINT REFERENCES ades_estatus(id),
    ref                              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active                        BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion                       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion                 VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion             VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version                      INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_escuelas IS 'Entidad raíz: Instituto Nevadi.';
SELECT auditoria.asignar_trigger('ades_escuelas');

CREATE TABLE ades_planteles (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_plantel       VARCHAR(100) NOT NULL,
    escuela_id           BIGINT       NOT NULL REFERENCES ades_escuelas(id),
    clave_ct             VARCHAR(20),           -- clave de centro de trabajo SEP
    estatus_id           BIGINT REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_planteles_nombre_escuela UNIQUE (nombre_plantel, escuela_id)
);
COMMENT ON TABLE  ades_planteles IS 'Sedes físicas: Metepec, Tenancingo, Ixtapan de la Sal.';
COMMENT ON COLUMN ades_planteles.clave_ct IS 'Clave del Centro de Trabajo asignada por la SEP.';
SELECT auditoria.asignar_trigger('ades_planteles');

-- =============================================================================
-- 7. ESTRUCTURA ACADÉMICA
-- =============================================================================
CREATE TABLE ades_niveles_educativos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_nivel         VARCHAR(50)  NOT NULL UNIQUE, -- PRIMARIA, SECUNDARIA, PREPARATORIA
    autoridad_educativa  VARCHAR(50)  NOT NULL,         -- SEP, UAEMEX
    tipo_ciclo           VARCHAR(20)  NOT NULL DEFAULT 'ANUAL',  -- ANUAL, SEMESTRAL
    num_periodos_eval    INTEGER      NOT NULL DEFAULT 3,
    tiene_extraordinario BOOLEAN      NOT NULL DEFAULT FALSE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON COLUMN ades_niveles_educativos.tipo_ciclo IS 'ANUAL para SEP, SEMESTRAL para UAEMEX.';
COMMENT ON COLUMN ades_niveles_educativos.num_periodos_eval IS 'Primaria=3, Secundaria=6, Preparatoria=2 parciales+final.';
SELECT auditoria.asignar_trigger('ades_niveles_educativos');

-- Relación plantel ↔ nivel (qué niveles opera cada plantel)
CREATE TABLE ades_plantel_niveles (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    nivel_educativo_id   BIGINT       NOT NULL REFERENCES ades_niveles_educativos(id),
    clave_ct_nivel       VARCHAR(20), -- clave CT específica por nivel si difiere
    estatus_id           BIGINT REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_plantel_nivel UNIQUE (plantel_id, nivel_educativo_id)
);
COMMENT ON TABLE ades_plantel_niveles IS 'Niveles activos por plantel. Tenancingo no tiene PREPARATORIA. Ixtapan solo tiene 1° y 2° de secundaria.';
SELECT auditoria.asignar_trigger('ades_plantel_niveles');

CREATE TABLE ades_grados (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_grado         INTEGER      NOT NULL,
    nombre_grado         VARCHAR(50)  NOT NULL, -- "Primer grado", "1er semestre", etc.
    nivel_educativo_id   BIGINT       NOT NULL REFERENCES ades_niveles_educativos(id),
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    estatus_id           BIGINT REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_grados UNIQUE (numero_grado, nivel_educativo_id, plantel_id)
);
COMMENT ON TABLE ades_grados IS 'Grados por nivel y plantel. Ixtapan secundaria solo tiene grados 1 y 2.';
SELECT auditoria.asignar_trigger('ades_grados');

CREATE TABLE ades_ciclos_escolares (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_ciclo         VARCHAR(50)  NOT NULL, -- "2025-2026", "25B", "26A"
    nivel_educativo_id   BIGINT       NOT NULL REFERENCES ades_niveles_educativos(id),
    fecha_inicio         DATE         NOT NULL,
    fecha_fin            DATE         NOT NULL,
    tipo_ciclo           VARCHAR(20)  NOT NULL DEFAULT 'ANUAL', -- ANUAL, SEMESTRAL
    es_vigente           BOOLEAN      NOT NULL DEFAULT TRUE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_ciclos_nombre_nivel UNIQUE (nombre_ciclo, nivel_educativo_id)
);
COMMENT ON COLUMN ades_ciclos_escolares.nombre_ciclo IS '2025-2026 para SEP; 25B/26A para UAEMEX.';
SELECT auditoria.asignar_trigger('ades_ciclos_escolares');

CREATE TABLE ades_periodos_evaluacion (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_periodo       VARCHAR(100) NOT NULL, -- "1er Bimestre", "1er Parcial", "Final"
    numero_periodo       INTEGER      NOT NULL,
    tipo_periodo         VARCHAR(20)  NOT NULL DEFAULT 'ORDINARIO', -- ORDINARIO, FINAL, EXTRAORDINARIO
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    fecha_inicio         DATE         NOT NULL,
    fecha_fin            DATE         NOT NULL,
    fecha_entrega_boletas DATE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_periodos_eval UNIQUE (numero_periodo, tipo_periodo, ciclo_escolar_id)
);
COMMENT ON COLUMN ades_periodos_evaluacion.tipo_periodo IS 'ORDINARIO (bimestres/parciales), FINAL, EXTRAORDINARIO.';
SELECT auditoria.asignar_trigger('ades_periodos_evaluacion');

CREATE TABLE ades_calendario_escolar (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    fecha_evento         DATE         NOT NULL,
    nombre_evento        VARCHAR(200) NOT NULL,
    tipo_evento          VARCHAR(50)  NOT NULL, -- DIA_FESTIVO, VACACIONES, INICIO_CLASES, FIN_CLASES, CONSEJO_TECNICO, SUSPENSION
    aplica_todos_planteles BOOLEAN    NOT NULL DEFAULT TRUE,
    plantel_id           BIGINT       REFERENCES ades_planteles(id), -- null = todos
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_cal_ciclo_fecha ON ades_calendario_escolar(ciclo_escolar_id, fecha_evento);
SELECT auditoria.asignar_trigger('ades_calendario_escolar');

-- =============================================================================
-- 8. MATERIAS Y PLANES DE ESTUDIO
-- =============================================================================
CREATE TABLE ades_materias (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_materia       VARCHAR(100) NOT NULL,
    clave_materia        VARCHAR(20),
    nivel_educativo_id   BIGINT       NOT NULL REFERENCES ades_niveles_educativos(id),
    horas_semana         NUMERIC(4,1) DEFAULT 0,
    es_inglés            BOOLEAN      NOT NULL DEFAULT FALSE, -- flag especial para asignación docente
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_materias_nombre_nivel UNIQUE (nombre_materia, nivel_educativo_id)
);
COMMENT ON COLUMN ades_materias.es_inglés IS 'TRUE = un solo profesor por plantel cubre todos los grupos de primaria.';
SELECT auditoria.asignar_trigger('ades_materias');

-- Vincula materias con grados dentro de un plan de estudios
CREATE TABLE ades_materias_plan (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    grado_id             BIGINT       NOT NULL REFERENCES ades_grados(id),
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    horas_semana         NUMERIC(4,1) NOT NULL DEFAULT 0,
    es_obligatoria       BOOLEAN      NOT NULL DEFAULT TRUE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_mat_plan UNIQUE (materia_id, grado_id, ciclo_escolar_id)
);
COMMENT ON TABLE ades_materias_plan IS 'Plan de estudios: materias por grado y ciclo escolar. Al insertar aquí un proceso Celery genera las tareas del ciclo.';
SELECT auditoria.asignar_trigger('ades_materias_plan');

CREATE TABLE ades_temas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_tema          VARCHAR(255) NOT NULL,
    descripcion          TEXT,
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    grado_id             BIGINT       REFERENCES ades_grados(id),
    ciclo_escolar_id     BIGINT       REFERENCES ades_ciclos_escolares(id),
    orden                INTEGER      NOT NULL DEFAULT 1,
    periodo_sugerido     INTEGER,               -- número de bimestre/parcial sugerido
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_temas_materia ON ades_temas(materia_id);
SELECT auditoria.asignar_trigger('ades_temas');

-- =============================================================================
-- 9. ROLES Y USUARIOS
-- =============================================================================
CREATE TABLE ades_roles (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_rol           VARCHAR(50)  NOT NULL UNIQUE,
    descripcion          VARCHAR(255),
    nivel_acceso         INTEGER      NOT NULL DEFAULT 0, -- 0=más alto, escala
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_roles');

CREATE TABLE ades_personas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre               VARCHAR(100) NOT NULL,
    apellido_paterno     VARCHAR(100) NOT NULL,
    apellido_materno     VARCHAR(100),
    fecha_nacimiento     DATE,
    curp                 VARCHAR(18) UNIQUE,
    rfc                  VARCHAR(13),
    genero               CHAR(1),               -- M, F, O (otro)
    foto_url             VARCHAR(500),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_personas_curp ON ades_personas(curp);
CREATE INDEX idx_ades_personas_nombre ON ades_personas USING gin(
    (nombre || ' ' || apellido_paterno || ' ' || COALESCE(apellido_materno,'')) gin_trgm_ops
);
SELECT auditoria.asignar_trigger('ades_personas');

CREATE TABLE ades_usuarios (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_usuario       VARCHAR(150) NOT NULL UNIQUE,
    email_institucional  VARCHAR(255) UNIQUE,
    clave_hash           VARCHAR(255),          -- NULL para usuarios SSO/OIDC
    oidc_sub             VARCHAR(255) UNIQUE,   -- subject del JWT de Authentik/Google
    persona_id           BIGINT       NOT NULL REFERENCES ades_personas(id),
    rol_id               BIGINT       NOT NULL REFERENCES ades_roles(id),
    plantel_id           BIGINT       REFERENCES ades_planteles(id), -- NULL = acceso global
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    ultimo_acceso        TIMESTAMPTZ,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT chk_ades_usuarios_auth CHECK (
        clave_hash IS NOT NULL OR oidc_sub IS NOT NULL
    )
);
COMMENT ON COLUMN ades_usuarios.clave_hash    IS 'NULL para personal con SSO Google/Authentik.';
COMMENT ON COLUMN ades_usuarios.oidc_sub      IS 'Subject del JWT emitido por Authentik. Identifica unívocamente al usuario en OIDC.';
COMMENT ON COLUMN ades_usuarios.plantel_id    IS 'NULL = admin global con acceso a todos los planteles.';
SELECT auditoria.asignar_trigger('ades_usuarios');

-- =============================================================================
-- 10. PROFESORES Y ESTUDIANTES
-- =============================================================================
CREATE TABLE ades_profesores (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_empleado      VARCHAR(50)  UNIQUE,
    persona_id           BIGINT       NOT NULL REFERENCES ades_personas(id),
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    tipo_contrato        VARCHAR(30)  DEFAULT 'BASE', -- BASE, INTERINO, HONORARIOS
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_prof_plantel ON ades_profesores(plantel_id);
SELECT auditoria.asignar_trigger('ades_profesores');

-- Disponibilidad horaria del profesor para aSc TimeTables
CREATE TABLE ades_disponibilidad_docente (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profesor_id          BIGINT       NOT NULL REFERENCES ades_profesores(id),
    dia_semana           SMALLINT     NOT NULL, -- 1=Lunes … 5=Viernes
    hora_inicio          TIME         NOT NULL,
    hora_fin             TIME         NOT NULL,
    disponible           BOOLEAN      NOT NULL DEFAULT TRUE,
    motivo_no_disponible VARCHAR(200),
    ciclo_escolar_id     BIGINT       REFERENCES ades_ciclos_escolares(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_disponibilidad_docente IS 'Restricciones horarias del docente para exportación a aSc TimeTables.';
SELECT auditoria.asignar_trigger('ades_disponibilidad_docente');

CREATE TABLE ades_estudiantes (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    matricula            VARCHAR(50)  UNIQUE,
    persona_id           BIGINT       NOT NULL REFERENCES ades_personas(id),
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    fecha_ingreso        DATE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_est_plantel ON ades_estudiantes(plantel_id);
SELECT auditoria.asignar_trigger('ades_estudiantes');

CREATE TABLE ades_contactos_familiares (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    persona_id           BIGINT       NOT NULL REFERENCES ades_personas(id),
    parentesco           VARCHAR(30)  NOT NULL DEFAULT 'PADRE', -- PADRE, MADRE, TUTOR, ABUELO, OTRO
    es_tutor_legal       BOOLEAN      NOT NULL DEFAULT FALSE,
    es_contacto_emergencia BOOLEAN    NOT NULL DEFAULT FALSE,
    puede_recoger        BOOLEAN      NOT NULL DEFAULT TRUE,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_contactos_familiares');

-- =============================================================================
-- 11. GRUPOS (corrección del gap: profesor_id no obligatorio)
-- La relación profesor↔materia↔grupo va en ades_asignaciones_docentes
-- =============================================================================
CREATE TABLE ades_grupos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_grupo         VARCHAR(10)  NOT NULL, -- 'A', 'B'
    grado_id             BIGINT       NOT NULL REFERENCES ades_grados(id),
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    profesor_titular_id  BIGINT       REFERENCES ades_profesores(id), -- solo primaria
    capacidad_maxima     INTEGER      DEFAULT 35,
    turno                VARCHAR(20)  DEFAULT 'MATUTINO',
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_grupos UNIQUE (nombre_grupo, grado_id, ciclo_escolar_id)
);
COMMENT ON COLUMN ades_grupos.profesor_titular_id IS 'Solo aplica en primaria. En secundaria y preparatoria es NULL; los docentes se asignan por materia en ades_asignaciones_docentes.';
SELECT auditoria.asignar_trigger('ades_grupos');

-- Tabla que resuelve el gap de grupos con múltiples profesores (secundaria/prep)
CREATE TABLE ades_asignaciones_docentes (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo_id             BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    profesor_id          BIGINT       NOT NULL REFERENCES ades_profesores(id),
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_asignaciones UNIQUE (grupo_id, materia_id, ciclo_escolar_id)
);
COMMENT ON TABLE ades_asignaciones_docentes IS 'Asignación profesor↔materia↔grupo. Aplica a todos los niveles. En primaria el titular cubre todas las materias excepto inglés.';
SELECT auditoria.asignar_trigger('ades_asignaciones_docentes');

CREATE TABLE ades_inscripciones (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    grupo_id             BIGINT       NOT NULL REFERENCES ades_grupos(id),
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    fecha_inscripcion    DATE         NOT NULL DEFAULT CURRENT_DATE,
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_inscripciones UNIQUE (estudiante_id, grupo_id, ciclo_escolar_id)
);
SELECT auditoria.asignar_trigger('ades_inscripciones');

-- =============================================================================
-- 12. HORARIOS (para integración con aSc TimeTables)
-- =============================================================================
CREATE TABLE ades_aulas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_aula          VARCHAR(50)  NOT NULL,
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    tipo_aula            VARCHAR(30)  NOT NULL DEFAULT 'AULA', -- AULA, LABORATORIO, COMPUTO, CANCHA, TALLER
    capacidad            INTEGER      DEFAULT 35,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_aulas UNIQUE (nombre_aula, plantel_id)
);
COMMENT ON TABLE ades_aulas IS 'Espacios físicos por plantel. Requerido como entrada para aSc TimeTables.';
SELECT auditoria.asignar_trigger('ades_aulas');

CREATE TABLE ades_horarios (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo_id             BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    profesor_id          BIGINT       NOT NULL REFERENCES ades_profesores(id),
    aula_id              BIGINT       REFERENCES ades_aulas(id),
    ciclo_escolar_id     BIGINT       NOT NULL REFERENCES ades_ciclos_escolares(id),
    dia_semana           SMALLINT     NOT NULL, -- 1=Lunes … 5=Viernes
    hora_inicio          TIME         NOT NULL,
    hora_fin             TIME         NOT NULL,
    origen               VARCHAR(20)  NOT NULL DEFAULT 'ASC', -- ASC, MANUAL
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_horarios_grupo ON ades_horarios(grupo_id, dia_semana);
CREATE INDEX idx_ades_horarios_profesor ON ades_horarios(profesor_id, dia_semana);
COMMENT ON TABLE  ades_horarios IS 'Horario de clases generado por aSc TimeTables (XML import) o capturado manualmente.';
COMMENT ON COLUMN ades_horarios.origen IS 'ASC = importado desde aSc TimeTables. MANUAL = capturado directamente.';
SELECT auditoria.asignar_trigger('ades_horarios');

-- =============================================================================
-- 13. CLASES, ASISTENCIAS Y PLANEACIÓN
-- =============================================================================
CREATE TABLE ades_clases (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    horario_id           BIGINT       REFERENCES ades_horarios(id),
    grupo_id             BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    profesor_id          BIGINT       NOT NULL REFERENCES ades_profesores(id),
    fecha_clase          DATE         NOT NULL,
    hora_inicio          TIME         NOT NULL,
    hora_fin             TIME         NOT NULL,
    tema_visto           TEXT,
    observaciones        TEXT,
    estatus_clase        VARCHAR(20)  NOT NULL DEFAULT 'PROGRAMADA', -- PROGRAMADA, IMPARTIDA, CANCELADA, SUSPENDIDA
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_clases_grupo_fecha ON ades_clases(grupo_id, fecha_clase);
SELECT auditoria.asignar_trigger('ades_clases');

CREATE TABLE ades_asistencias (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clase_id             BIGINT       NOT NULL REFERENCES ades_clases(id),
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    estatus_asistencia   VARCHAR(20)  NOT NULL DEFAULT 'PRESENTE', -- PRESENTE, AUSENTE, TARDE, JUSTIFICADO
    observacion          VARCHAR(255),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_asistencias UNIQUE (clase_id, estudiante_id)
);
CREATE INDEX idx_ades_asist_estudiante ON ades_asistencias(estudiante_id);
SELECT auditoria.asignar_trigger('ades_asistencias');

CREATE TABLE ades_planeacion_clases (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo_id                 BIGINT   NOT NULL REFERENCES ades_grupos(id),
    tema_id                  BIGINT   NOT NULL REFERENCES ades_temas(id),
    fecha_planeada           DATE     NOT NULL,
    descripcion_actividades  TEXT,
    recursos_didacticos      TEXT,
    ref                      UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion         VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version              INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_planeacion_clases');

CREATE TABLE ades_avance_planificacion (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    planeacion_clase_id      BIGINT   NOT NULL REFERENCES ades_planeacion_clases(id),
    clase_id                 BIGINT   REFERENCES ades_clases(id),
    fecha_ejecucion          DATE     NOT NULL,
    es_completado            BOOLEAN  NOT NULL DEFAULT FALSE,
    comentarios_profesor     TEXT,
    ref                      UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion         VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version              INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_avance_planificacion');

-- =============================================================================
-- 14. TAREAS Y ENTREGAS
-- =============================================================================
CREATE TABLE ades_tareas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo               VARCHAR(255) NOT NULL,
    descripcion          TEXT,
    grupo_id             BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id           BIGINT       NOT NULL REFERENCES ades_materias(id),
    tema_id              BIGINT       REFERENCES ades_temas(id),
    periodo_evaluacion_id BIGINT      REFERENCES ades_periodos_evaluacion(id),
    fecha_asignacion     DATE         NOT NULL DEFAULT CURRENT_DATE,
    fecha_entrega        DATE         NOT NULL,
    puntaje_maximo       NUMERIC(5,2) DEFAULT 10.0,
    permite_entrega_tarde BOOLEAN     NOT NULL DEFAULT FALSE,
    origen               VARCHAR(20)  NOT NULL DEFAULT 'MANUAL', -- MANUAL, AUTO (generada por plan de estudios)
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON COLUMN ades_tareas.origen IS 'AUTO = generada automáticamente al cargar plan de estudios via Celery.';
CREATE INDEX idx_ades_tareas_grupo_materia ON ades_tareas(grupo_id, materia_id);
SELECT auditoria.asignar_trigger('ades_tareas');

CREATE TABLE ades_tareas_entregas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tarea_id             BIGINT       NOT NULL REFERENCES ades_tareas(id),
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    fecha_entrega        TIMESTAMPTZ,
    es_tarde             BOOLEAN      NOT NULL DEFAULT FALSE,
    comentario_alumno    TEXT,
    estatus_entrega      VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, ENTREGADO, CALIFICADO, TARDE
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_entregas UNIQUE (tarea_id, estudiante_id)
);
SELECT auditoria.asignar_trigger('ades_tareas_entregas');

-- =============================================================================
-- 15. CALIFICACIONES Y EVALUACIONES
-- =============================================================================
CREATE TABLE ades_rubricas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_rubrica       VARCHAR(255) NOT NULL,
    descripcion          TEXT,
    materia_id           BIGINT       REFERENCES ades_materias(id),
    nivel_educativo_id   BIGINT       REFERENCES ades_niveles_educativos(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_rubricas');

CREATE TABLE ades_rubrica_criterios (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rubrica_id           BIGINT       NOT NULL REFERENCES ades_rubricas(id),
    nombre_criterio      VARCHAR(255) NOT NULL,
    descripcion          TEXT,
    ponderacion          NUMERIC(5,2) NOT NULL DEFAULT 0,
    orden                INTEGER      NOT NULL DEFAULT 1,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_rubrica_criterios');

CREATE TABLE ades_calificaciones_tareas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tarea_entrega_id     BIGINT       NOT NULL REFERENCES ades_tareas_entregas(id) UNIQUE,
    rubrica_id           BIGINT       REFERENCES ades_rubricas(id),
    calificacion         NUMERIC(5,2) NOT NULL,
    comentarios_docente  TEXT,
    fecha_calificacion   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_calificaciones_tareas');

CREATE TABLE ades_evaluaciones (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_evaluacion     VARCHAR(255) NOT NULL,
    descripcion           TEXT,
    grupo_id              BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id            BIGINT       NOT NULL REFERENCES ades_materias(id),
    periodo_evaluacion_id BIGINT       NOT NULL REFERENCES ades_periodos_evaluacion(id),
    fecha_evaluacion      DATE         NOT NULL,
    tipo_evaluacion       VARCHAR(30)  NOT NULL DEFAULT 'ORDINARIO', -- ORDINARIO, FINAL, EXTRAORDINARIO
    puntaje_maximo        NUMERIC(5,2) DEFAULT 10.0,
    ref                   UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version           INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_evaluaciones');

CREATE TABLE ades_calificaciones_evaluaciones (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluacion_id        BIGINT       NOT NULL REFERENCES ades_evaluaciones(id),
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    calificacion         NUMERIC(5,2) NOT NULL,
    es_acreditado        BOOLEAN      GENERATED ALWAYS AS (calificacion >= 6.0) STORED,
    comentarios          TEXT,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_cal_eval UNIQUE (evaluacion_id, estudiante_id)
);
COMMENT ON COLUMN ades_calificaciones_evaluaciones.es_acreditado IS 'Columna generada: TRUE si calificacion >= 6.0.';
SELECT auditoria.asignar_trigger('ades_calificaciones_evaluaciones');

-- Calificación final por periodo (bimestre/parcial) — consolida tareas + evaluaciones
CREATE TABLE ades_calificaciones_periodo (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id         BIGINT       NOT NULL REFERENCES ades_estudiantes(id),
    grupo_id              BIGINT       NOT NULL REFERENCES ades_grupos(id),
    materia_id            BIGINT       NOT NULL REFERENCES ades_materias(id),
    periodo_evaluacion_id BIGINT       NOT NULL REFERENCES ades_periodos_evaluacion(id),
    calificacion_final    NUMERIC(5,2) NOT NULL,
    es_acreditado         BOOLEAN      GENERATED ALWAYS AS (calificacion_final >= 6.0) STORED,
    observaciones         TEXT,
    ref                   UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version           INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_cal_periodo UNIQUE (estudiante_id, materia_id, periodo_evaluacion_id)
);
SELECT auditoria.asignar_trigger('ades_calificaciones_periodo');

-- =============================================================================
-- 16. EXPEDIENTE MÉDICO
-- =============================================================================
CREATE TABLE ades_personal_salud (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id           BIGINT       NOT NULL REFERENCES ades_personas(id),
    plantel_id           BIGINT       NOT NULL REFERENCES ades_planteles(id),
    cedula_profesional   VARCHAR(50),
    especialidad         VARCHAR(100),
    estatus_id           BIGINT       REFERENCES ades_estatus(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_personal_salud');

CREATE TABLE ades_expedientes_medicos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id        BIGINT       NOT NULL REFERENCES ades_estudiantes(id) UNIQUE,
    tipo_sangre          VARCHAR(5),
    alergias             TEXT,        -- JSON o texto libre; usar JSONB para consultas
    medicamentos_autorizados TEXT,
    condiciones_cronicas TEXT,
    observaciones_generales TEXT,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_expedientes_medicos');

CREATE TABLE ades_incidentes_medicos (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id           BIGINT   NOT NULL REFERENCES ades_estudiantes(id),
    personal_salud_id       BIGINT   REFERENCES ades_personal_salud(id),
    fecha_incidente         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    descripcion             TEXT     NOT NULL,
    tratamiento_aplicado    TEXT,
    requirio_traslado       BOOLEAN  NOT NULL DEFAULT FALSE,
    notificado_tutor        BOOLEAN  NOT NULL DEFAULT FALSE,
    fecha_notificacion_tutor TIMESTAMPTZ,
    ref                     UUID    NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    fccreacion              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion        VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion    VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version             INTEGER NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_incidentes_medicos');

-- =============================================================================
-- 17. REPORTES DE CONDUCTA Y ACADÉMICOS
-- =============================================================================
CREATE TABLE ades_reportes_conducta (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id         BIGINT   NOT NULL REFERENCES ades_estudiantes(id),
    grupo_id              BIGINT   NOT NULL REFERENCES ades_grupos(id),
    reportado_por_id      BIGINT   NOT NULL REFERENCES ades_profesores(id),
    fecha_reporte         DATE     NOT NULL DEFAULT CURRENT_DATE,
    tipo_falta            VARCHAR(50) NOT NULL, -- LEVE, GRAVE, MUY_GRAVE
    descripcion           TEXT     NOT NULL,
    medida_aplicada       TEXT,
    compromiso_mejora     TEXT,
    requiere_seguimiento  BOOLEAN  NOT NULL DEFAULT FALSE,
    estatus_id            BIGINT   REFERENCES ades_estatus(id),
    ref                   UUID    NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fccreacion            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version           INTEGER NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_reportes_conducta');

CREATE TABLE ades_reportes_academicos (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estudiante_id         BIGINT   NOT NULL REFERENCES ades_estudiantes(id),
    ciclo_escolar_id      BIGINT   NOT NULL REFERENCES ades_ciclos_escolares(id),
    periodo_evaluacion_id BIGINT   REFERENCES ades_periodos_evaluacion(id),
    tipo_reporte          VARCHAR(30) NOT NULL DEFAULT 'BOLETA', -- BOLETA, CERTIFICADO, CONSTANCIA
    datos_reporte         JSONB,   -- snapshot de calificaciones al momento de generar
    fecha_generacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generado_por_id       BIGINT   REFERENCES ades_usuarios(id),
    ref                   UUID    NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fccreacion            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version           INTEGER NOT NULL DEFAULT 1
);
COMMENT ON COLUMN ades_reportes_academicos.datos_reporte IS 'Snapshot JSONB de calificaciones al momento de emitir el reporte, para inmutabilidad histórica.';
SELECT auditoria.asignar_trigger('ades_reportes_academicos');

-- =============================================================================
-- 18. COMUNICADOS Y NOTIFICACIONES
-- =============================================================================
CREATE TABLE ades_comunicados (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo               VARCHAR(255) NOT NULL,
    contenido            TEXT         NOT NULL,
    tipo_comunicado      VARCHAR(30)  NOT NULL DEFAULT 'GENERAL', -- GENERAL, ACADEMICO, ADMINISTRATIVO, URGENTE
    -- Alcance
    escuela_id           BIGINT       REFERENCES ades_escuelas(id),
    plantel_id           BIGINT       REFERENCES ades_planteles(id),
    nivel_educativo_id   BIGINT       REFERENCES ades_niveles_educativos(id),
    grupo_id             BIGINT       REFERENCES ades_grupos(id),
    -- NULL en los anteriores = aplica a todos
    requiere_acuse       BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_publicacion    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_vencimiento    TIMESTAMPTZ,
    creado_por_id        BIGINT       REFERENCES ades_usuarios(id),
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_comunicados');

CREATE TABLE ades_acuses_comunicado (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comunicado_id        BIGINT       NOT NULL REFERENCES ades_comunicados(id),
    usuario_id           BIGINT       NOT NULL REFERENCES ades_usuarios(id),
    fecha_acuse          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_origen            INET,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_acuse UNIQUE (comunicado_id, usuario_id)
);
SELECT auditoria.asignar_trigger('ades_acuses_comunicado');

CREATE TABLE ades_notificaciones (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id           BIGINT       NOT NULL REFERENCES ades_usuarios(id),
    titulo               VARCHAR(255) NOT NULL,
    cuerpo               TEXT,
    tipo                 VARCHAR(30)  NOT NULL DEFAULT 'INFO', -- INFO, ALERTA, TAREA, CALIFICACION, SALUD
    entidad_tipo         VARCHAR(50), -- objeto origen (TAREA, COMUNICADO, INCIDENTE_MEDICO, etc.)
    entidad_id           BIGINT,
    leido                BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_leido          TIMESTAMPTZ,
    canal                VARCHAR(20)  NOT NULL DEFAULT 'WEB', -- WEB, EMAIL, PUSH
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_ades_notif_usuario ON ades_notificaciones(usuario_id, leido);
SELECT auditoria.asignar_trigger('ades_notificaciones');

-- =============================================================================
-- 19. INFORMACIÓN GENERAL DE ESCUELA (misión, visión, valores)
-- =============================================================================
CREATE TABLE ades_informacion_escuela (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    escuela_id           BIGINT       NOT NULL REFERENCES ades_escuelas(id) UNIQUE,
    mision               TEXT,
    vision               TEXT,
    valores              TEXT,
    historia             TEXT,
    ref                  UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
SELECT auditoria.asignar_trigger('ades_informacion_escuela');

-- =============================================================================
-- 20. VISTA: RESUMEN DE GRUPOS (útil para Superset y FastAPI)
-- =============================================================================
CREATE OR REPLACE VIEW ades_v_grupos_resumen AS
SELECT
    g.id                    AS grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    gr.numero_grado,
    ne.nombre_nivel,
    p.nombre_plantel,
    ce.nombre_ciclo,
    g.turno,
    g.capacidad_maxima,
    COUNT(i.id)             AS total_inscritos,
    g.is_active
FROM ades_grupos g
JOIN ades_grados           gr  ON gr.id  = g.grado_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_plantel_niveles  pn  ON pn.nivel_educativo_id = ne.id AND pn.plantel_id = gr.plantel_id
JOIN ades_planteles        p   ON p.id  = pn.plantel_id
JOIN ades_ciclos_escolares ce  ON ce.id = g.ciclo_escolar_id
LEFT JOIN ades_inscripciones i ON i.grupo_id = g.id AND i.is_active = TRUE
GROUP BY g.id, g.nombre_grupo, gr.nombre_grado, gr.numero_grado,
         ne.nombre_nivel, p.nombre_plantel, ce.nombre_ciclo,
         g.turno, g.capacidad_maxima, g.is_active;

COMMENT ON VIEW ades_v_grupos_resumen IS 'Vista consolidada de grupos con conteo de inscritos. Útil para dashboard Superset.';

-- =============================================================================
-- ÍNDICES ADICIONALES DE RENDIMIENTO
-- =============================================================================
CREATE INDEX idx_ades_inscr_ciclo   ON ades_inscripciones(ciclo_escolar_id);
CREATE INDEX idx_ades_tareas_fecha  ON ades_tareas(fecha_entrega);
CREATE INDEX idx_ades_cal_periodo_est ON ades_calificaciones_periodo(estudiante_id, materia_id);

-- =============================================================================
-- FIN DEL SCRIPT
-- Tablas creadas: 40
-- Módulos cubiertos: catálogos, geo, identidad, estructura académica, planes de
--   estudio, usuarios/roles, profesores, estudiantes, grupos, horarios (aSc),
--   clases, asistencias, tareas, calificaciones, evaluaciones, expediente médico,
--   conducta, comunicados, notificaciones.
-- Gaps Oracle resueltos: profesor_id en grupos (nullable), ades_asignaciones_docentes,
--   ades_materias_plan, ades_horarios, ades_aulas, ades_disponibilidad_docente,
--   ades_notificaciones, ades_personal_salud, oidc_sub en usuarios.
-- =============================================================================

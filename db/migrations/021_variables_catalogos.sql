-- =============================================================================
-- MIGRACIÓN 021: Variables del Sistema + Catálogos Dinámicos
-- Origen: Starter → ADES (FASE 26-A)
-- Fecha: 2026-06-09
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- EXTENSIONES (removido pgcrypto en FASE 26-B)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- CATÁLOGOS DINÁMICOS
-- Patrón: cabecera (ades_catalogos) + items (ades_catalogo_items)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_catalogos (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo      VARCHAR(80)  NOT NULL UNIQUE,   -- CAT_GENEROS, CAT_PARENTESCO...
    nombre      VARCHAR(150) NOT NULL,
    descripcion VARCHAR(500),
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

COMMENT ON TABLE ades_catalogos IS 'Cabecera de catálogos dinámicos administrables desde el módulo Admin.';

CREATE TABLE IF NOT EXISTS ades_catalogo_items (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    catalogo_id  UUID NOT NULL REFERENCES ades_catalogos(id) ON DELETE RESTRICT,
    valor        VARCHAR(200) NOT NULL,
    descripcion  VARCHAR(500),
    orden        INTEGER NOT NULL DEFAULT 0,
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_catalogo_valor UNIQUE (catalogo_id, valor)
);

COMMENT ON TABLE ades_catalogo_items IS 'Items/valores de los catálogos dinámicos.';

CREATE INDEX IF NOT EXISTS idx_catalogo_items_catalogo ON ades_catalogo_items(catalogo_id);
CREATE INDEX IF NOT EXISTS idx_catalogo_items_activo ON ades_catalogo_items(catalogo_id, is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- VARIABLES DEL SISTEMA
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_variables_sistema (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre          VARCHAR(100) NOT NULL UNIQUE,
    tipo_valor      VARCHAR(20)  NOT NULL DEFAULT 'TEXTO',
    valor           TEXT,
    descripcion     VARCHAR(500),
    encriptado      BOOLEAN NOT NULL DEFAULT FALSE,
    solo_lectura    BOOLEAN NOT NULL DEFAULT FALSE,
    grupo           VARCHAR(50),
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT ck_tipo_valor CHECK (
        tipo_valor IN ('TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD')
    )
);

COMMENT ON TABLE ades_variables_sistema IS 'Variables de configuración del sistema, administrables desde el módulo Admin sin necesidad de SSH.';
COMMENT ON COLUMN ades_variables_sistema.encriptado IS 'En FASE 26-B, indica que es un secreto en Vault. La UI nunca devuelve el path plano.';
COMMENT ON COLUMN ades_variables_sistema.solo_lectura IS 'Si TRUE, la variable no puede modificarse desde la UI (solo desde migraciones).';

CREATE INDEX IF NOT EXISTS idx_variables_grupo ON ades_variables_sistema(grupo);
CREATE INDEX IF NOT EXISTS idx_variables_activo ON ades_variables_sistema(is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- ENCRIPTACIÓN: Manejada por Authentik/Vault externamente (FASE 26-B)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Catálogos base
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_catalogos (codigo, nombre, descripcion) VALUES
    ('CAT_GENEROS',           'Géneros',                    'Géneros de las personas'),
    ('CAT_PARENTESCO',        'Parentesco',                 'Relación familiar del contacto con el alumno'),
    ('CAT_TIPO_CONTRATO',     'Tipo de Contrato',           'Tipo de contrato de los profesores'),
    ('CAT_NIVEL_ESTUDIOS',    'Nivel de Estudios',          'Máximo nivel educativo alcanzado'),
    ('CAT_TIPO_SANGRE',       'Tipo de Sangre',             'Grupos sanguíneos para expediente médico'),
    ('CAT_TURNO',             'Turno',                      'Turno escolar del grupo o profesor'),
    ('CAT_TIPO_FALTA',        'Tipo de Falta Disciplinaria','Clasificación de faltas disciplinarias'),
    ('CAT_TIPO_AUSENCIA',     'Tipo de Ausencia',           'Clasificación de ausencias en asistencias'),
    ('CAT_ESTADO_CIVIL',      'Estado Civil',               'Estado civil de personas'),
    ('CAT_TIPO_EVALUACION',   'Tipo de Evaluación',         'Modalidad del examen o evaluación')
ON CONFLICT (codigo) DO NOTHING;

-- Items de cada catálogo
WITH cat AS (SELECT id, codigo FROM ades_catalogos)
INSERT INTO ades_catalogo_items (catalogo_id, valor, orden) VALUES
    -- CAT_GENEROS
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Hombre',                  10),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Mujer',                   20),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'No binario',              30),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Prefiero no indicar',     40),
    -- CAT_PARENTESCO
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Padre',                10),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Madre',                20),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Tutor legal',          30),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Abuelo/a',             40),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Tío/a',                50),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Hermano/a mayor',      60),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Otro',                 99),
    -- CAT_TIPO_CONTRATO
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'TIEMPO_COMPLETO',   10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'MEDIO_TIEMPO',      20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'POR_HORAS',         30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'EVENTUAL',          40),
    -- CAT_NIVEL_ESTUDIOS
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Primaria',         10),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Secundaria',       20),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Bachillerato',     30),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Técnico',          35),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Licenciatura',     40),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Maestría',         50),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Doctorado',        60),
    -- CAT_TIPO_SANGRE
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'O+',  10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'O-',  20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'A+',  30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'A-',  40),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'B+',  50),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'B-',  60),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'AB+', 70),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'AB-', 80),
    -- CAT_TURNO
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'MATUTINO',   10),
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'VESPERTINO', 20),
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'NOCTURNO',   30),
    -- CAT_TIPO_FALTA
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Leve',     10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Moderada', 20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Grave',    30),
    -- CAT_TIPO_AUSENCIA
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Injustificada', 10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Justificada',   20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Retardo',       30),
    -- CAT_ESTADO_CIVIL
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Soltero/a',    10),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Casado/a',     20),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Divorciado/a', 30),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Viudo/a',      40),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Unión libre',   50),
    -- CAT_TIPO_EVALUACION
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'ORDINARIO',          10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'EXTRAORDINARIO',     20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'TITULO_SUFICIENCIA', 30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'DIAGNOSTICO',        40)
ON CONFLICT (catalogo_id, valor) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Variables del sistema ADES
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_variables_sistema (nombre, tipo_valor, valor, descripcion, grupo, solo_lectura) VALUES
    -- GRUPO: SISTEMA
    ('NOMBRE_INSTITUCION',          'TEXTO',    'Instituto Nevadi',             'Nombre oficial de la institución educativa',                          'SISTEMA',         FALSE),
    ('NOMBRE_SISTEMA',              'TEXTO',    'ADES',                         'Nombre corto del sistema',                                           'SISTEMA',         TRUE),
    ('ZONA_HORARIA',                'TEXTO',    'America/Mexico_City',          'Zona horaria del servidor y la institución',                          'SISTEMA',         FALSE),
    ('URL_PORTAL',                  'TEXTO',    'https://ades.setag.mx',        'URL pública de la aplicación',                                        'SISTEMA',         FALSE),
    ('URL_SERVICIOS',               'TEXTO',    'https://ades.setag.mx/api/v1', 'Base URL del API REST',                                               'SISTEMA',         FALSE),
    ('ACTIVAR_DEBUG',               'BOOLEANO', 'false',                        'Activa modo debug en el API (solo desarrollo)',                       'SISTEMA',         FALSE),
    ('PATH_TEMPORALES',             'TEXTO',    '/tmp',                         'Carpeta temporal para generación de archivos',                        'SISTEMA',         FALSE),
    ('HORA_RESPALDO_BD',            'HORA',     '02:00:00',                     'Hora programada del respaldo automático de base de datos',            'SISTEMA',         FALSE),
    ('JSON_CONFIG_UI',              'JSON',     '{"tema":"claro","idioma":"es"}','Configuración de la interfaz de usuario',                            'SISTEMA',         FALSE),
    ('JSON_MARCA',                  'JSON',     '{"logo_url":"","color_primario":"#2563eb","slogan":"Formando el futuro"}','Identidad visual de la institución','SISTEMA',FALSE),
    -- GRUPO: GEO
    ('ID_PAIS_DEFAULT',             'NUMERO',   '52',                           'Clave internacional México ISO 3166-1',                               'GEO',             FALSE),
    ('ID_ESTADO_DEFAULT',           'NUMERO',   '15',                           'Clave de Estado de México (default para formularios)',                'GEO',             FALSE),
    ('ID_MUNICIPIO_DEFAULT',        'NUMERO',   '125',                          'Clave de municipio predeterminado',                                   'GEO',             FALSE),
    -- GRUPO: CORREO
    ('JSON_CORREO',                 'JSON',     '{"host":"smtp.nevadi.mx","port":587,"from":"no-reply@nevadi.mx","tls":true}','Configuración del servidor de correo','CORREO',FALSE),
    -- GRUPO: NOTIFICACIONES
    ('HABILITAR_NOTIFICACIONES',    'BOOLEANO', 'true',                         'Activa el envío de notificaciones push vía ntfy',                     'NOTIFICACIONES',  FALSE),
    ('URL_NTFY',                    'TEXTO',    'https://notify.ades.setag.mx', 'URL del servidor ntfy para notificaciones push',                      'NOTIFICACIONES',  FALSE),
    -- GRUPO: ACADEMICO
    ('ESCALA_CALIF_MIN',            'NUMERO',   '5',                            'Calificación mínima válida (SEP: 5, UAEMEX: 0)',                      'ACADEMICO',       FALSE),
    ('ESCALA_CALIF_MAX',            'NUMERO',   '10',                           'Calificación máxima válida',                                          'ACADEMICO',       FALSE),
    ('CALIFICACION_APROBATORIA',    'NUMERO',   '6',                            'Calificación mínima de aprobación',                                   'ACADEMICO',       FALSE),
    ('MAX_ALUMNOS_POR_GRUPO',       'NUMERO',   '30',                           'Capacidad máxima default por grupo (sobrecupo requiere autorización)', 'ACADEMICO',       FALSE),
    ('PORCENTAJE_AUSENTISMO_ALERTA','NUMERO',   '20',                           'Porcentaje de ausentismo que activa alertas automáticas',              'ACADEMICO',       FALSE),
    ('DIAS_AUSENCIAS_ALERTA',       'NUMERO',   '3',                            'Número de ausencias consecutivas que activa alerta al padre',         'ACADEMICO',       FALSE),
    ('FECHA_CIERRE_EJERCICIO',      'FECHA',    '2026-12-31T06:00:00.000Z',     'Fecha de cierre del ciclo escolar vigente',                           'ACADEMICO',       FALSE),
    -- GRUPO: REPORTES
    ('DIAS_VIGENCIA_LINK_BOLETA',   'NUMERO',   '30',                           'Días de validez de los links de descarga de boletas (MinIO presigned)','REPORTES',       FALSE),
    ('URL_CARBONE',                 'TEXTO',    'http://carbone:3001',          'URL interna del microservicio Carbone (generación de PDFs)',          'REPORTES',        TRUE),
    ('URL_STIRLING',                'TEXTO',    'http://stirling-pdf:8081',     'URL interna de Stirling-PDF (marca de agua y compresión)',            'REPORTES',        TRUE),
    -- GRUPO: BI
    ('URL_SUPERSET',                'TEXTO',    'https://bi.ades.setag.mx',     'URL del servidor Apache Superset',                                    'BI',              FALSE),
    -- GRUPO: ALMACENAMIENTO
    ('URL_MINIO',                   'TEXTO',    'https://minio.ades.setag.mx',  'URL pública de MinIO para descarga de archivos',                      'ALMACENAMIENTO',  FALSE),
    -- GRUPO: IA
    ('TOKEN_ANTHROPIC_API',         'PASSWORD', 'vault:secret/data/ades/anthropic',  'Vault path para API Key de Anthropic Claude',                       'IA',              FALSE),
    -- GRUPO: INTEGRACIONES
    ('TOKEN_MINIO_ACCESS_KEY',      'PASSWORD', 'vault:secret/data/ades/minio',      'Vault path para Access Key de MinIO',                               'INTEGRACIONES',   FALSE)
ON CONFLICT (nombre) DO UPDATE SET
    descripcion = EXCLUDED.descripcion,
    grupo       = EXCLUDED.grupo;

-- Marcar las PASSWORD como encriptadas
UPDATE ades_variables_sistema
SET encriptado = TRUE
WHERE tipo_valor = 'PASSWORD';

COMMIT;

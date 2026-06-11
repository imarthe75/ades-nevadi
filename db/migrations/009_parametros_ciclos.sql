-- =============================================================================
-- MIGRACIÓN 009 — Parámetros del sistema + Promoción automática de ciclo
-- =============================================================================
-- Instituto Nevadi / ADES
-- Fecha: 2026-06-04
--
-- Cambios:
--   1. ades_parametros_sistema  — tabla key-value para configuración global
--   2. Estatus adicionales       — PROMOVIDO, EGRESADO, REPROBADO para ESTUDIANTE
--                                  REINGRESO para ESTUDIANTE
--   3. cerrar_ciclo_y_promover() — función PG para cierre de ciclo y
--                                  reinscripción automática del siguiente
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. TABLA DE PARÁMETROS DEL SISTEMA
-- -----------------------------------------------------------------------------
-- Almacena variables configurables por ADMIN_GLOBAL desde la UI.
-- Ejemplos: teléfono institucional, correo contacto, logo_url, nombre_sistema,
--           color_primario, color_secundario, feature flags, etc.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ades_parametros_sistema (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    clave                VARCHAR(100) NOT NULL,               -- 'TEL_PRINCIPAL', 'LOGO_URL', etc.
    valor                TEXT,                                -- valor actual
    valor_default        TEXT,                                -- valor de fábrica / reset
    tipo_valor           VARCHAR(20)  NOT NULL DEFAULT 'TEXTO',
                                                             -- TEXTO | NUMERO | BOOLEAN | URL | COLOR | JSON
    descripcion          VARCHAR(500),                        -- descripción amigable para la UI admin
    grupo                VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
                                                             -- GENERAL | APARIENCIA | CONTACTO | SEP | FUNCIONALIDAD
    es_publico           BOOLEAN     NOT NULL DEFAULT FALSE, -- si TRUE, visible sin autenticación (ej. nombre escuela)
    es_editable          BOOLEAN     NOT NULL DEFAULT TRUE,  -- si FALSE, solo lectura (constantes del sistema)
    ref                  UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_param_clave UNIQUE (clave)
);
COMMENT ON TABLE  ades_parametros_sistema IS 'Parámetros configurables del sistema. Modificables por ADMIN_GLOBAL desde la UI.';
COMMENT ON COLUMN ades_parametros_sistema.clave IS 'Identificador único del parámetro. SCREAMING_SNAKE_CASE.';
COMMENT ON COLUMN ades_parametros_sistema.grupo IS 'Agrupación para la UI de administración.';
COMMENT ON COLUMN ades_parametros_sistema.es_publico IS 'Si TRUE, la API expone el valor sin requerir autenticación.';

SELECT auditoria.asignar_trigger('ades_parametros_sistema');

-- Valores iniciales
INSERT INTO ades_parametros_sistema
    (clave, valor, valor_default, tipo_valor, descripcion, grupo, es_publico) VALUES
-- GENERAL
('NOMBRE_SISTEMA',       'ADES',                    'ADES',                    'TEXTO',    'Nombre corto del sistema',                                    'GENERAL',       TRUE),
('NOMBRE_INSTITUCION',   'Instituto Nevadi',         'Instituto Nevadi',         'TEXTO',    'Nombre completo de la institución',                           'GENERAL',       TRUE),
('SLOGAN',               NULL,                       NULL,                       'TEXTO',    'Slogan institucional (opcional)',                              'GENERAL',       TRUE),
-- CONTACTO
('TEL_PRINCIPAL',        NULL,                       NULL,                       'TEXTO',    'Teléfono principal de la institución',                         'CONTACTO',      TRUE),
('EMAIL_CONTACTO',       NULL,                       NULL,                       'TEXTO',    'Correo de contacto institucional',                             'CONTACTO',      TRUE),
('SITIO_WEB',            'https://ades.setag.mx',    'https://ades.setag.mx',    'URL',      'URL del sistema escolar',                                     'CONTACTO',      TRUE),
-- APARIENCIA
('LOGO_URL',             NULL,                       NULL,                       'URL',      'URL del logo principal (MinIO). Si NULL, usa el SVG embebido', 'APARIENCIA',    TRUE),
('COLOR_PRIMARIO',       '#C41724',                  '#C41724',                  'COLOR',    'Color primario institucional (hex)',                           'APARIENCIA',    TRUE),
('COLOR_SECUNDARIO',     '#1A1A2E',                  '#1A1A2E',                  'COLOR',    'Color secundario / oscuro institucional (hex)',                'APARIENCIA',    TRUE),
('FAVICON_URL',          NULL,                       NULL,                       'URL',      'URL favicon personalizado. Si NULL, usa /favicon.svg',         'APARIENCIA',    TRUE),
-- SEP / ACADÉMICO
('CLAVE_CCT_PRIMARIA',   NULL,                       NULL,                       'TEXTO',    'Clave de Centro de Trabajo nivel Primaria (SEP)',              'SEP',           FALSE),
('CLAVE_CCT_SECUNDARIA', NULL,                       NULL,                       'TEXTO',    'Clave de Centro de Trabajo nivel Secundaria (SEP)',            'SEP',           FALSE),
('CLAVE_CCT_PREPARATORIA',NULL,                      NULL,                       'TEXTO',    'Clave de Centro de Trabajo nivel Preparatoria (UAEMEX)',       'SEP',           FALSE),
('ESCALA_CALIFICACION',  'SEP',                      'SEP',                      'TEXTO',    'Escala de calificación por defecto: SEP (0-10) o UAEMEX (0-100)', 'SEP',        FALSE),
-- FUNCIONALIDAD
('PORTAL_PADRES_ACTIVO', 'true',                     'true',                     'BOOLEAN',  'Habilita el portal de padres de familia',                      'FUNCIONALIDAD', FALSE),
('ENCUESTAS_ACTIVO',     'true',                     'true',                     'BOOLEAN',  'Habilita el módulo de encuestas docentes',                     'FUNCIONALIDAD', FALSE),
('IA_ACTIVO',            'true',                     'true',                     'BOOLEAN',  'Habilita asistente IA (requiere API key configurada)',          'FUNCIONALIDAD', FALSE),
('OPENAI_API_KEY',       NULL,                       NULL,                       'TEXTO',    'API Key de OpenAI para el asistente IA (cifrada en reposo)',   'FUNCIONALIDAD', FALSE)
ON CONFLICT (clave) DO NOTHING;


-- -----------------------------------------------------------------------------
-- 2. ESTATUS ADICIONALES PARA ESTUDIANTE
-- -----------------------------------------------------------------------------
INSERT INTO ades_estatus (entidad, nombre_estatus, descripcion) VALUES
  ('ESTUDIANTE', 'ACTIVO',         'Alumno inscrito activo en el ciclo vigente'),
  ('ESTUDIANTE', 'PROMOVIDO',      'Alumno promovido al siguiente grado/nivel'),
  ('ESTUDIANTE', 'REPROBADO',      'Alumno que no acreditó el grado — puede reinscribirse al mismo'),
  ('ESTUDIANTE', 'EGRESADO',       'Alumno que completó el nivel educativo'),
  ('ESTUDIANTE', 'REINGRESO',      'Alumno que regresa después de una baja temporal')
ON CONFLICT (entidad, nombre_estatus) DO NOTHING;


-- -----------------------------------------------------------------------------
-- 3. FUNCIÓN: cerrar_ciclo_y_promover
-- -----------------------------------------------------------------------------
-- Propósito:
--   Cierra el ciclo escolar vigente y crea inscripciones para el siguiente ciclo
--   para todos los alumnos activos/promovidos, asignándolos al siguiente grado.
--
-- Parámetros:
--   p_ciclo_origen_id   UUID del ciclo que se cierra (es_vigente = TRUE)
--   p_ciclo_destino_id  UUID del nuevo ciclo ya creado (es_vigente = FALSE aún)
--   p_usuario           Usuario que ejecuta el proceso (para auditoría)
--
-- Lógica:
--   1. Valida que el ciclo origen esté vigente y el destino no.
--   2. Para cada inscripción activa del ciclo origen:
--      a. Si estatus BAJA → no se reinscribe.
--      b. Si estatus EGRESADO (completó nivel) → no se reinscribe en ese nivel.
--      c. Si estatus REPROBADO → se reinscribe en el MISMO grado (grupo homónimo).
--      d. Si estatus ACTIVO o PROMOVIDO → se reinscribe en el SIGUIENTE grado.
--   3. La asignación de grupos se hace por grupo homónimo del siguiente grado
--      (mismo nombre, mismo plantel, mismo nivel). Si no existe el grupo destino,
--      se registra en la tabla ades_promociones_pendientes para gestión manual.
--   4. Cierra el ciclo origen (es_vigente = FALSE).
--   5. Activa el ciclo destino (es_vigente = TRUE).
--   6. Retorna un JSON con estadísticas del proceso.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ades_promociones_pendientes (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    estudiante_id        UUID         NOT NULL REFERENCES ades_estudiantes(id),
    ciclo_origen_id      UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    ciclo_destino_id     UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    grupo_origen_id      UUID         NOT NULL REFERENCES ades_grupos(id),
    motivo               VARCHAR(200) NOT NULL DEFAULT 'GRUPO_DESTINO_NO_ENCONTRADO',
    resuelta             BOOLEAN     NOT NULL DEFAULT FALSE,
    grupo_asignado_id    UUID         REFERENCES ades_grupos(id),
    ref                  UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_promociones_pendientes IS 'Alumnos cuya reinscripción automática no pudo completarse por falta de grupo destino. Requieren asignación manual.';

SELECT auditoria.asignar_trigger('ades_promociones_pendientes');


CREATE OR REPLACE FUNCTION cerrar_ciclo_y_promover(
    p_ciclo_origen_id   UUID,
    p_ciclo_destino_id  UUID,
    p_usuario           VARCHAR(150) DEFAULT current_user
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
    v_promovidos        INTEGER := 0;
    v_reprobados        INTEGER := 0;
    v_bajas             INTEGER := 0;
    v_egresados         INTEGER := 0;
    v_pendientes        INTEGER := 0;
    v_inscripcion       RECORD;
    v_grado_siguiente   UUID;
    v_grupo_destino     UUID;
    v_grado_actual      INTEGER;
    v_grado_max         INTEGER;
    v_nivel_id          UUID;
    v_estatus_activo    UUID;
    v_estatus_promovido UUID;
    v_estatus_baja      UUID;
    v_estatus_egresado  UUID;
    v_estatus_reprobado UUID;
BEGIN
    -- Validar que el ciclo origen exista y esté vigente
    IF NOT EXISTS (
        SELECT 1 FROM ades_ciclos_escolares
        WHERE id = p_ciclo_origen_id AND es_vigente = TRUE AND is_active = TRUE
    ) THEN
        RAISE EXCEPTION 'El ciclo origen % no está vigente o no existe.', p_ciclo_origen_id;
    END IF;

    -- Validar que el ciclo destino exista
    IF NOT EXISTS (
        SELECT 1 FROM ades_ciclos_escolares
        WHERE id = p_ciclo_destino_id AND is_active = TRUE
    ) THEN
        RAISE EXCEPTION 'El ciclo destino % no existe.', p_ciclo_destino_id;
    END IF;

    -- Cachear IDs de estatus relevantes
    SELECT id INTO v_estatus_activo    FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'ACTIVO'    LIMIT 1;
    SELECT id INTO v_estatus_promovido FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'PROMOVIDO' LIMIT 1;
    SELECT id INTO v_estatus_baja      FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'BAJA'      LIMIT 1;
    SELECT id INTO v_estatus_egresado  FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'EGRESADO'  LIMIT 1;
    SELECT id INTO v_estatus_reprobado FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'REPROBADO' LIMIT 1;

    -- Iterar sobre todas las inscripciones del ciclo origen
    FOR v_inscripcion IN
        SELECT
            i.id             AS inscripcion_id,
            i.estudiante_id,
            i.grupo_id,
            i.estatus_id,
            g.grado_id,
            g.nombre_grupo,
            g.plantel_id,
            gr.numero_grado,
            gr.nivel_educativo_id,
            e_est.nombre_estatus
        FROM ades_inscripciones      i
        JOIN ades_grupos             g   ON g.id  = i.grupo_id
        JOIN ades_grados             gr  ON gr.id = g.grado_id
        JOIN ades_estatus            e_est ON e_est.id = i.estatus_id
        WHERE i.ciclo_escolar_id = p_ciclo_origen_id
          AND i.is_active = TRUE
    LOOP
        -- Alumnos de baja: no reinscribir
        IF v_inscripcion.estatus_id = v_estatus_baja THEN
            v_bajas := v_bajas + 1;
            CONTINUE;
        END IF;

        -- Grado máximo del nivel para detectar egresados
        SELECT MAX(numero_grado) INTO v_grado_max
        FROM ades_grados
        WHERE nivel_educativo_id = v_inscripcion.nivel_educativo_id
          AND plantel_id = v_inscripcion.plantel_id
          AND is_active = TRUE;

        -- Si es el último grado del nivel → egresado, no reinscribir en ese nivel
        IF v_inscripcion.numero_grado >= v_grado_max AND v_inscripcion.estatus_id != v_estatus_reprobado THEN
            -- Marcar como egresado
            UPDATE ades_inscripciones
            SET estatus_id = v_estatus_egresado,
                usuario_modificacion = p_usuario
            WHERE id = v_inscripcion.inscripcion_id;
            v_egresados := v_egresados + 1;
            CONTINUE;
        END IF;

        -- Determinar grado destino
        IF v_inscripcion.estatus_id = v_estatus_reprobado THEN
            -- Reprobado: mismo grado
            v_grado_siguiente := v_inscripcion.grado_id;
            v_reprobados := v_reprobados + 1;
        ELSE
            -- Promovido o activo: siguiente grado (número + 1, mismo nivel y plantel)
            SELECT id INTO v_grado_siguiente
            FROM ades_grados
            WHERE nivel_educativo_id = v_inscripcion.nivel_educativo_id
              AND plantel_id         = v_inscripcion.plantel_id
              AND numero_grado       = v_inscripcion.numero_grado + 1
              AND is_active = TRUE
            LIMIT 1;

            IF v_grado_siguiente IS NULL THEN
                -- No hay grado siguiente → egresado
                UPDATE ades_inscripciones
                SET estatus_id = v_estatus_egresado,
                    usuario_modificacion = p_usuario
                WHERE id = v_inscripcion.inscripcion_id;
                v_egresados := v_egresados + 1;
                CONTINUE;
            END IF;
            v_promovidos := v_promovidos + 1;
        END IF;

        -- Buscar grupo homónimo en el ciclo destino (mismo nombre, grado siguiente)
        SELECT id INTO v_grupo_destino
        FROM ades_grupos
        WHERE grado_id        = v_grado_siguiente
          AND nombre_grupo    = v_inscripcion.nombre_grupo
          AND ciclo_escolar_id = p_ciclo_destino_id
          AND is_active = TRUE
        LIMIT 1;

        IF v_grupo_destino IS NULL THEN
            -- No existe grupo destino → dejar para asignación manual
            INSERT INTO ades_promociones_pendientes
                (estudiante_id, ciclo_origen_id, ciclo_destino_id, grupo_origen_id, usuario_creacion)
            VALUES
                (v_inscripcion.estudiante_id, p_ciclo_origen_id, p_ciclo_destino_id,
                 v_inscripcion.grupo_id, p_usuario);
            v_pendientes := v_pendientes + 1;
            CONTINUE;
        END IF;

        -- Crear inscripción en ciclo destino
        INSERT INTO ades_inscripciones
            (estudiante_id, grupo_id, ciclo_escolar_id, estatus_id, usuario_creacion)
        VALUES
            (v_inscripcion.estudiante_id, v_grupo_destino, p_ciclo_destino_id,
             v_estatus_activo, p_usuario)
        ON CONFLICT (estudiante_id, grupo_id, ciclo_escolar_id) DO NOTHING;

    END LOOP;

    -- Cerrar ciclo origen
    UPDATE ades_ciclos_escolares
    SET es_vigente = FALSE,
        usuario_modificacion = p_usuario
    WHERE id = p_ciclo_origen_id;

    -- Activar ciclo destino
    UPDATE ades_ciclos_escolares
    SET es_vigente = TRUE,
        usuario_modificacion = p_usuario
    WHERE id = p_ciclo_destino_id;

    RETURN jsonb_build_object(
        'ok',          TRUE,
        'promovidos',  v_promovidos,
        'reprobados',  v_reprobados,
        'egresados',   v_egresados,
        'bajas',       v_bajas,
        'pendientes',  v_pendientes,
        'total',       v_promovidos + v_reprobados + v_egresados + v_bajas + v_pendientes
    );
END;
$$;

COMMENT ON FUNCTION cerrar_ciclo_y_promover IS
'Cierra el ciclo origen (es_vigente=FALSE), activa el destino (es_vigente=TRUE) y
crea inscripciones para todos los alumnos activos. Alumnos sin grupo destino quedan
en ades_promociones_pendientes para asignación manual. Retorna JSON con estadísticas.';

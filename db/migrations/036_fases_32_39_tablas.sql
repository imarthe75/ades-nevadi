-- ============================================================
-- Migración 036: Tablas FASE 32-39
-- Movilidad, Evaluación Avanzada, Portal Familias, Salud Avanzada,
-- Foros, Compliance, IA Avanzada, Procesos Escolares
-- ============================================================
BEGIN;

-- ──────────────────────────────────────────────────────────────
-- FASE 32: Movilidad Estudiantil
-- (ades_cambios_grupo y ades_bajas YA EXISTEN — solo verificar)
-- ──────────────────────────────────────────────────────────────

-- ──────────────────────────────────────────────────────────────
-- FASE 33: Evaluación Avanzada
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_escalas_evaluacion (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre          VARCHAR(80)  NOT NULL,
    nivel_educativo VARCHAR(30)  NOT NULL CHECK (nivel_educativo IN ('PRIMARIA','SECUNDARIA','PREPARATORIA')),
    descripcion     TEXT,
    valores_json    JSONB        NOT NULL DEFAULT '[]',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_escalas_evaluacion IS 'Escalas de evaluación cualitativa SEP/UAEMEX';

CREATE TABLE IF NOT EXISTS ades_observaciones_pedagogicas (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id   UUID        NOT NULL REFERENCES ades_estudiantes(id),
    observacion TEXT        NOT NULL,
    periodo     VARCHAR(20),
    tipo        VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    autor_id    UUID        REFERENCES ades_personas(id),
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_observaciones_pedagogicas IS 'Observaciones pedagógicas por alumno (EV-017)';
CREATE INDEX IF NOT EXISTS idx_obs_ped_alumno ON ades_observaciones_pedagogicas(alumno_id);

CREATE TABLE IF NOT EXISTS ades_nee (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id            UUID        NOT NULL REFERENCES ades_estudiantes(id),
    tipo_nee             VARCHAR(40) NOT NULL,
    descripcion          TEXT        NOT NULL,
    apoyos_requeridos    TEXT,
    fecha_deteccion      DATE,
    profesional_detecta  VARCHAR(120),
    activa               BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_nee IS 'Necesidades Educativas Especiales (EV-024)';
CREATE INDEX IF NOT EXISTS idx_nee_alumno ON ades_nee(alumno_id) WHERE activa;

CREATE TABLE IF NOT EXISTS ades_asignaciones_aula (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    clase_id    UUID        REFERENCES ades_clases(id),
    aula_id     UUID        NOT NULL REFERENCES ades_aulas(id),
    fecha       DATE        NOT NULL,
    hora_inicio TIME        NOT NULL,
    hora_fin    TIME        NOT NULL,
    observaciones TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_asignaciones_aula IS 'Asignación puntual de aula a clase/evento (EV-025)';
CREATE INDEX IF NOT EXISTS idx_asig_aula_fecha ON ades_asignaciones_aula(aula_id, fecha) WHERE is_active;

-- ──────────────────────────────────────────────────────────────
-- FASE 34: Portal Familias
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_tutores_alumnos (
    id                       UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id                UUID        NOT NULL REFERENCES ades_estudiantes(id),
    persona_id               UUID        NOT NULL REFERENCES ades_personas(id),
    relacion                 VARCHAR(30) NOT NULL DEFAULT 'TUTOR',
    prioridad                SMALLINT    NOT NULL DEFAULT 1 CHECK (prioridad BETWEEN 1 AND 5),
    puede_recoger            BOOLEAN     NOT NULL DEFAULT TRUE,
    es_responsable_economico BOOLEAN     NOT NULL DEFAULT FALSE,
    es_contacto_emergencia   BOOLEAN     NOT NULL DEFAULT FALSE,
    nivel_acceso_portal      VARCHAR(20) NOT NULL DEFAULT 'LECTURA',
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system',
    UNIQUE (alumno_id, persona_id)
);
COMMENT ON TABLE ades_tutores_alumnos IS 'Vínculos tutor-alumno con permisos de portal (PE-029)';

CREATE TABLE IF NOT EXISTS ades_restricciones_tutor (
    tutor_alumno_id          UUID PRIMARY KEY REFERENCES ades_tutores_alumnos(id),
    puede_ver_calificaciones BOOLEAN NOT NULL DEFAULT TRUE,
    puede_ver_asistencias    BOOLEAN NOT NULL DEFAULT TRUE,
    puede_ver_conducta       BOOLEAN NOT NULL DEFAULT TRUE,
    puede_ver_tareas         BOOLEAN NOT NULL DEFAULT TRUE,
    puede_descargar_documentos BOOLEAN NOT NULL DEFAULT TRUE,
    puede_comunicarse_docentes BOOLEAN NOT NULL DEFAULT TRUE,
    razon_restriccion        TEXT,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_restricciones_tutor IS 'Restricciones de acceso al portal por tutor (PE-033)';

CREATE TABLE IF NOT EXISTS ades_tareas_sistema (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tipo_tarea  VARCHAR(60) NOT NULL,
    payload_json JSONB      NOT NULL DEFAULT '{}',
    estado      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    error_msg   TEXT,
    intentos    SMALLINT    NOT NULL DEFAULT 0,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_tareas_sistema IS 'Cola de tareas async del sistema (notificaciones, Celery tasks)';

-- ──────────────────────────────────────────────────────────────
-- FASE 35: Salud Avanzada
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_medicamentos_alumno (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id           UUID         NOT NULL REFERENCES ades_estudiantes(id),
    nombre_medicamento  VARCHAR(100) NOT NULL,
    dosis               VARCHAR(100) NOT NULL,
    frecuencia          VARCHAR(80)  NOT NULL,
    horario             VARCHAR(80),
    via_administracion  VARCHAR(30)  NOT NULL DEFAULT 'ORAL',
    prescrito_por       VARCHAR(120),
    fecha_inicio        DATE,
    fecha_fin           DATE,
    observaciones       TEXT,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_medicamentos_alumno IS 'Medicamentos en uso de alumnos (SB-003)';
CREATE INDEX IF NOT EXISTS idx_med_alumno ON ades_medicamentos_alumno(alumno_id) WHERE is_active;

CREATE TABLE IF NOT EXISTS ades_actas_incidente_medico (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    incidente_id          UUID        NOT NULL,  -- FK a ades_incidentes_medicos si existe
    descripcion_detallada TEXT        NOT NULL,
    testigos              TEXT,
    medidas_tomadas       TEXT        NOT NULL,
    requirio_traslado     BOOLEAN     NOT NULL DEFAULT FALSE,
    hospital_destino      VARCHAR(200),
    notificado_familia    BOOLEAN     NOT NULL DEFAULT TRUE,
    firma_responsable     TEXT,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_actas_incidente_medico IS 'Actas formales de incidentes médicos (SB-005)';

CREATE TABLE IF NOT EXISTS ades_seguimiento_psicosocial (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id             UUID        NOT NULL REFERENCES ades_estudiantes(id),
    tipo_atencion         VARCHAR(30) NOT NULL,
    motivo                TEXT        NOT NULL,
    observaciones         TEXT        NOT NULL,
    estrategias_sugeridas TEXT,
    requiere_derivacion   BOOLEAN     NOT NULL DEFAULT FALSE,
    derivado_a            VARCHAR(200),
    proxima_sesion        DATE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_seguimiento_psicosocial IS 'Seguimiento psicosocial por alumno (SB-021)';
CREATE INDEX IF NOT EXISTS idx_psicosocial_alumno ON ades_seguimiento_psicosocial(alumno_id);

CREATE TABLE IF NOT EXISTS ades_tutorias (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id            UUID        NOT NULL REFERENCES ades_estudiantes(id),
    tipo_tutoria         VARCHAR(30) NOT NULL,
    tema                 VARCHAR(200) NOT NULL,
    descripcion          TEXT        NOT NULL,
    duracion_minutos     SMALLINT    NOT NULL DEFAULT 50,
    acuerdos             TEXT,
    proxima_sesion       DATE,
    requiere_seguimiento BOOLEAN     NOT NULL DEFAULT FALSE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_tutorias IS 'Sesiones de tutoría individual/grupal (SB-022)';
CREATE INDEX IF NOT EXISTS idx_tutoria_alumno ON ades_tutorias(alumno_id);

-- ──────────────────────────────────────────────────────────────
-- FASE 36: Foros y Anuncios
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_foros (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre      VARCHAR(120) NOT NULL,
    descripcion TEXT,
    tipo        VARCHAR(30)  NOT NULL DEFAULT 'GRUPO',
    grupo_id    UUID         REFERENCES ades_grupos(id),
    plantel_id  UUID         REFERENCES ades_planteles(id),
    es_moderado BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_por  UUID         REFERENCES ades_personas(id),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_foros IS 'Foros de discusión por grupo/plantel (CO-020)';

CREATE TABLE IF NOT EXISTS ades_mensajes_foro (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    foro_id          UUID        NOT NULL REFERENCES ades_foros(id),
    mensaje_padre_id UUID        REFERENCES ades_mensajes_foro(id),
    asunto           VARCHAR(200) NOT NULL,
    contenido        TEXT        NOT NULL,
    adjunto_url      TEXT,
    estado           VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO',
    autor_id         UUID        REFERENCES ades_personas(id),
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_mensajes_foro IS 'Mensajes en foros (CO-021)';
CREATE INDEX IF NOT EXISTS idx_msg_foro ON ades_mensajes_foro(foro_id) WHERE is_active;

CREATE TABLE IF NOT EXISTS ades_respuestas_foro (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    mensaje_id   UUID    NOT NULL REFERENCES ades_mensajes_foro(id),
    contenido    TEXT    NOT NULL,
    adjunto_url  TEXT,
    autor_id     UUID    REFERENCES ades_personas(id),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_respuestas_foro IS 'Respuestas encadenadas en foros (CO-022)';

CREATE TABLE IF NOT EXISTS ades_anuncios (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    titulo           VARCHAR(200) NOT NULL,
    contenido        TEXT         NOT NULL,
    plantel_id       UUID         REFERENCES ades_planteles(id),
    nivel_educativo  VARCHAR(30),
    fecha_inicio     DATE         NOT NULL,
    fecha_fin        DATE,
    es_urgente       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_anuncios IS 'Tablón de anuncios oficial (CO-023)';
CREATE INDEX IF NOT EXISTS idx_anuncios_fecha ON ades_anuncios(fecha_inicio, fecha_fin) WHERE is_active;

-- ──────────────────────────────────────────────────────────────
-- FASE 37: Compliance / Admin Avanzado
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_log_autenticacion (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id    TEXT        NOT NULL,
    ip_origen     INET,
    user_agent    TEXT,
    exitoso       BOOLEAN     NOT NULL DEFAULT TRUE,
    motivo_fallo  VARCHAR(100),
    fecha_login   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- auditoría mínima (no biu, es log)
    ref           UUID        DEFAULT uuidv7()
);
COMMENT ON TABLE ades_log_autenticacion IS 'Log de autenticaciones Authentik (AD-007)';
CREATE INDEX IF NOT EXISTS idx_log_auth_usuario ON ades_log_autenticacion(usuario_id, fecha_login DESC);

CREATE TABLE IF NOT EXISTS ades_normatividad (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre               VARCHAR(200) NOT NULL,
    tipo                 VARCHAR(20)  NOT NULL,
    descripcion          TEXT         NOT NULL,
    fecha_vigencia_inicio DATE         NOT NULL,
    fecha_vigencia_fin    DATE,
    url_documento        TEXT,
    aplica_primaria      BOOLEAN      NOT NULL DEFAULT TRUE,
    aplica_secundaria    BOOLEAN      NOT NULL DEFAULT TRUE,
    aplica_preparatoria  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_normatividad IS 'Catálogo de normatividad SEP/UAEMEX/Interna (AD-014)';

CREATE TABLE IF NOT EXISTS ades_retenciones (
    id                 UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id          UUID        NOT NULL REFERENCES ades_estudiantes(id),
    tipo_retencion     VARCHAR(30) NOT NULL,
    motivo             TEXT        NOT NULL,
    fecha_inicio       DATE        NOT NULL,
    fecha_fin          DATE,
    acciones_requeridas TEXT,
    autorizado_por     UUID        REFERENCES ades_personas(id),
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_retenciones IS 'Retenciones académicas/administrativas de alumnos (AD-017)';

CREATE TABLE IF NOT EXISTS ades_alertas_cumplimiento (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    tipo_alerta   VARCHAR(40) NOT NULL,
    descripcion   TEXT        NOT NULL,
    alumno_id     UUID        REFERENCES ades_estudiantes(id),
    plantel_id    UUID        REFERENCES ades_planteles(id),
    severidad     VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    requiere_accion BOOLEAN   NOT NULL DEFAULT TRUE,
    estado        VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_alertas_cumplimiento IS 'Alertas de incumplimiento normativo (AD-030)';
CREATE INDEX IF NOT EXISTS idx_alertas_estado ON ades_alertas_cumplimiento(estado, severidad);

-- ──────────────────────────────────────────────────────────────
-- FASE 38: IA Avanzada
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_evaluaciones_riesgo (
    alumno_id       UUID PRIMARY KEY REFERENCES ades_estudiantes(id),
    score_riesgo    SMALLINT    NOT NULL DEFAULT 0,
    nivel_riesgo    VARCHAR(10) NOT NULL DEFAULT 'BAJO',
    indicadores_json JSONB      NOT NULL DEFAULT '{}',
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_evaluaciones_riesgo IS 'Evaluaciones de riesgo de abandono escolar (IA-005)';

-- ades_conversaciones_ia ya debe existir del módulo de chatbot

-- ──────────────────────────────────────────────────────────────
-- FASE 39: Procesos Escolares
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_solicitudes_admision (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre              VARCHAR(100) NOT NULL,
    apellido_paterno    VARCHAR(80)  NOT NULL,
    apellido_materno    VARCHAR(80),
    fecha_nacimiento    DATE         NOT NULL,
    curp                CHAR(18)     NOT NULL,
    nivel_solicitado    VARCHAR(20)  NOT NULL,
    grado_solicitado    SMALLINT     NOT NULL,
    plantel_id          UUID         REFERENCES ades_planteles(id),
    ciclo_escolar_id    UUID         REFERENCES ades_ciclos_escolares(id),
    nombre_tutor        VARCHAR(150) NOT NULL,
    telefono_tutor      VARCHAR(20),
    email_tutor         VARCHAR(120),
    escuela_procedencia VARCHAR(200),
    promedio_procedencia NUMERIC(4,2),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    motivo_decision     TEXT,
    grupo_asignado_id   UUID         REFERENCES ades_grupos(id),
    fecha_resolucion    TIMESTAMPTZ,
    resuelto_por        UUID         REFERENCES ades_personas(id),
    fecha_solicitud     DATE         NOT NULL DEFAULT CURRENT_DATE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_solicitudes_admision IS 'Solicitudes de admisión de nuevos alumnos (PE-003)';
CREATE INDEX IF NOT EXISTS idx_admision_curp ON ades_solicitudes_admision(curp);
CREATE INDEX IF NOT EXISTS idx_admision_estado ON ades_solicitudes_admision(estado, plantel_id);

CREATE TABLE IF NOT EXISTS ades_documentos_admision (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    admision_id       UUID        NOT NULL REFERENCES ades_solicitudes_admision(id),
    tipo_documento    VARCHAR(60) NOT NULL,
    nombre_archivo    VARCHAR(200),
    url_documento     TEXT,
    estado_validacion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones     TEXT,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_documentos_admision IS 'Documentos del proceso de admisión (PE-006)';

CREATE TABLE IF NOT EXISTS ades_inscripciones_optativas (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    estudiante_id    UUID NOT NULL REFERENCES ades_estudiantes(id),
    materia_id       UUID NOT NULL REFERENCES ades_materias(id),
    ciclo_escolar_id UUID NOT NULL REFERENCES ades_ciclos_escolares(id),
    fecha_inscripcion DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system',
    UNIQUE (estudiante_id, materia_id, ciclo_escolar_id)
);
COMMENT ON TABLE ades_inscripciones_optativas IS 'Inscripción de alumnos a materias optativas/electivas (PE-014)';

CREATE TABLE IF NOT EXISTS ades_acuerdos_convivencia (
    id                 UUID PRIMARY KEY DEFAULT uuidv7(),
    alumno_id          UUID        NOT NULL REFERENCES ades_estudiantes(id),
    tutor_nombre       VARCHAR(150) NOT NULL,
    tutor_firma_hash   TEXT,
    ip_firma           INET,
    firmado_por_usuario TEXT       NOT NULL,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_acuerdos_convivencia IS 'Registro de firma de acuerdo de convivencia (PE-026)';

CREATE TABLE IF NOT EXISTS ades_calendarios_academicos (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre           VARCHAR(150) NOT NULL,
    ciclo_escolar_id UUID         REFERENCES ades_ciclos_escolares(id),
    nivel_educativo  VARCHAR(30)  NOT NULL,
    tipo             VARCHAR(40)  NOT NULL,
    fecha_inicio     DATE         NOT NULL,
    fecha_fin        DATE         NOT NULL,
    descripcion      TEXT,
    es_oficial       BOOLEAN      NOT NULL DEFAULT TRUE,
    plantel_id       UUID         REFERENCES ades_planteles(id),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_calendarios_academicos IS 'Calendario académico SEP/UAEMEX por ciclo y nivel (AC-005)';
CREATE INDEX IF NOT EXISTS idx_cal_acad_ciclo ON ades_calendarios_academicos(ciclo_escolar_id, nivel_educativo);

CREATE TABLE IF NOT EXISTS ades_periodos_evaluacion (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    ciclo_escolar_id UUID        NOT NULL REFERENCES ades_ciclos_escolares(id),
    nombre_periodo   VARCHAR(80) NOT NULL,
    nivel_educativo  VARCHAR(30) NOT NULL,
    tipo_evaluacion  VARCHAR(30) NOT NULL,
    fecha_inicio     DATE        NOT NULL,
    fecha_fin        DATE        NOT NULL,
    abierto          BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                  UUID,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion     TEXT        NOT NULL DEFAULT 'system',
    usuario_modificacion TEXT        NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE ades_periodos_evaluacion IS 'Períodos de evaluación con apertura/cierre (AC-014)';

-- ──────────────────────────────────────────────────────────────
-- Asignar triggers de auditoría (biu en DEV)
-- ──────────────────────────────────────────────────────────────

SELECT auditoria.asignar_biu('public.ades_escalas_evaluacion');
SELECT auditoria.asignar_biu('public.ades_observaciones_pedagogicas');
SELECT auditoria.asignar_biu('public.ades_nee');
SELECT auditoria.asignar_biu('public.ades_asignaciones_aula');
SELECT auditoria.asignar_biu('public.ades_tutores_alumnos');
SELECT auditoria.asignar_biu('public.ades_restricciones_tutor');
SELECT auditoria.asignar_biu('public.ades_tareas_sistema');
SELECT auditoria.asignar_biu('public.ades_medicamentos_alumno');
SELECT auditoria.asignar_biu('public.ades_actas_incidente_medico');
SELECT auditoria.asignar_biu('public.ades_seguimiento_psicosocial');
SELECT auditoria.asignar_biu('public.ades_tutorias');
SELECT auditoria.asignar_biu('public.ades_foros');
SELECT auditoria.asignar_biu('public.ades_mensajes_foro');
SELECT auditoria.asignar_biu('public.ades_respuestas_foro');
SELECT auditoria.asignar_biu('public.ades_anuncios');
SELECT auditoria.asignar_biu('public.ades_normatividad');
SELECT auditoria.asignar_biu('public.ades_retenciones');
SELECT auditoria.asignar_biu('public.ades_alertas_cumplimiento');
SELECT auditoria.asignar_biu('public.ades_evaluaciones_riesgo');
SELECT auditoria.asignar_biu('public.ades_solicitudes_admision');
SELECT auditoria.asignar_biu('public.ades_documentos_admision');
SELECT auditoria.asignar_biu('public.ades_inscripciones_optativas');
SELECT auditoria.asignar_biu('public.ades_acuerdos_convivencia');
SELECT auditoria.asignar_biu('public.ades_calendarios_academicos');
SELECT auditoria.asignar_biu('public.ades_periodos_evaluacion');

COMMIT;

-- Verificar cobertura
SELECT * FROM auditoria.reporte_cobertura() ORDER BY tabla;

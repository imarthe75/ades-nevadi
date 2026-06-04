-- =============================================================================
-- Migración 004 — Certificados digitales y mejoras rúbricas
-- Ejecutar: docker compose exec -T postgres psql -U ades_admin -d ades -f /docker-entrypoint-initdb.d/migrations/004_certificados_rubricas.sql
-- =============================================================================

-- ── 1. Tabla de certificados digitales ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_certificados (
    id                  uuid        PRIMARY KEY DEFAULT uuidv7(),
    estudiante_id       uuid        NOT NULL REFERENCES ades_estudiantes(id),
    ciclo_escolar_id    uuid        NOT NULL REFERENCES ades_ciclos_escolares(id),
    tipo_certificado    varchar(40) NOT NULL DEFAULT 'ESTUDIOS',
    -- ESTUDIOS | CONDUCTA | PARTICIPACION | MERITO_ACADEMICO | ASISTENCIA_PERFECTA
    folio               varchar(60) UNIQUE NOT NULL DEFAULT
                            UPPER(LEFT(REPLACE(gen_random_uuid()::text, '-', ''), 16)),
    nivel_educativo     varchar(50) NOT NULL,
    grado_completado    varchar(100),
    promedio_final      numeric(5,2),
    fecha_emision       date        NOT NULL DEFAULT CURRENT_DATE,
    fecha_vencimiento   date,
    vigente             boolean     NOT NULL DEFAULT TRUE,
    datos_adicionales   jsonb,
    emitido_por_id      uuid        REFERENCES ades_usuarios(id),
    ref                 uuid        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active           boolean     NOT NULL DEFAULT TRUE,
    fccreacion          timestamptz NOT NULL DEFAULT NOW(),
    fcmodificacion      timestamptz NOT NULL DEFAULT NOW(),
    usuario_creacion    varchar(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion varchar(150) NOT NULL DEFAULT CURRENT_USER,
    row_version         integer     NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_ades_cert_estudiante
    ON ades_certificados (estudiante_id, tipo_certificado);

CREATE INDEX IF NOT EXISTS idx_ades_cert_folio
    ON ades_certificados (folio);

CREATE TRIGGER trg_aud_biu
    BEFORE INSERT OR UPDATE ON ades_certificados
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

COMMENT ON TABLE ades_certificados IS 'Certificados digitales con folio único verificable';
COMMENT ON COLUMN ades_certificados.folio IS 'Código alfanumérico público para verificación de autenticidad';

-- ── 2. Índice de búsqueda rápida en rúbricas ─────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ades_rubricas_materia
    ON ades_rubricas (materia_id, nivel_educativo_id)
    WHERE is_active = TRUE;

-- ── 3. Agregar columna niveles_logro a rubrica_criterios ─────────────────────
-- Almacena los 4 niveles de logro como JSONB:
-- [{"nivel": 1, "etiqueta": "Inicial", "descripcion": "..."}, ...]
ALTER TABLE ades_rubrica_criterios
    ADD COLUMN IF NOT EXISTS niveles_logro jsonb;

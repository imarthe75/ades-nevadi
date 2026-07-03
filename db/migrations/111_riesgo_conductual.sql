-- =============================================================================
-- Migración: 111_riesgo_conductual.sql
-- Descripción: SB-016 — análisis de patrones de conducta (factores de riesgo).
--              Mismo patrón que ades_evaluaciones_riesgo (IA-005, riesgo
--              académico) aplicado a incidentes de conducta: score basado en
--              frecuencia y severidad de faltas en una ventana de tiempo.
-- Tablas afectadas: ades_riesgo_conductual (nueva)
-- Dependencias: ades_estudiantes, ades_reportes_conducta, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_riesgo_conductual (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    estudiante_id        UUID NOT NULL REFERENCES ades_estudiantes(id),
    score_riesgo         NUMERIC(5,2) NOT NULL,
    nivel_riesgo         VARCHAR(10) NOT NULL CHECK (nivel_riesgo IN ('BAJO', 'MEDIO', 'ALTO')),
    total_incidentes     INTEGER NOT NULL DEFAULT 0,
    incidentes_graves    INTEGER NOT NULL DEFAULT 0,
    ventana_dias         INTEGER NOT NULL DEFAULT 90,
    indicadores_json     JSONB,
    fecha_calculo        TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

COMMENT ON TABLE ades_riesgo_conductual IS
    'SB-016 — score de riesgo conductual por alumno basado en frecuencia/severidad de faltas en una ventana de tiempo (regla, no IA).';

CREATE INDEX IF NOT EXISTS idx_riesgo_conductual_estudiante ON ades_riesgo_conductual(estudiante_id, fecha_calculo DESC);

SELECT auditoria.asignar_biu('public.ades_riesgo_conductual');

-- =============================================================================
-- MIGRACION 089 - Centinela-AI: Auditoria de cumplimiento integral
-- ----------------------------------------------------------------------------
-- Crea tablas para persistir escaneos de compliance y issues SOAR en GitLab.
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.ades_compliance_scans (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id             TEXT NOT NULL,
    commit_sha             TEXT NOT NULL,
    repository_url         TEXT NOT NULL,
    branch                 TEXT,
    merge_request_iid      INTEGER,
    status                 TEXT NOT NULL DEFAULT 'completed',
    severity_critical      INTEGER NOT NULL DEFAULT 0,
    severity_high          INTEGER NOT NULL DEFAULT 0,
    severity_medium        INTEGER NOT NULL DEFAULT 0,
    severity_low           INTEGER NOT NULL DEFAULT 0,
    findings_total         INTEGER NOT NULL DEFAULT 0,
    raw_scan               JSONB NOT NULL DEFAULT '{}'::jsonb,
    enriched_scan          JSONB NOT NULL DEFAULT '{}'::jsonb,
    scanned_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ref                    UUID,
    row_version            INTEGER,
    fecha_creacion         TIMESTAMPTZ,
    fecha_modificacion     TIMESTAMPTZ,
    usuario_creacion       TEXT,
    usuario_modificacion   TEXT
);

CREATE TABLE IF NOT EXISTS public.ades_compliance_issues (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id                UUID NOT NULL REFERENCES public.ades_compliance_scans(id) ON DELETE CASCADE,
    project_id             TEXT NOT NULL,
    finding_id             TEXT,
    issue_iid              INTEGER,
    issue_web_url          TEXT,
    issue_title            TEXT NOT NULL,
    severity               TEXT NOT NULL,
    standard               TEXT,
    estado                 TEXT NOT NULL DEFAULT 'opened',
    ref                    UUID,
    row_version            INTEGER,
    fecha_creacion         TIMESTAMPTZ,
    fecha_modificacion     TIMESTAMPTZ,
    usuario_creacion       TEXT,
    usuario_modificacion   TEXT
);

CREATE INDEX IF NOT EXISTS idx_comp_scans_project_time
    ON public.ades_compliance_scans(project_id, scanned_at DESC);

CREATE INDEX IF NOT EXISTS idx_comp_scans_commit
    ON public.ades_compliance_scans(commit_sha);

CREATE INDEX IF NOT EXISTS idx_comp_issues_scan
    ON public.ades_compliance_issues(scan_id);

CREATE INDEX IF NOT EXISTS idx_comp_issues_project
    ON public.ades_compliance_issues(project_id);

SELECT auditoria.asignar_biu('public.ades_compliance_scans');
SELECT auditoria.asignar_biu('public.ades_compliance_issues');

COMMENT ON TABLE public.ades_compliance_scans IS
    'Resultados agregados de auditorias Shift-Left (SAST/SCA/DLP/IaC) enriquecidas por Aura-Sentinel.';

COMMENT ON TABLE public.ades_compliance_issues IS
    'Trazabilidad SOAR de issues creados automaticamente en GitLab por hallazgos criticos/altos.';

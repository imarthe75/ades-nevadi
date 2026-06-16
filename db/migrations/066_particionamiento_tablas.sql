-- ============================================================
-- Migración 066: Particionamiento de tablas grandes
-- SPRINT 5 — ADES Instituto Nevadi
-- ============================================================
-- Tablas particionadas:
--   1. ades_asistencias        → RANGE(fecha_creacion) por año
--   2. ades_calificaciones_periodo → RANGE(fecha_creacion) por año
-- Optimización BRIN/composite index:
--   3. ades_codigos_postales, ades_localidades, ades_tareas_entregas
-- ============================================================
-- TRADE-OFFS DE PARTICIONAMIENTO EN PG18:
--   - UNIQUE constraints globales requieren incluir partition key → se eliminan
--   - FK entrantes (otras tablas → esta) no pueden referenciar solo (id) → se eliminan
--   - FK salientes (esta → otras tablas) se recrean normalmente
--   - Tablas hijo (ades_justificaciones_falta, ades_calificaciones_historico)
--     tienen 0 filas → eliminación de FK es segura
-- ============================================================

BEGIN;

-- ============================================================
-- 0. ELIMINAR VISTAS MATERIALIZADAS DEPENDIENTES
--    (se recrean al final apuntando a las tablas particionadas)
-- ============================================================

-- Orden: dependientes primero
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_resumen_plantel;       -- depende de mv_riesgo_academico
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_riesgo_academico;      -- depende de asistencias + calificaciones
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_asistencia_diaria;     -- depende de asistencias
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_calificaciones_grupo;  -- depende de calificaciones_periodo
DROP VIEW             IF EXISTS public.v_indicadores_cierre_ciclo; -- vista regular, depende de calificaciones_periodo
DROP MATERIALIZED VIEW IF EXISTS public.v_asistencias_resumen;     -- depende de asistencias

-- ============================================================
-- 1. PARTICIONAR ades_asistencias POR AÑO
-- ============================================================

-- 1.1 Eliminar FK entrante desde justificaciones_falta (tabla con 0 filas)
ALTER TABLE ades_justificaciones_falta
    DROP CONSTRAINT IF EXISTS ades_justificaciones_falta_asistencia_id_fkey;

-- 1.2 Eliminar FK circular: asistencias → justificaciones (se invierte la relación en la app)
ALTER TABLE ades_asistencias
    DROP CONSTRAINT IF EXISTS ades_asistencias_justificacion_id_fkey;

-- 1.3 Guardar definición de triggers especializados antes de renombrar
-- trg_recalcular_desde_asistencia ya existe como función — solo necesitamos recrear el trigger

-- 1.4 Renombrar tabla original
ALTER TABLE ades_asistencias RENAME TO ades_asistencias_legacy;

-- 1.5 Crear tabla particionada con estructura idéntica a la real
CREATE TABLE ades_asistencias (
    id                   UUID         NOT NULL DEFAULT uuidv7(),
    clase_id             UUID         NOT NULL,
    estudiante_id        UUID         NOT NULL,
    estatus_asistencia   VARCHAR(20)  NOT NULL DEFAULT 'PRESENTE',
    observacion          VARCHAR(255),
    ref                  UUID         NOT NULL DEFAULT uuidv7(),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    row_version          INTEGER      NOT NULL DEFAULT 1,
    justificacion_id     UUID,
    PRIMARY KEY (id, fecha_creacion)
) PARTITION BY RANGE (fecha_creacion);

-- 1.6 Crear particiones por año (ciclos histórico + futuros)
CREATE TABLE ades_asistencias_2025
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2025-01-01 00:00:00+00') TO ('2026-01-01 00:00:00+00');

CREATE TABLE ades_asistencias_2026
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE ades_asistencias_2027
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');

CREATE TABLE ades_asistencias_2028
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2028-01-01 00:00:00+00') TO ('2029-01-01 00:00:00+00');

CREATE TABLE ades_asistencias_default
    PARTITION OF ades_asistencias DEFAULT;

-- 1.7 FK salientes — se recrean en tabla particionada (PG11+ soporta FK desde particionadas)
ALTER TABLE ades_asistencias
    ADD CONSTRAINT ades_asistencias_clase_id_fkey
    FOREIGN KEY (clase_id) REFERENCES ades_clases(id);

ALTER TABLE ades_asistencias
    ADD CONSTRAINT ades_asistencias_estudiante_id_fkey
    FOREIGN KEY (estudiante_id) REFERENCES ades_estudiantes(id);

-- 1.8 Índices (se propagan automáticamente a todas las particiones)
CREATE INDEX idx_asistencias_clase_id      ON ades_asistencias (clase_id);
CREATE INDEX idx_asistencias_estudiante_id ON ades_asistencias (estudiante_id);
CREATE INDEX idx_asistencias_estatus       ON ades_asistencias (estatus_asistencia);
CREATE INDEX idx_asistencias_est_clase     ON ades_asistencias (estudiante_id, clase_id, estatus_asistencia);
CREATE INDEX idx_asistencias_fecha_brin    ON ades_asistencias USING brin (fecha_creacion);

-- 1.9 Copiar datos desde legacy
INSERT INTO ades_asistencias
    (id, clase_id, estudiante_id, estatus_asistencia, observacion,
     ref, is_active, fecha_creacion, fecha_modificacion, usuario_creacion,
     usuario_modificacion, row_version, justificacion_id)
SELECT id, clase_id, estudiante_id, estatus_asistencia, observacion,
       ref, is_active, fecha_creacion, fecha_modificacion, usuario_creacion,
       usuario_modificacion, row_version, justificacion_id
FROM ades_asistencias_legacy;

-- 1.10 Triggers en tabla particionada
SELECT auditoria.asignar_biu('public.ades_asistencias');

CREATE TRIGGER trg_gradebook_asistencia
    AFTER INSERT OR UPDATE OF estatus_asistencia ON ades_asistencias
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_desde_asistencia();

-- 1.11 Verificar integridad
DO $$
DECLARE v_original BIGINT; v_nueva BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_original FROM ades_asistencias_legacy;
    SELECT COUNT(*) INTO v_nueva   FROM ades_asistencias;
    IF v_original <> v_nueva THEN
        RAISE EXCEPTION 'Migración asistencias fallida: original=% nueva=%', v_original, v_nueva;
    END IF;
    RAISE NOTICE 'ades_asistencias: % filas migradas OK', v_nueva;
END;
$$;

-- 1.12 Eliminar tabla legacy
DROP TABLE ades_asistencias_legacy;

-- ============================================================
-- 2. PARTICIONAR ades_calificaciones_periodo POR AÑO
-- ============================================================

-- 2.1 Eliminar FK entrante desde calificaciones_historico (0 filas)
ALTER TABLE ades_calificaciones_historico
    DROP CONSTRAINT IF EXISTS ades_calificaciones_historico_cal_periodo_id_fkey;

-- 2.2 Renombrar tabla original
ALTER TABLE ades_calificaciones_periodo RENAME TO ades_calificaciones_periodo_legacy;

-- 2.3 Crear tabla particionada con estructura idéntica a la real
CREATE TABLE ades_calificaciones_periodo (
    id                   UUID         NOT NULL DEFAULT uuidv7(),
    estudiante_id        UUID         NOT NULL,
    grupo_id             UUID         NOT NULL,
    materia_id           UUID         NOT NULL,
    periodo_evaluacion_id UUID         NOT NULL,
    calificacion_final   NUMERIC(5,2) NOT NULL,
    observaciones        TEXT,
    ref                  UUID         NOT NULL DEFAULT uuidv7(),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    row_version          INTEGER      NOT NULL DEFAULT 1,
    score_por_item       JSONB                 DEFAULT '{}',
    calificacion_calculada NUMERIC(5,2),
    ajuste_manual        NUMERIC(5,2),
    justificacion_ajuste TEXT,
    fecha_calculo        TIMESTAMPTZ,
    fecha_cierre         TIMESTAMPTZ,
    cerrada              BOOLEAN      NOT NULL DEFAULT FALSE,
    inasistencias        SMALLINT              DEFAULT 0,
    justificadas         SMALLINT              DEFAULT 0,
    es_acreditado        BOOLEAN      NOT NULL DEFAULT FALSE,
    cerrado_por          UUID,
    PRIMARY KEY (id, fecha_creacion),
    CONSTRAINT chk_cal_periodo_rango
        CHECK (calificacion_final >= 0 AND calificacion_final <= 100),
    CONSTRAINT chk_cal_periodo_calculada_rango
        CHECK (calificacion_calculada IS NULL OR (calificacion_calculada >= 0 AND calificacion_calculada <= 100))
) PARTITION BY RANGE (fecha_creacion);

-- 2.4 Crear particiones por año
CREATE TABLE ades_calificaciones_periodo_2025
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2025-01-01 00:00:00+00') TO ('2026-01-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_2026
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_2027
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_2028
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2028-01-01 00:00:00+00') TO ('2029-01-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_default
    PARTITION OF ades_calificaciones_periodo DEFAULT;

-- 2.5 FK salientes — se recrean normalmente
ALTER TABLE ades_calificaciones_periodo
    ADD CONSTRAINT ades_calificaciones_periodo_estudiante_id_fkey
    FOREIGN KEY (estudiante_id) REFERENCES ades_estudiantes(id);

ALTER TABLE ades_calificaciones_periodo
    ADD CONSTRAINT ades_calificaciones_periodo_grupo_id_fkey
    FOREIGN KEY (grupo_id) REFERENCES ades_grupos(id);

ALTER TABLE ades_calificaciones_periodo
    ADD CONSTRAINT ades_calificaciones_periodo_materia_id_fkey
    FOREIGN KEY (materia_id) REFERENCES ades_materias(id);

ALTER TABLE ades_calificaciones_periodo
    ADD CONSTRAINT ades_calificaciones_periodo_periodo_evaluacion_id_fkey
    FOREIGN KEY (periodo_evaluacion_id) REFERENCES ades_periodos_evaluacion(id);

ALTER TABLE ades_calificaciones_periodo
    ADD CONSTRAINT ades_calificaciones_periodo_cerrado_por_fkey
    FOREIGN KEY (cerrado_por) REFERENCES ades_usuarios(id) ON DELETE SET NULL;

-- 2.6 Índices
CREATE INDEX idx_calper_estudiante_id ON ades_calificaciones_periodo (estudiante_id);
CREATE INDEX idx_calper_grupo_id      ON ades_calificaciones_periodo (grupo_id);
CREATE INDEX idx_calper_materia_id    ON ades_calificaciones_periodo (materia_id);
CREATE INDEX idx_calper_periodo_id    ON ades_calificaciones_periodo (periodo_evaluacion_id);
CREATE INDEX idx_calper_fecha_brin    ON ades_calificaciones_periodo USING brin (fecha_creacion);
CREATE INDEX idx_calper_est_cal_acred ON ades_calificaciones_periodo (estudiante_id, calificacion_final, es_acreditado);
CREATE INDEX idx_calper_score_jsonb   ON ades_calificaciones_periodo USING gin (score_por_item);

-- 2.7 Copiar datos desde legacy
INSERT INTO ades_calificaciones_periodo
SELECT * FROM ades_calificaciones_periodo_legacy;

-- 2.8 Triggers
SELECT auditoria.asignar_biu('public.ades_calificaciones_periodo');

CREATE TRIGGER trg_calificacion_periodo_acreditado
    BEFORE INSERT OR UPDATE OF calificacion_final, grupo_id ON ades_calificaciones_periodo
    FOR EACH ROW EXECUTE FUNCTION trg_set_es_acreditado();

-- 2.9 Verificar integridad
DO $$
DECLARE v_original BIGINT; v_nueva BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_original FROM ades_calificaciones_periodo_legacy;
    SELECT COUNT(*) INTO v_nueva   FROM ades_calificaciones_periodo;
    IF v_original <> v_nueva THEN
        RAISE EXCEPTION 'Migración calificaciones_periodo fallida: original=% nueva=%', v_original, v_nueva;
    END IF;
    RAISE NOTICE 'ades_calificaciones_periodo: % filas migradas OK', v_nueva;
END;
$$;

-- 2.10 Eliminar tabla legacy
DROP TABLE ades_calificaciones_periodo_legacy;

-- ============================================================
-- 3. RECREAR VISTAS MATERIALIZADAS (apuntan a tablas particionadas)
-- ============================================================

-- 3a. mv_asistencia_diaria (depende solo de ades_asistencias)
CREATE MATERIALIZED VIEW ades_bi.mv_asistencia_diaria AS
SELECT cl.fecha_clase AS fecha,
    cl.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    count(DISTINCT a.estudiante_id) AS total_alumnos,
    sum(CASE WHEN a.estatus_asistencia::text = 'PRESENTE'::text THEN 1 ELSE 0 END) AS presentes,
    sum(CASE WHEN a.estatus_asistencia::text = 'AUSENTE'::text  THEN 1 ELSE 0 END) AS ausentes,
    sum(CASE WHEN a.estatus_asistencia::text = 'TARDANZA'::text THEN 1 ELSE 0 END) AS tardanzas,
    round(100.0 * sum(CASE WHEN a.estatus_asistencia::text = 'PRESENTE'::text THEN 1 ELSE 0 END)::numeric
        / NULLIF(count(DISTINCT a.estudiante_id), 0)::numeric, 2) AS pct_asistencia
FROM ades_asistencias a
JOIN ades_clases cl               ON cl.id  = a.clase_id
JOIN ades_grupos g                ON g.id   = cl.grupo_id
JOIN ades_grados gr               ON gr.id  = g.grado_id
JOIN ades_planteles pl            ON pl.id  = gr.plantel_id
JOIN ades_niveles_educativos ne   ON ne.id  = gr.nivel_educativo_id
GROUP BY cl.grupo_id, cl.fecha_clase, g.nombre_grupo, gr.nombre_grado, pl.nombre_plantel, ne.nombre_nivel
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_asistencia_diaria
    ON ades_bi.mv_asistencia_diaria (fecha, grupo_id);

-- 3b. mv_riesgo_academico (depende de ades_asistencias + ades_calificaciones_periodo)
CREATE MATERIALIZED VIEW ades_bi.mv_riesgo_academico AS
SELECT e.id AS estudiante_id,
    (p.nombre::text || ' '::text) || p.apellido_paterno::text AS nombre_alumno,
    i.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    round(avg(cp.calificacion_final), 2) AS promedio_general,
    round(100.0 * sum(CASE WHEN a.estatus_asistencia::text = 'PRESENTE'::text THEN 1 ELSE 0 END)::numeric
        / NULLIF(count(DISTINCT a.id), 0)::numeric, 1) AS pct_asistencia,
    count(DISTINCT CASE WHEN cp.calificacion_final < 6.0 THEN cp.materia_id ELSE NULL::uuid END) AS materias_reprobadas,
    CASE
        WHEN avg(cp.calificacion_final) < 5.0
          OR (100.0 * sum(CASE WHEN a.estatus_asistencia::text = 'PRESENTE'::text THEN 1 ELSE 0 END)::numeric
              / NULLIF(count(DISTINCT a.id), 0)::numeric) < 70 THEN 'ALTO'::text
        WHEN avg(cp.calificacion_final) < 6.0
          OR (100.0 * sum(CASE WHEN a.estatus_asistencia::text = 'PRESENTE'::text THEN 1 ELSE 0 END)::numeric
              / NULLIF(count(DISTINCT a.id), 0)::numeric) < 80 THEN 'MEDIO'::text
        ELSE 'BAJO'::text
    END AS nivel_riesgo
FROM ades_estudiantes e
JOIN ades_personas p              ON p.id  = e.persona_id
JOIN ades_inscripciones i         ON i.estudiante_id = e.id AND i.is_active = true
JOIN ades_ciclos_escolares c      ON c.id  = i.ciclo_escolar_id AND c.es_vigente = true
JOIN ades_grupos g                ON g.id  = i.grupo_id
JOIN ades_grados gr               ON gr.id = g.grado_id
JOIN ades_planteles pl            ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne   ON ne.id = gr.nivel_educativo_id
LEFT JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = e.id AND cp.grupo_id = i.grupo_id
LEFT JOIN ades_clases cl          ON cl.grupo_id = i.grupo_id
LEFT JOIN ades_asistencias a      ON a.clase_id = cl.id AND a.estudiante_id = e.id
GROUP BY e.id, p.nombre, p.apellido_paterno, i.grupo_id, g.nombre_grupo, gr.nombre_grado, pl.nombre_plantel, ne.nombre_nivel
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_riesgo_academico
    ON ades_bi.mv_riesgo_academico (estudiante_id, grupo_id);

-- 3c. mv_resumen_plantel (depende de mv_riesgo_academico)
CREATE MATERIALIZED VIEW ades_bi.mv_resumen_plantel AS
SELECT pl.id AS plantel_id,
    pl.nombre_plantel,
    ne.nombre_nivel,
    count(DISTINCT i.estudiante_id) AS total_alumnos,
    count(DISTINCT g.id) AS total_grupos,
    round(avg(cp.calificacion_final), 2) AS promedio_institucional,
    round(100.0 * count(DISTINCT CASE WHEN ra.nivel_riesgo = 'ALTO'::text THEN ra.estudiante_id ELSE NULL::uuid END)::numeric
        / NULLIF(count(DISTINCT i.estudiante_id), 0)::numeric, 1) AS pct_riesgo_alto
FROM ades_planteles pl
JOIN ades_grados gr               ON gr.plantel_id = pl.id
JOIN ades_niveles_educativos ne   ON ne.id = gr.nivel_educativo_id
JOIN ades_grupos g                ON g.grado_id = gr.id AND g.is_active = true
JOIN ades_inscripciones i         ON i.grupo_id = g.id AND i.is_active = true
JOIN ades_ciclos_escolares c      ON c.id = i.ciclo_escolar_id AND c.es_vigente = true
LEFT JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = i.estudiante_id
LEFT JOIN ades_bi.mv_riesgo_academico ra ON ra.estudiante_id = i.estudiante_id
GROUP BY pl.id, pl.nombre_plantel, ne.nombre_nivel
WITH NO DATA;

-- 3d. mv_calificaciones_grupo (materializada — depende de calificaciones_periodo)
CREATE MATERIALIZED VIEW ades_bi.mv_calificaciones_grupo AS
SELECT i.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    m.nombre_materia,
    m.id AS materia_id,
    pe.nombre_periodo,
    pe.numero_periodo,
    count(DISTINCT cp.estudiante_id) AS alumnos_evaluados,
    round(avg(cp.calificacion_final), 2) AS promedio,
    min(cp.calificacion_final) AS minimo,
    max(cp.calificacion_final) AS maximo,
    sum(CASE WHEN cp.calificacion_final >= 6.0 THEN 1 ELSE 0 END) AS aprobados,
    sum(CASE WHEN cp.calificacion_final <  6.0 THEN 1 ELSE 0 END) AS reprobados
FROM ades_calificaciones_periodo cp
JOIN ades_inscripciones i         ON i.estudiante_id = cp.estudiante_id AND i.grupo_id = cp.grupo_id AND i.is_active = true
JOIN ades_grupos g                ON g.id  = i.grupo_id
JOIN ades_grados gr               ON gr.id = g.grado_id
JOIN ades_planteles pl            ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne   ON ne.id = gr.nivel_educativo_id
JOIN ades_materias m              ON m.id  = cp.materia_id
JOIN ades_periodos_evaluacion pe  ON pe.id = cp.periodo_evaluacion_id
GROUP BY i.grupo_id, g.nombre_grupo, gr.nombre_grado, pl.nombre_plantel, ne.nombre_nivel,
         m.nombre_materia, m.id, pe.nombre_periodo, pe.numero_periodo
WITH NO DATA;

-- 3e. v_indicadores_cierre_ciclo (vista regular — depende de calificaciones_periodo)
CREATE VIEW public.v_indicadores_cierre_ciclo AS
SELECT c.id AS ciclo_escolar_id,
    c.nombre_ciclo,
    c.nivel_educativo_id,
    n.nombre_nivel,
    c.fecha_inicio,
    c.fecha_fin,
    c.estado,
    (SELECT count(DISTINCT i.estudiante_id)
     FROM ades_inscripciones i WHERE i.ciclo_escolar_id = c.id AND i.is_active = true) AS matricula_total,
    (SELECT count(DISTINCT ad.profesor_id)
     FROM ades_asignaciones_docentes ad
     JOIN ades_grupos g ON ad.grupo_id = g.id
     WHERE g.ciclo_escolar_id = c.id AND ad.is_active = true) AS total_docentes,
    COALESCE((SELECT round(avg(cp.calificacion_final), 2)
     FROM ades_calificaciones_periodo cp
     JOIN ades_periodos_evaluacion pe ON cp.periodo_evaluacion_id = pe.id
     WHERE pe.ciclo_escolar_id = c.id AND cp.is_active = true), 0.0) AS promedio_general,
    COALESCE((SELECT round(count(CASE WHEN cp.es_acreditado = true THEN 1 ELSE NULL::integer END)::numeric
                           * 100.0 / NULLIF(count(cp.id), 0)::numeric, 2)
     FROM ades_calificaciones_periodo cp
     JOIN ades_periodos_evaluacion pe ON cp.periodo_evaluacion_id = pe.id
     WHERE pe.ciclo_escolar_id = c.id AND cp.is_active = true), 100.00) AS tasa_aprobacion,
    (SELECT count(*)
     FROM ades_bajas b
     JOIN ades_inscripciones i ON b.estudiante_id = i.estudiante_id
     WHERE i.ciclo_escolar_id = c.id AND b.is_active = true) AS total_bajas,
    (SELECT count(*)
     FROM ades_inscripciones i
     JOIN ades_estudiantes e ON i.estudiante_id = e.id
     WHERE i.ciclo_escolar_id = c.id AND i.is_active = true AND e.is_active = true) AS total_alumnos_activos
FROM ades_ciclos_escolares c
JOIN ades_niveles_educativos n ON c.nivel_educativo_id = n.id;

-- 3f. v_asistencias_resumen (vista public)
CREATE MATERIALIZED VIEW public.v_asistencias_resumen AS
SELECT estudiante_id,
    estatus_asistencia,
    count(*) AS total
FROM ades_asistencias a
GROUP BY estudiante_id, estatus_asistencia
WITH NO DATA;

-- ============================================================
-- 4. BRIN / composite indexes en tablas de referencia estática
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_codpos_cp_localidad
    ON ades_codigos_postales (codigo_postal, localidad_id);

CREATE INDEX IF NOT EXISTS idx_codpos_municipio
    ON ades_codigos_postales (municipio_id);

CREATE INDEX IF NOT EXISTS idx_localidades_municipio
    ON ades_localidades (municipio_id);

CREATE INDEX IF NOT EXISTS idx_entregas_brin_fecha
    ON ades_tareas_entregas USING brin (fecha_creacion)
    WITH (pages_per_range = 128);

-- ============================================================
-- 5. VACUUM ANALYZE post-migración
-- ============================================================

ANALYZE ades_asistencias;
ANALYZE ades_calificaciones_periodo;
ANALYZE ades_codigos_postales;
ANALYZE ades_localidades;

COMMIT;

DO $$
BEGIN
    RAISE NOTICE '=== Mig 066 SPRINT 5: Particionamiento completado ===';
    RAISE NOTICE 'ades_asistencias: RANGE/año (2025-2028 + default)';
    RAISE NOTICE 'ades_calificaciones_periodo: RANGE/año (2025-2028 + default)';
    RAISE NOTICE 'Trade-offs: UNIQUE globales eliminados (no soportados en PG particiones)';
    RAISE NOTICE 'FK entrantes eliminadas: se mantiene integridad a nivel aplicación';
    RAISE NOTICE '=====================================================';
END;
$$;

-- ============================================================
-- MIGRACIÓN 007 — GRADEBOOK CURRICULAR INTEGRADO
-- Módulo de ponderaciones, actividades evaluables y cálculo
-- automático de calificaciones finales por período.
-- ============================================================
-- Ejecutar:
--   docker exec -i ades-postgres psql -U ades_admin -d ades < 007_gradebook.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 0. Escala de calificación por nivel educativo
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_niveles_educativos
    ADD COLUMN IF NOT EXISTS escala_maxima     NUMERIC(5,1) NOT NULL DEFAULT 10.0,
    ADD COLUMN IF NOT EXISTS minimo_aprobatorio NUMERIC(5,1) NOT NULL DEFAULT 6.0;

-- Primaria y Secundaria SEP: 0-10, mínimo 6
UPDATE ades_niveles_educativos
   SET escala_maxima = 10.0, minimo_aprobatorio = 6.0
 WHERE nombre_nivel IN ('PRIMARIA', 'SECUNDARIA');

-- Preparatoria UAEMEX: 0-100, mínimo 60
UPDATE ades_niveles_educativos
   SET escala_maxima = 100.0, minimo_aprobatorio = 60.0
 WHERE nombre_nivel = 'PREPARATORIA';

-- ─────────────────────────────────────────────────────────────
-- 1. Esquemas de ponderación
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_esquemas_ponderacion (
    id                  UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    nombre              VARCHAR(120)  NOT NULL,
    nivel_educativo_id  UUID          NOT NULL REFERENCES ades_niveles_educativos(id),
    materia_id          UUID          REFERENCES ades_materias(id),   -- NULL = aplica a todo el nivel
    vigente_desde       DATE          NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta       DATE,
    creado_por          UUID          REFERENCES ades_usuarios(id),
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    ref                 UUID          NOT NULL UNIQUE DEFAULT uuidv7(),
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_creacion          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_modificacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    usuario_creacion    VARCHAR(150)  NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    row_version         INT           NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_esquemas_nivel
    ON ades_esquemas_ponderacion (nivel_educativo_id, activo);
CREATE INDEX IF NOT EXISTS idx_esquemas_materia
    ON ades_esquemas_ponderacion (materia_id) WHERE materia_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 2. Ítems de cada esquema de ponderación
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_items_ponderacion (
    id                    UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    esquema_id            UUID         NOT NULL REFERENCES ades_esquemas_ponderacion(id) ON DELETE CASCADE,
    tipo_item             VARCHAR(30)  NOT NULL CHECK (tipo_item IN (
                              'examen','tarea','proyecto','asistencia',
                              'comportamiento','participacion','laboratorio','otro')),
    nombre_personalizado  VARCHAR(80),
    peso_porcentaje       NUMERIC(5,2) NOT NULL CHECK (peso_porcentaje > 0 AND peso_porcentaje <= 100),
    orden_display         INT          NOT NULL DEFAULT 1,
    ref                   UUID         NOT NULL UNIQUE DEFAULT uuidv7(),
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    row_version           INT          NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_items_esquema
    ON ades_items_ponderacion (esquema_id);

-- ─────────────────────────────────────────────────────────────
-- 3. Extensión de ades_tareas — trazabilidad curricular
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_tareas
    ADD COLUMN IF NOT EXISTS tipo_item        VARCHAR(30)  NOT NULL DEFAULT 'tarea',
    ADD COLUMN IF NOT EXISTS plan_trabajo_id  UUID         REFERENCES ades_planeacion_clases(id),
    ADD COLUMN IF NOT EXISTS rubrica_id       UUID         REFERENCES ades_rubricas(id),
    ADD COLUMN IF NOT EXISTS fecha_examen     DATE,
    ADD COLUMN IF NOT EXISTS instrucciones_url TEXT;

-- tipo_item hereda los mismos valores que ades_items_ponderacion
ALTER TABLE ades_tareas
    DROP CONSTRAINT IF EXISTS chk_tareas_tipo_item;
ALTER TABLE ades_tareas
    ADD CONSTRAINT chk_tareas_tipo_item CHECK (tipo_item IN (
        'examen','tarea','proyecto','asistencia',
        'comportamiento','participacion','laboratorio','otro'));

-- ─────────────────────────────────────────────────────────────
-- 4. Extensión de ades_tareas_entregas — campo de archivo + calificación inline
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_tareas_entregas
    ADD COLUMN IF NOT EXISTS archivo_url              TEXT,
    ADD COLUMN IF NOT EXISTS calificacion_obtenida    NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS comentario_profesor      TEXT,
    ADD COLUMN IF NOT EXISTS calificado_por           UUID  REFERENCES ades_usuarios(id),
    ADD COLUMN IF NOT EXISTS fecha_calificacion_docente TIMESTAMPTZ;

-- Ampliar estatus permitidos (SIN_ENTREGA, EXCUSA, CALIFICADA ya usados en código)
-- El varchar(20) existente soporta todos los valores; los validamos en la aplicación.

-- ─────────────────────────────────────────────────────────────
-- 5. Extensión de ades_calificaciones_periodo — desglose + auditoría de ajuste
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_calificaciones_periodo
    ADD COLUMN IF NOT EXISTS score_por_item       JSONB        DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS calificacion_calculada NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS ajuste_manual         NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS justificacion_ajuste  TEXT,
    ADD COLUMN IF NOT EXISTS fecha_calculo         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fecha_cierre          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cerrada               BOOLEAN      NOT NULL DEFAULT FALSE;

-- Índice para buscar períodos abiertos
CREATE INDEX IF NOT EXISTS idx_cal_periodo_cerrada
    ON ades_calificaciones_periodo (cerrada, periodo_evaluacion_id);

-- ─────────────────────────────────────────────────────────────
-- 6. Función calcular_calificacion_periodo()
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION calcular_calificacion_periodo(
    p_alumno_id     UUID,
    p_grupo_id      UUID,
    p_materia_id    UUID,
    p_periodo_id    UUID
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_nivel_id       UUID;
    v_escala         NUMERIC;
    v_esquema_id     UUID;
    v_score          NUMERIC := 0;
    v_scores         JSONB   := '{}';
    v_cal_final      NUMERIC;
    v_item           RECORD;
    v_avg_examenes   NUMERIC;
    v_tareas_total   INT;
    v_tareas_entregadas INT;
    v_score_item     NUMERIC;
    v_pct_asist      NUMERIC;
    v_dias_habiles   INT;
    v_dias_presentes NUMERIC;
    v_fecha_ini      DATE;
    v_fecha_fin      DATE;
BEGIN
    -- 1. Nivel educativo y escala del grupo
    SELECT gr.nivel_educativo_id, ne.escala_maxima
      INTO v_nivel_id, v_escala
      FROM ades_grupos g
      JOIN ades_grados gr ON gr.id = g.grado_id
      JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
     WHERE g.id = p_grupo_id;

    IF v_nivel_id IS NULL THEN RETURN NULL; END IF;

    -- 2. Esquema de ponderación: específico de materia > genérico del nivel
    SELECT id INTO v_esquema_id
      FROM ades_esquemas_ponderacion
     WHERE nivel_educativo_id = v_nivel_id
       AND activo = TRUE
       AND (vigente_hasta IS NULL OR vigente_hasta >= CURRENT_DATE)
       AND vigente_desde <= CURRENT_DATE
     ORDER BY (materia_id = p_materia_id) DESC NULLS LAST,
              vigente_desde DESC
     LIMIT 1;

    IF v_esquema_id IS NULL THEN RETURN NULL; END IF;

    -- Rango del período
    SELECT fecha_inicio, fecha_fin
      INTO v_fecha_ini, v_fecha_fin
      FROM ades_periodos_evaluacion
     WHERE id = p_periodo_id;

    -- 3. Calcular score por cada ítem del esquema
    FOR v_item IN
        SELECT tipo_item, COALESCE(nombre_personalizado, tipo_item) AS etiqueta,
               peso_porcentaje
          FROM ades_items_ponderacion
         WHERE esquema_id = v_esquema_id AND is_active = TRUE
         ORDER BY orden_display
    LOOP
        v_score_item := 0;

        CASE v_item.tipo_item
        WHEN 'examen' THEN
            -- Promedio de calificaciones de evaluaciones del período
            SELECT COALESCE(AVG(ce.calificacion), 0)
              INTO v_avg_examenes
              FROM ades_calificaciones_evaluaciones ce
              JOIN ades_evaluaciones ev ON ev.id = ce.evaluacion_id
             WHERE ce.estudiante_id = p_alumno_id
               AND ev.grupo_id     = p_grupo_id
               AND ev.materia_id   = p_materia_id
               AND ev.periodo_evaluacion_id = p_periodo_id;
            v_score_item := COALESCE(v_avg_examenes, 0);

        WHEN 'tarea', 'proyecto', 'laboratorio', 'participacion', 'otro' THEN
            -- (entregadas calificadas) / total esperadas × escala
            SELECT COUNT(*), COUNT(*) FILTER (
                       WHERE te.estatus_entrega IN ('CALIFICADA','ENTREGADA')
                   )
              INTO v_tareas_total, v_tareas_entregadas
              FROM ades_tareas t
              LEFT JOIN ades_tareas_entregas te
                     ON te.tarea_id = t.id AND te.estudiante_id = p_alumno_id
             WHERE t.grupo_id  = p_grupo_id
               AND t.materia_id = p_materia_id
               AND t.tipo_item  = v_item.tipo_item
               AND (v_fecha_ini IS NULL OR t.fecha_entrega >= v_fecha_ini)
               AND (v_fecha_fin IS NULL OR t.fecha_entrega <= v_fecha_fin)
               AND t.is_active = TRUE;

            IF COALESCE(v_tareas_total, 0) > 0 THEN
                v_score_item := (COALESCE(v_tareas_entregadas, 0)::NUMERIC
                                 / v_tareas_total) * v_escala;
            ELSE
                v_score_item := v_escala; -- Sin actividades del tipo = no penalizar
            END IF;

        WHEN 'asistencia' THEN
            -- (presentes + retardos×0.5) / días hábiles × escala
            SELECT COUNT(a.id),
                   SUM(CASE
                       WHEN a.estatus_asistencia = 'PRESENTE'  THEN 1.0
                       WHEN a.estatus_asistencia = 'TARDANZA'  THEN 0.5
                       ELSE 0 END)
              INTO v_dias_habiles, v_dias_presentes
              FROM ades_asistencias a
              JOIN ades_clases cl ON cl.id = a.clase_id
             WHERE a.estudiante_id = p_alumno_id
               AND cl.grupo_id     = p_grupo_id
               AND cl.materia_id   = p_materia_id
               AND (v_fecha_ini IS NULL OR cl.fecha_clase >= v_fecha_ini)
               AND (v_fecha_fin IS NULL OR cl.fecha_clase <= v_fecha_fin);

            IF COALESCE(v_dias_habiles, 0) > 0 THEN
                v_score_item := (COALESCE(v_dias_presentes, 0)
                                 / v_dias_habiles) * v_escala;
            ELSE
                v_score_item := v_escala;
            END IF;

        WHEN 'comportamiento' THEN
            -- Sin reportes de conducta = score máximo; cada reporte resta
            DECLARE v_reportes INT;
            BEGIN
                SELECT COUNT(*) INTO v_reportes
                  FROM ades_reportes_conducta rc
                  JOIN ades_estudiantes est ON est.id = rc.estudiante_id
                 WHERE rc.estudiante_id = p_alumno_id
                   AND (v_fecha_ini IS NULL OR rc.fecha_reporte >= v_fecha_ini)
                   AND (v_fecha_fin IS NULL OR rc.fecha_reporte <= v_fecha_fin);
                v_score_item := GREATEST(0, v_escala - (v_reportes * (v_escala * 0.1)));
            END;
        END CASE;

        v_scores := v_scores || jsonb_build_object(v_item.tipo_item, ROUND(v_score_item, 2));
        v_score  := v_score + (v_score_item * v_item.peso_porcentaje / 100.0);
    END LOOP;

    -- 4. Redondear según escala
    IF v_escala >= 100 THEN
        v_cal_final := ROUND(v_score);          -- UAEMEX: entero
    ELSE
        v_cal_final := ROUND(v_score, 1);       -- SEP: 1 decimal
    END IF;

    -- 5. Upsert en ades_calificaciones_periodo
    INSERT INTO ades_calificaciones_periodo
           (estudiante_id, grupo_id, materia_id, periodo_evaluacion_id,
            score_por_item, calificacion_calculada, calificacion_final,
            fecha_calculo, is_active)
    VALUES (p_alumno_id, p_grupo_id, p_materia_id, p_periodo_id,
            v_scores, v_cal_final, v_cal_final,
            now(), TRUE)
    ON CONFLICT (estudiante_id, materia_id, periodo_evaluacion_id)
    DO UPDATE SET
        score_por_item       = EXCLUDED.score_por_item,
        calificacion_calculada = EXCLUDED.calificacion_calculada,
        calificacion_final   = CASE
            WHEN ades_calificaciones_periodo.cerrada = TRUE
            THEN ades_calificaciones_periodo.calificacion_final   -- no tocar si cerrada
            WHEN ades_calificaciones_periodo.ajuste_manual IS NOT NULL
            THEN ades_calificaciones_periodo.ajuste_manual         -- respetar ajuste manual
            ELSE EXCLUDED.calificacion_calculada
        END,
        fecha_calculo        = now(),
        fecha_modificacion       = now(),
        row_version          = ades_calificaciones_periodo.row_version + 1;

    RETURN v_cal_final;
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- 7. Función trigger para ades_tareas_entregas
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_recalcular_desde_entrega()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_grupo_id   UUID;
    v_materia_id UUID;
    v_periodo_id UUID;
BEGIN
    -- Obtener contexto de la tarea
    SELECT t.grupo_id, t.materia_id, t.periodo_evaluacion_id
      INTO v_grupo_id, v_materia_id, v_periodo_id
      FROM ades_tareas t
     WHERE t.id = NEW.tarea_id;

    IF v_grupo_id IS NOT NULL AND v_materia_id IS NOT NULL AND v_periodo_id IS NOT NULL THEN
        PERFORM calcular_calificacion_periodo(
            NEW.estudiante_id, v_grupo_id, v_materia_id, v_periodo_id
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_gradebook_entrega ON ades_tareas_entregas;
CREATE TRIGGER trg_gradebook_entrega
    AFTER INSERT OR UPDATE OF estatus_entrega, calificacion_obtenida
    ON ades_tareas_entregas
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_desde_entrega();

-- ─────────────────────────────────────────────────────────────
-- 8. Función trigger para ades_calificaciones_evaluaciones
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_recalcular_desde_examen()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_grupo_id   UUID;
    v_materia_id UUID;
    v_periodo_id UUID;
BEGIN
    SELECT ev.grupo_id, ev.materia_id, ev.periodo_evaluacion_id
      INTO v_grupo_id, v_materia_id, v_periodo_id
      FROM ades_evaluaciones ev
     WHERE ev.id = NEW.evaluacion_id;

    IF v_grupo_id IS NOT NULL THEN
        PERFORM calcular_calificacion_periodo(
            NEW.estudiante_id, v_grupo_id, v_materia_id, v_periodo_id
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_gradebook_examen ON ades_calificaciones_evaluaciones;
CREATE TRIGGER trg_gradebook_examen
    AFTER INSERT OR UPDATE OF calificacion
    ON ades_calificaciones_evaluaciones
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_desde_examen();

-- ─────────────────────────────────────────────────────────────
-- 9. Función trigger para ades_asistencias
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_recalcular_desde_asistencia()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_grupo_id   UUID;
    v_materia_id UUID;
    v_periodo_id UUID;
BEGIN
    -- Encontrar el período activo para esta clase
    SELECT cl.grupo_id, cl.materia_id,
           (SELECT pe.id
              FROM ades_periodos_evaluacion pe
             WHERE pe.ciclo_escolar_id = cl.ciclo_escolar_id
               AND cl.fecha_clase BETWEEN COALESCE(pe.fecha_inicio, '1900-01-01')
                                      AND COALESCE(pe.fecha_fin,    '2099-12-31')
             LIMIT 1)
      INTO v_grupo_id, v_materia_id, v_periodo_id
      FROM ades_clases cl
     WHERE cl.id = NEW.clase_id;

    IF v_grupo_id IS NOT NULL AND v_periodo_id IS NOT NULL THEN
        PERFORM calcular_calificacion_periodo(
            NEW.estudiante_id, v_grupo_id, v_materia_id, v_periodo_id
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_gradebook_asistencia ON ades_asistencias;
CREATE TRIGGER trg_gradebook_asistencia
    AFTER INSERT OR UPDATE OF estatus_asistencia
    ON ades_asistencias
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_desde_asistencia();

-- ─────────────────────────────────────────────────────────────
-- 10. Seeds — esquemas de ponderación base
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_nivel_primaria    UUID;
    v_nivel_secundaria  UUID;
    v_nivel_prepa       UUID;
    v_esquema_id        UUID;
BEGIN
    SELECT id INTO v_nivel_primaria   FROM ades_niveles_educativos WHERE nombre_nivel = 'PRIMARIA'    LIMIT 1;
    SELECT id INTO v_nivel_secundaria FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA'  LIMIT 1;
    SELECT id INTO v_nivel_prepa      FROM ades_niveles_educativos WHERE nombre_nivel = 'PREPARATORIA' LIMIT 1;

    -- ── Primaria SEP: Examen 70 | Tareas 20 | Asistencia 10 ─────────────
    IF v_nivel_primaria IS NOT NULL THEN
        INSERT INTO ades_esquemas_ponderacion
               (nombre, nivel_educativo_id, vigente_desde, activo)
        VALUES ('SEP Primaria — Base', v_nivel_primaria, '2025-08-01', TRUE)
        ON CONFLICT DO NOTHING
        RETURNING id INTO v_esquema_id;

        IF v_esquema_id IS NOT NULL THEN
            INSERT INTO ades_items_ponderacion
                   (esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display)
            VALUES (v_esquema_id, 'examen',     'Examen',      70, 1),
                   (v_esquema_id, 'tarea',      'Tareas',      20, 2),
                   (v_esquema_id, 'asistencia', 'Asistencia',  10, 3);
        END IF;
    END IF;

    -- ── Secundaria SEP: Examen 60 | Tareas 25 | Asistencia 10 | Participación 5 ─
    IF v_nivel_secundaria IS NOT NULL THEN
        INSERT INTO ades_esquemas_ponderacion
               (nombre, nivel_educativo_id, vigente_desde, activo)
        VALUES ('SEP Secundaria — Base', v_nivel_secundaria, '2025-08-01', TRUE)
        ON CONFLICT DO NOTHING
        RETURNING id INTO v_esquema_id;

        IF v_esquema_id IS NOT NULL THEN
            INSERT INTO ades_items_ponderacion
                   (esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display)
            VALUES (v_esquema_id, 'examen',        'Examen',        60, 1),
                   (v_esquema_id, 'tarea',         'Tareas',        25, 2),
                   (v_esquema_id, 'asistencia',    'Asistencia',    10, 3),
                   (v_esquema_id, 'participacion', 'Participación',  5, 4);
        END IF;
    END IF;

    -- ── Preparatoria UAEMEX: Examen 70 | Proyectos 20 | Asistencia 10 ─
    IF v_nivel_prepa IS NOT NULL THEN
        INSERT INTO ades_esquemas_ponderacion
               (nombre, nivel_educativo_id, vigente_desde, activo)
        VALUES ('UAEMEX Preparatoria — Base', v_nivel_prepa, '2025-08-01', TRUE)
        ON CONFLICT DO NOTHING
        RETURNING id INTO v_esquema_id;

        IF v_esquema_id IS NOT NULL THEN
            INSERT INTO ades_items_ponderacion
                   (esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display)
            VALUES (v_esquema_id, 'examen',     'Examen',     70, 1),
                   (v_esquema_id, 'proyecto',   'Proyectos',  20, 2),
                   (v_esquema_id, 'asistencia', 'Asistencia', 10, 3);
        END IF;
    END IF;
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- 11. Triggers de auditoría en tablas nuevas
-- ─────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
         WHERE tgname = 'trg_aud_biu'
           AND tgrelid = 'ades_esquemas_ponderacion'::regclass
    ) THEN
        CREATE TRIGGER trg_aud_biu
        BEFORE INSERT OR UPDATE ON ades_esquemas_ponderacion
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
         WHERE tgname = 'trg_aud_biu'
           AND tgrelid = 'ades_items_ponderacion'::regclass
    ) THEN
        CREATE TRIGGER trg_aud_biu
        BEFORE INSERT OR UPDATE ON ades_items_ponderacion
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
    END IF;
END;
$$;

COMMIT;

-- ─────────────────────────────────────────────────────────────
-- Verificación
-- ─────────────────────────────────────────────────────────────
SELECT 'ades_esquemas_ponderacion' AS tabla, COUNT(*) AS filas
  FROM ades_esquemas_ponderacion
UNION ALL
SELECT 'ades_items_ponderacion', COUNT(*) FROM ades_items_ponderacion
UNION ALL
SELECT 'escala_configurada', COUNT(*)
  FROM ades_niveles_educativos
 WHERE escala_maxima IS NOT NULL;

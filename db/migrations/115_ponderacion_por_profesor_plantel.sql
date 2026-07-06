-- =============================================================================
-- Migración: 115_ponderacion_por_profesor_plantel.sql
-- Descripción: Permite que cada profesor defina su propio esquema de ponderación
--              de calificaciones (tareas/asistencia/conducta/examen), y que Nevadi
--              defina un ajuste estándar por plantel, además del esquema
--              institucional genérico existente por nivel+materia.
--              Jerarquía de resolución en calcular_calificacion_periodo():
--                esquema del profesor  >  esquema del plantel  >  esquema institucional
--              Requerimiento de negocio confirmado 2026-07-06 (ver
--              DECISIONS/ para contexto de la auditoría de fórmula de calificación).
-- Tablas afectadas: ades_esquemas_ponderacion, función calcular_calificacion_periodo()
-- Dependencias: ades_profesores, ades_planteles, ades_asignaciones_docentes, ades_grados
-- Autor: ADES
-- Fecha: 2026-07-06
-- =============================================================================

BEGIN;

ALTER TABLE ades_esquemas_ponderacion
    ADD COLUMN IF NOT EXISTS profesor_id UUID REFERENCES ades_profesores(id),
    ADD COLUMN IF NOT EXISTS plantel_id  UUID REFERENCES ades_planteles(id);

COMMENT ON COLUMN ades_esquemas_ponderacion.profesor_id IS
    'NULL = esquema institucional/plantel. Si tiene valor, es el esquema propio de ese profesor (solo él puede editarlo).';
COMMENT ON COLUMN ades_esquemas_ponderacion.plantel_id IS
    'NULL = esquema institucional (todos los planteles del nivel). Si tiene valor, es el ajuste estándar de ese plantel.';

CREATE INDEX IF NOT EXISTS idx_esquemas_profesor
    ON ades_esquemas_ponderacion (profesor_id) WHERE profesor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esquemas_plantel
    ON ades_esquemas_ponderacion (plantel_id) WHERE plantel_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- calcular_calificacion_periodo(): agrega resolución de esquema
-- por profesor (vía ades_asignaciones_docentes) y por plantel
-- (vía ades_grados), con precedencia profesor > plantel > institucional.
-- Cuerpo base: 093_classroom_gaps.sql (soporte NEE) + este ajuste.
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.calcular_calificacion_periodo(p_alumno_id uuid, p_grupo_id uuid, p_materia_id uuid, p_periodo_id uuid)
 RETURNS numeric
 LANGUAGE plpgsql
 SECURITY DEFINER
 AS $function$
 DECLARE
     v_nivel_id       UUID;
     v_escala         NUMERIC;
     v_esquema_id     UUID;
     v_plantel_id     UUID;
     v_profesor_id    UUID;
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
     v_is_nee         BOOLEAN := FALSE;
 BEGIN
     -- Check if student has active NEE status
     SELECT EXISTS (
         SELECT 1 FROM ades_nee WHERE alumno_id = p_alumno_id AND activa = TRUE
     ) INTO v_is_nee;

     SELECT gr.nivel_educativo_id, ne.escala_maxima, gr.plantel_id
       INTO v_nivel_id, v_escala, v_plantel_id
       FROM ades_grupos g
       JOIN ades_grados gr ON gr.id = g.grado_id
       JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
      WHERE g.id = p_grupo_id;

     IF v_nivel_id IS NULL THEN RETURN NULL; END IF;

     -- Profesor asignado a esta materia+grupo (para resolver su esquema propio, si existe)
     SELECT ad.profesor_id INTO v_profesor_id
       FROM ades_asignaciones_docentes ad
      WHERE ad.grupo_id = p_grupo_id
        AND ad.materia_id = p_materia_id
        AND ad.is_active = TRUE
      ORDER BY ad.fecha_creacion DESC
      LIMIT 1;

     -- Esquema de ponderación: profesor propio > plantel > institucional genérico.
     -- Dentro de cada nivel de precedencia, prioriza coincidencia NEE y materia específica.
     SELECT id INTO v_esquema_id
       FROM ades_esquemas_ponderacion
      WHERE nivel_educativo_id = v_nivel_id
        AND activo = TRUE
        AND (vigente_hasta IS NULL OR vigente_hasta >= CURRENT_DATE)
        AND vigente_desde <= CURRENT_DATE
        AND (profesor_id IS NULL OR profesor_id = v_profesor_id)
        AND (plantel_id IS NULL OR plantel_id = v_plantel_id)
      ORDER BY (profesor_id IS NOT NULL AND profesor_id = v_profesor_id) DESC,
               (profesor_id IS NULL AND plantel_id IS NOT NULL AND plantel_id = v_plantel_id) DESC,
               (es_nee = v_is_nee) DESC,
               (materia_id = p_materia_id) DESC NULLS LAST,
               vigente_desde DESC
      LIMIT 1;

     IF v_esquema_id IS NULL THEN RETURN NULL; END IF;

     SELECT fecha_inicio, fecha_fin
       INTO v_fecha_ini, v_fecha_fin
       FROM ades_periodos_evaluacion
      WHERE id = p_periodo_id;

     FOR v_item IN
         SELECT tipo_item, COALESCE(nombre_personalizado, tipo_item) AS etiqueta, peso_porcentaje
           FROM ades_items_ponderacion
          WHERE esquema_id = v_esquema_id AND is_active = TRUE
          ORDER BY orden_display
     LOOP
         v_score_item := 0;

         CASE v_item.tipo_item
         WHEN 'examen' THEN
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
             SELECT COUNT(*), COUNT(*) FILTER (WHERE te.estatus_entrega IN ('CALIFICADA','ENTREGADA'))
               INTO v_tareas_total, v_tareas_entregadas
               FROM ades_tareas t
               LEFT JOIN ades_tareas_entregas te ON te.tarea_id = t.id AND te.estudiante_id = p_alumno_id
              WHERE t.grupo_id   = p_grupo_id
                AND t.materia_id = p_materia_id
                AND t.tipo_item  = v_item.tipo_item
                AND (v_fecha_ini IS NULL OR t.fecha_entrega >= v_fecha_ini)
                AND (v_fecha_fin IS NULL OR t.fecha_entrega <= v_fecha_fin)
                AND t.is_active = TRUE;

             IF COALESCE(v_tareas_total, 0) > 0 THEN
                 v_score_item := (COALESCE(v_tareas_entregadas, 0)::NUMERIC / v_tareas_total) * v_escala;
             ELSE
                 v_score_item := v_escala;
             END IF;

         WHEN 'asistencia' THEN
             SELECT COUNT(a.id),
                    SUM(CASE
                        WHEN a.estatus_asistencia = 'PRESENTE' THEN 1.0
                        WHEN a.estatus_asistencia = 'TARDE'    THEN 0.5
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
                 v_score_item := (COALESCE(v_dias_presentes, 0) / v_dias_habiles) * v_escala;
             ELSE
                 v_score_item := v_escala;
             END IF;

         WHEN 'comportamiento' THEN
             DECLARE v_reportes INT;
             BEGIN
                 SELECT COUNT(*) INTO v_reportes
                   FROM ades_reportes_conducta rc
                  WHERE rc.estudiante_id = p_alumno_id
                    AND (v_fecha_ini IS NULL OR rc.fecha_reporte >= v_fecha_ini)
                    AND (v_fecha_fin IS NULL OR rc.fecha_reporte <= v_fecha_fin);
                 v_score_item := GREATEST(0, v_escala - (v_reportes * (v_escala * 0.1)));
             END;
         END CASE;

         v_scores := v_scores || jsonb_build_object(v_item.tipo_item, ROUND(v_score_item, 2));
         v_score  := v_score + (v_score_item * v_item.peso_porcentaje / 100.0);
     END LOOP;

     IF v_escala >= 100 THEN
         v_cal_final := ROUND(v_score);
     ELSE
         v_cal_final := ROUND(v_score, 1);
     END IF;

     UPDATE ades_calificaciones_periodo
        SET score_por_item         = v_scores,
            calificacion_calculada = v_cal_final,
            calificacion_final     = CASE
                WHEN cerrada = TRUE            THEN calificacion_final
                WHEN ajuste_manual IS NOT NULL THEN ajuste_manual
                ELSE v_cal_final
            END,
            fecha_calculo          = now(),
            fecha_modificacion     = now(),
            row_version            = row_version + 1
      WHERE estudiante_id         = p_alumno_id
        AND materia_id            = p_materia_id
        AND periodo_evaluacion_id = p_periodo_id;

     IF NOT FOUND THEN
         INSERT INTO ades_calificaciones_periodo
                (estudiante_id, grupo_id, materia_id, periodo_evaluacion_id,
                 score_por_item, calificacion_calculada, calificacion_final,
                 fecha_calculo, is_active)
         VALUES (p_alumno_id, p_grupo_id, p_materia_id, p_periodo_id,
                 v_scores, v_cal_final, v_cal_final, now(), TRUE);
     END IF;

     RETURN v_cal_final;
 END;
 $function$;

COMMIT;

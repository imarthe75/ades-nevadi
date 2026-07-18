-- =============================================================================
-- Migración 155 — Corrige bug real de mig. 153
-- (cerrar_ciclo_sep_conjunto_y_promover): al promover un alumno de ciclo, la
-- función insertaba la nueva inscripción (ciclo destino, is_active=TRUE) pero
-- nunca desactivaba la inscripción del ciclo origen — cada alumno promovido
-- quedaba con 2 inscripciones activas simultáneas (una en el ciclo viejo, ya
-- no vigente, y otra en el nuevo). Encontrado 2026-07-18 durante un muestreo
-- manual de UI (R-21): la lista de Alumnos mostraba cada alumno promovido
-- duplicado, con grado distinto en cada fila. Confirmado en BD: exactamente
-- 1,612 alumnos afectados — el mismo número reportado como "promovidos" por
-- la ejecución real de la reinscripción masiva del 2026-07-17.
--
-- Dos partes:
--   1) CREATE OR REPLACE de la función — agrega el UPDATE que faltaba, para
--      que la próxima promoción (ciclo 2027-2028) no repita el bug.
--   2) Reparación de datos: desactiva las inscripciones "huérfanas" que
--      quedaron activas en un ciclo ya no vigente, SOLO para alumnos que
--      tienen otra inscripción activa en el ciclo vigente correspondiente
--      (evita tocar cualquier caso legítimo no relacionado con este bug).
-- =============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION cerrar_ciclo_sep_conjunto_y_promover(
    p_ciclo_origen_primaria     UUID,
    p_ciclo_destino_primaria    UUID,
    p_ciclo_origen_secundaria   UUID,
    p_ciclo_destino_secundaria  UUID,
    p_usuario                   VARCHAR(150) DEFAULT current_user
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
    v_ciclo_destino_actual UUID;
    v_grado_siguiente   UUID;
    v_grupo_destino     UUID;
    v_grado_max         INTEGER;
    v_estatus_activo    UUID;
    v_estatus_baja      UUID;
    v_estatus_egresado  UUID;
    v_estatus_reprobado UUID;
    v_nueva_inscripcion_id UUID;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ades_ciclos_escolares WHERE id = p_ciclo_origen_primaria   AND es_vigente AND is_active) THEN
        RAISE EXCEPTION 'El ciclo origen (Primaria) % no está vigente o no existe.', p_ciclo_origen_primaria;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM ades_ciclos_escolares WHERE id = p_ciclo_origen_secundaria AND es_vigente AND is_active) THEN
        RAISE EXCEPTION 'El ciclo origen (Secundaria) % no está vigente o no existe.', p_ciclo_origen_secundaria;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM ades_ciclos_escolares WHERE id = p_ciclo_destino_primaria   AND is_active) THEN
        RAISE EXCEPTION 'El ciclo destino (Primaria) % no existe.', p_ciclo_destino_primaria;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM ades_ciclos_escolares WHERE id = p_ciclo_destino_secundaria AND is_active) THEN
        RAISE EXCEPTION 'El ciclo destino (Secundaria) % no existe.', p_ciclo_destino_secundaria;
    END IF;

    SELECT id INTO v_estatus_activo    FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'ACTIVO'    LIMIT 1;
    SELECT id INTO v_estatus_baja      FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'BAJA'      LIMIT 1;
    SELECT id INTO v_estatus_egresado  FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'EGRESADO'  LIMIT 1;
    SELECT id INTO v_estatus_reprobado FROM ades_estatus WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'REPROBADO' LIMIT 1;

    FOR v_inscripcion IN
        SELECT
            i.id             AS inscripcion_id,
            i.estudiante_id,
            i.grupo_id,
            i.estatus_id,
            i.ciclo_escolar_id AS ciclo_origen_id,
            g.grado_id,
            g.nombre_grupo,
            gr.plantel_id,
            gr.numero_grado,
            gr.nivel_educativo_id
        FROM ades_inscripciones      i
        JOIN ades_grupos             g   ON g.id  = i.grupo_id
        JOIN ades_grados             gr  ON gr.id = g.grado_id
        WHERE i.ciclo_escolar_id = ANY (ARRAY[p_ciclo_origen_primaria, p_ciclo_origen_secundaria])
          AND i.is_active = TRUE
    LOOP
        v_ciclo_destino_actual := CASE
            WHEN v_inscripcion.ciclo_origen_id = p_ciclo_origen_primaria THEN p_ciclo_destino_primaria
            ELSE p_ciclo_destino_secundaria
        END;

        IF v_inscripcion.estatus_id = v_estatus_baja THEN
            v_bajas := v_bajas + 1;
            CONTINUE;
        END IF;

        SELECT MAX(numero_grado) INTO v_grado_max
        FROM ades_grados
        WHERE nivel_educativo_id = v_inscripcion.nivel_educativo_id
          AND plantel_id = v_inscripcion.plantel_id
          AND is_active = TRUE;

        IF v_inscripcion.numero_grado >= v_grado_max AND v_inscripcion.estatus_id != v_estatus_reprobado THEN
            UPDATE ades_inscripciones
            SET estatus_id = v_estatus_egresado, is_active = FALSE, usuario_modificacion = p_usuario
            WHERE id = v_inscripcion.inscripcion_id;
            v_egresados := v_egresados + 1;
            CONTINUE;
        END IF;

        IF v_inscripcion.estatus_id = v_estatus_reprobado THEN
            v_grado_siguiente := v_inscripcion.grado_id;
            v_reprobados := v_reprobados + 1;
        ELSE
            SELECT id INTO v_grado_siguiente
            FROM ades_grados
            WHERE nivel_educativo_id = v_inscripcion.nivel_educativo_id
              AND plantel_id         = v_inscripcion.plantel_id
              AND numero_grado       = v_inscripcion.numero_grado + 1
              AND is_active = TRUE
            LIMIT 1;

            IF v_grado_siguiente IS NULL THEN
                UPDATE ades_inscripciones
                SET estatus_id = v_estatus_egresado, is_active = FALSE, usuario_modificacion = p_usuario
                WHERE id = v_inscripcion.inscripcion_id;
                v_egresados := v_egresados + 1;
                CONTINUE;
            END IF;
            v_promovidos := v_promovidos + 1;
        END IF;

        SELECT id INTO v_grupo_destino
        FROM ades_grupos
        WHERE grado_id         = v_grado_siguiente
          AND nombre_grupo     = v_inscripcion.nombre_grupo
          AND ciclo_escolar_id = v_ciclo_destino_actual
          AND is_active = TRUE
        LIMIT 1;

        IF v_grupo_destino IS NULL THEN
            INSERT INTO ades_promociones_pendientes
                (estudiante_id, ciclo_origen_id, ciclo_destino_id, grupo_origen_id, usuario_creacion)
            VALUES
                (v_inscripcion.estudiante_id, v_inscripcion.ciclo_origen_id, v_ciclo_destino_actual,
                 v_inscripcion.grupo_id, p_usuario);
            v_pendientes := v_pendientes + 1;
            CONTINUE;
        END IF;

        INSERT INTO ades_inscripciones
            (estudiante_id, grupo_id, ciclo_escolar_id, estatus_id, usuario_creacion)
        VALUES
            (v_inscripcion.estudiante_id, v_grupo_destino, v_ciclo_destino_actual,
             v_estatus_activo, p_usuario)
        ON CONFLICT (estudiante_id, grupo_id, ciclo_escolar_id) DO NOTHING
        RETURNING id INTO v_nueva_inscripcion_id;

        -- Bug real corregido 2026-07-18 (mig. 155): faltaba desactivar la
        -- inscripción de origen — sin esto, el alumno queda con 2
        -- inscripciones activas simultáneas (ciclo viejo + ciclo nuevo).
        -- Solo se desactiva si la nueva inscripción realmente se insertó (el
        -- ON CONFLICT DO NOTHING de arriba pudo no insertar nada si ya
        -- existía) — evita desactivar el origen sin tener un destino activo.
        IF v_nueva_inscripcion_id IS NOT NULL THEN
            UPDATE ades_inscripciones
            SET is_active = FALSE, usuario_modificacion = p_usuario
            WHERE id = v_inscripcion.inscripcion_id;
        END IF;
    END LOOP;

    UPDATE ades_ciclos_escolares
    SET es_vigente = FALSE, usuario_modificacion = p_usuario
    WHERE id = ANY (ARRAY[p_ciclo_origen_primaria, p_ciclo_origen_secundaria]);

    UPDATE ades_ciclos_escolares
    SET es_vigente = TRUE, usuario_modificacion = p_usuario
    WHERE id = ANY (ARRAY[p_ciclo_destino_primaria, p_ciclo_destino_secundaria]);

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

COMMENT ON FUNCTION cerrar_ciclo_sep_conjunto_y_promover IS
'Variante de cerrar_ciclo_y_promover() (mig. 009/152) para el sistema SEP
(Primaria+Secundaria), que exige un único año vigente compartido entre ambos
niveles (trg_ciclo_sistema_vigente, mig. 083). Procesa ambos pares de ciclo en
una sola transacción y solo hace los UPDATE de es_vigente al final, cada uno
multi-fila, para nunca dejar un estado intermedio con nombre_ciclo distinto
entre Primaria y Secundaria. Fix 2026-07-18 (mig. 155): desactiva la
inscripción de origen al promover — antes dejaba 2 inscripciones activas
simultáneas por alumno promovido.';

-- ── Reparación de datos: los 1,612 alumnos ya afectados por el bug ─────────
-- Desactiva la inscripción "huérfana" (ciclo ya no vigente) SOLO para
-- alumnos que tienen otra inscripción activa en un ciclo SÍ vigente —
-- criterio verificado antes de aplicar: exactamente 1,612 alumnos con 2
-- inscripciones activas, 1,612 en ciclo no-vigente + 1,612 en ciclo vigente
-- (pareo 1:1 exacto, confirmado por consulta directa antes de esta migración).
WITH duplicadas AS (
    SELECT estudiante_id
    FROM ades_inscripciones
    WHERE is_active = TRUE
    GROUP BY estudiante_id
    HAVING COUNT(*) > 1
)
UPDATE ades_inscripciones i
SET is_active = FALSE,
    usuario_modificacion = 'mig_155_fix_duplicadas'
FROM ades_ciclos_escolares c
WHERE i.ciclo_escolar_id = c.id
  AND i.is_active = TRUE
  AND c.es_vigente = FALSE
  AND i.estudiante_id IN (SELECT estudiante_id FROM duplicadas)
  AND EXISTS (
      -- Verificación de seguridad: solo tocar si el alumno SÍ tiene otra
      -- inscripción activa en un ciclo vigente (nunca dejar a un alumno con
      -- CERO inscripciones activas por este UPDATE).
      SELECT 1 FROM ades_inscripciones i2
      JOIN ades_ciclos_escolares c2 ON c2.id = i2.ciclo_escolar_id
      WHERE i2.estudiante_id = i.estudiante_id
        AND i2.is_active = TRUE
        AND i2.id != i.id
        AND c2.es_vigente = TRUE
  );

COMMIT;

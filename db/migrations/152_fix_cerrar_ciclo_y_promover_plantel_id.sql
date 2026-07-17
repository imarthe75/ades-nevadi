-- =============================================================================
-- Migración 152 — Fix real: cerrar_ciclo_y_promover() referenciaba una columna
-- inexistente (g.plantel_id) en vez de gr.plantel_id (ades_grados, no
-- ades_grupos). Bug preexistente de la migración 009 (2026-06-04), nunca
-- ejercitado con éxito hasta hoy (2026-07-17, primera ejecución real de la
-- reinscripción masiva de ciclo — la sesión 07-13 había decidido explícitamente
-- NO correrla por falta de un ciclo destino, así que este código nunca se
-- había probado en producción). Error real capturado en logs de ades-bff:
--   "ERROR: column g.plantel_id does not exist
--    Hint: Perhaps you meant to reference the column 'gr.plantel_id'."
-- Spring @Transactional revirtió limpiamente la llamada fallida — sin datos
-- corruptos que reparar, solo se corrige la función antes de reintentar.
-- =============================================================================

BEGIN;

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
            gr.plantel_id,
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
en ades_promociones_pendientes para asignación manual. Retorna JSON con estadísticas.
Fix 2026-07-17 (mig. 152): plantel_id se lee de gr (ades_grados), no de g (ades_grupos)
— esa columna no existe en ades_grupos.';

COMMIT;

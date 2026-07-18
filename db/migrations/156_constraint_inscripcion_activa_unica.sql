-- =============================================================================
-- Migración 156 — Auditoría solicitada tras el hallazgo de mig. 155 (1,612
-- alumnos con 2 inscripciones activas simultáneas). Dos partes:
--
-- 1) Mismo bug encontrado en el SEGUNDO lugar donde existía: la función
--    original `cerrar_ciclo_y_promover()` (mig. 009, fix de columna en mig.
--    152) — usada para UAEMEX Preparatoria (ciclos por semestre) — tiene
--    EXACTAMENTE el mismo defecto que `cerrar_ciclo_sep_conjunto_y_promover()`
--    (mig. 153, corregido en mig. 155): inserta la inscripción del ciclo
--    destino pero nunca desactiva la de origen. No se ha ejecutado todavía
--    para el ciclo actual (verificado: 0 filas duplicadas de UAEMEX antes de
--    esta migración — a diferencia de SEP, aquí no hace falta reparar datos,
--    solo corregir la función antes de que alguien la use).
--
--    Auditoría del resto del código (2026-07-18): se revisó todo el código
--    Java que toca `ades_inscripciones` (movilidad — cambio de grupo, bajas)
--    y está escrito correctamente (UPDATE en el mismo registro o
--    desactivación explícita antes de cualquier operación) — el defecto
--    estaba aislado a estas 2 funciones SQL de promoción masiva. Se
--    revisaron también todas las demás funciones PL/pgSQL del repo que
--    hacen INSERT masivo (`grep INSERT INTO ades_` en db/migrations/*.sql)
--    y ninguna otra tiene este patrón de "insertar activo nuevo sin
--    desactivar el viejo" sobre una entidad con invariante de unicidad.
--
-- 2) Restricción real a nivel de base de datos — la razón de fondo por la
--    que este bug pudo corromper datos reales sin que nada lo impidiera: no
--    existía ninguna restricción que hiciera IMPOSIBLE tener 2 inscripciones
--    activas para el mismo alumno. Los índices existentes
--    (`idx_inscripciones_activas`, `idx_ades_inscripciones_estudiante_grupo_activa`)
--    son solo de rendimiento (no UNIQUE) — no bloqueaban nada. Se agrega un
--    índice único parcial: de aquí en adelante, CUALQUIER bug futuro similar
--    (en esta función, en una nueva, o en un INSERT manual) fallará de
--    inmediato con una violación de constraint en vez de corromper datos en
--    silencio. Defensa en profundidad real, no solo el fix puntual de la
--    función.
-- =============================================================================

BEGIN;

-- ── Parte 1: mismo fix que mig. 155, aplicado a la función original ────────

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
    v_grado_max         INTEGER;
    v_estatus_activo    UUID;
    v_estatus_baja      UUID;
    v_estatus_egresado  UUID;
    v_estatus_reprobado UUID;
    v_nueva_inscripcion_id UUID;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM ades_ciclos_escolares
        WHERE id = p_ciclo_origen_id AND es_vigente = TRUE AND is_active = TRUE
    ) THEN
        RAISE EXCEPTION 'El ciclo origen % no está vigente o no existe.', p_ciclo_origen_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM ades_ciclos_escolares
        WHERE id = p_ciclo_destino_id AND is_active = TRUE
    ) THEN
        RAISE EXCEPTION 'El ciclo destino % no existe.', p_ciclo_destino_id;
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
            g.grado_id,
            g.nombre_grupo,
            gr.plantel_id,
            gr.numero_grado,
            gr.nivel_educativo_id
        FROM ades_inscripciones      i
        JOIN ades_grupos             g   ON g.id  = i.grupo_id
        JOIN ades_grados             gr  ON gr.id = g.grado_id
        WHERE i.ciclo_escolar_id = p_ciclo_origen_id
          AND i.is_active = TRUE
    LOOP
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
        WHERE grado_id        = v_grado_siguiente
          AND nombre_grupo    = v_inscripcion.nombre_grupo
          AND ciclo_escolar_id = p_ciclo_destino_id
          AND is_active = TRUE
        LIMIT 1;

        IF v_grupo_destino IS NULL THEN
            INSERT INTO ades_promociones_pendientes
                (estudiante_id, ciclo_origen_id, ciclo_destino_id, grupo_origen_id, usuario_creacion)
            VALUES
                (v_inscripcion.estudiante_id, p_ciclo_origen_id, p_ciclo_destino_id,
                 v_inscripcion.grupo_id, p_usuario);
            v_pendientes := v_pendientes + 1;
            CONTINUE;
        END IF;

        INSERT INTO ades_inscripciones
            (estudiante_id, grupo_id, ciclo_escolar_id, estatus_id, usuario_creacion)
        VALUES
            (v_inscripcion.estudiante_id, v_grupo_destino, p_ciclo_destino_id,
             v_estatus_activo, p_usuario)
        ON CONFLICT (estudiante_id, grupo_id, ciclo_escolar_id) DO NOTHING
        RETURNING id INTO v_nueva_inscripcion_id;

        -- Fix 2026-07-18 (mig. 156, mismo bug que mig. 155): faltaba
        -- desactivar la inscripción de origen tras crear la de destino.
        IF v_nueva_inscripcion_id IS NOT NULL THEN
            UPDATE ades_inscripciones
            SET is_active = FALSE, usuario_modificacion = p_usuario
            WHERE id = v_inscripcion.inscripcion_id;
        END IF;
    END LOOP;

    UPDATE ades_ciclos_escolares
    SET es_vigente = FALSE, usuario_modificacion = p_usuario
    WHERE id = p_ciclo_origen_id;

    UPDATE ades_ciclos_escolares
    SET es_vigente = TRUE, usuario_modificacion = p_usuario
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
crea inscripciones para todos los alumnos activos, desactivando la inscripción de
origen (fix 2026-07-18, mig. 156 — mismo bug que mig. 155/153: antes dejaba 2
inscripciones activas simultáneas por alumno promovido). Alumnos sin grupo destino
quedan en ades_promociones_pendientes para asignación manual.';

-- ── Parte 2: restricción real — nunca más 2 inscripciones activas por alumno ──
-- Verificado antes de crear el índice: 0 alumnos con >1 inscripción activa
-- (mig. 155 ya reparó los 1,612 casos reales de SEP; UAEMEX Prepa nunca tuvo
-- el bug ejercitado). Si este CREATE UNIQUE INDEX fallara, sería una señal de
-- que queda algún caso sin reparar — deliberadamente NO se usa CONCURRENTLY
-- para que la migración falle de forma ruidosa y visible si ese fuera el caso,
-- en vez de completarse a medias.
CREATE UNIQUE INDEX uq_ades_inscripciones_activa_por_estudiante
    ON ades_inscripciones (estudiante_id)
    WHERE is_active = TRUE;

COMMENT ON INDEX uq_ades_inscripciones_activa_por_estudiante IS
'Garantiza a nivel de BD que un alumno nunca tenga más de una inscripción activa
simultánea — invariante violado en producción 2026-07-17/18 por un bug real en
cerrar_ciclo_sep_conjunto_y_promover()/cerrar_ciclo_y_promover() (ver mig. 155/156).
Cualquier INSERT/UPDATE futuro que intente crear una segunda inscripción activa
para el mismo estudiante fallará con una violación de constraint en vez de
corromper datos en silencio.';

COMMIT;

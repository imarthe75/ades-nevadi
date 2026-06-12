-- =============================================================================
-- Migración 032: Reinscripción Masiva (PE-015)
-- =============================================================================
-- Agrega: ades_reinscripcion_ciclo (máquina de estados por alumno),
--         función pg_validar_reinscripcion_masiva() que detecta bloqueos.
-- La promoción efectiva usa cerrar_ciclo_y_promover() (ya en migración 009).
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Tabla ades_reinscripcion_ciclo
--    Una fila por (alumno × ciclo_destino). Máquina de estados:
--    PENDIENTE → VALIDADO → APROBADO
--                         → RECHAZADO
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_reinscripcion_ciclo (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    ciclo_origen_id      UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    ciclo_destino_id     UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    estudiante_id        UUID         NOT NULL REFERENCES ades_estudiantes(id),

    estado               VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                                      CHECK (estado IN ('PENDIENTE','VALIDADO','APROBADO','RECHAZADO')),

    -- Resultados de validación
    tiene_adeudos        BOOLEAN      NOT NULL DEFAULT FALSE,
    monto_adeudado       NUMERIC(10,2) NOT NULL DEFAULT 0,
    bloqueantes          JSONB,       -- lista de razones que impiden reinscribir

    razon_rechazo        TEXT,
    aprobado_por         UUID         REFERENCES ades_usuarios(id) ON DELETE SET NULL,
    fecha_validacion     TIMESTAMPTZ,
    fecha_aprobacion     TIMESTAMPTZ,

    -- Resultado de la promoción (relleno por cerrar_ciclo_y_promover)
    promovido            BOOLEAN,
    grupo_destino_id     UUID         REFERENCES ades_grupos(id),

    -- Auditoría estándar
    row_version          INT          NOT NULL DEFAULT 1,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,

    CONSTRAINT uq_reinscr_est_dest UNIQUE (estudiante_id, ciclo_destino_id)
);

CREATE INDEX IF NOT EXISTS idx_reinscr_ciclo_dest
    ON ades_reinscripcion_ciclo (ciclo_destino_id, estado);
CREATE INDEX IF NOT EXISTS idx_reinscr_ciclo_orig
    ON ades_reinscripcion_ciclo (ciclo_origen_id);
CREATE INDEX IF NOT EXISTS idx_reinscr_estudiante
    ON ades_reinscripcion_ciclo (estudiante_id);

COMMENT ON TABLE ades_reinscripcion_ciclo IS
    'Estado de validación de reinscripción de cada alumno para el siguiente ciclo.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Función: pg_validar_reinscripcion_masiva(ciclo_origen_id, ciclo_destino_id)
--    Inserta o actualiza filas en ades_reinscripcion_ciclo para todos los
--    alumnos activos del ciclo_origen.
--    Detecta: adeudos, estatus de baja, y falta de calificación final.
--    Retorna: resumen JSONB con contadores.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION pg_validar_reinscripcion_masiva(
    p_ciclo_origen_id  UUID,
    p_ciclo_destino_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql AS $$
DECLARE
    v_total      INT := 0;
    v_validados  INT := 0;
    v_bloqueados INT := 0;
    v_rec        RECORD;
    v_adeudo     NUMERIC(10,2);
    v_bloqueantes JSONB;
    v_estado     VARCHAR(20);
BEGIN
    FOR v_rec IN
        SELECT
            i.estudiante_id,
            i.grupo_id,
            est_stat.nombre_estatus AS estatus_alumno
        FROM ades_inscripciones i
        JOIN ades_estudiantes e   ON e.id = i.estudiante_id AND e.is_active = TRUE
        JOIN ades_estatus est_stat ON est_stat.id = i.estatus_id
        WHERE i.ciclo_escolar_id = p_ciclo_origen_id
          AND i.is_active = TRUE
    LOOP
        v_total := v_total + 1;
        v_bloqueantes := '[]'::JSONB;

        -- Verificar adeudos vigentes
        SELECT COALESCE(SUM(cp.saldo_pendiente), 0)
          INTO v_adeudo
          FROM ades_cuotas_pagos cp
         WHERE cp.estudiante_id = v_rec.estudiante_id
           AND cp.ciclo_escolar_id = p_ciclo_origen_id
           AND cp.estatus IN ('PENDIENTE', 'PARCIAL')
           AND cp.saldo_pendiente > 0
           AND cp.is_active = TRUE;

        IF v_adeudo > 0 THEN
            v_bloqueantes := v_bloqueantes || jsonb_build_array(
                jsonb_build_object('tipo', 'ADEUDO', 'detalle',
                    'Adeudo pendiente: $' || v_adeudo::TEXT)
            );
        END IF;

        -- Verificar estatus de baja
        IF v_rec.estatus_alumno IN ('BAJA', 'EGRESADO') THEN
            v_bloqueantes := v_bloqueantes || jsonb_build_array(
                jsonb_build_object('tipo', 'ESTATUS', 'detalle',
                    'Alumno con estatus: ' || v_rec.estatus_alumno)
            );
        END IF;

        -- Determinar estado resultante
        IF jsonb_array_length(v_bloqueantes) = 0 THEN
            v_estado := 'VALIDADO';
            v_validados := v_validados + 1;
        ELSE
            v_estado := 'PENDIENTE';   -- sigue en pendiente para revisión manual
            v_bloqueados := v_bloqueados + 1;
        END IF;

        -- Upsert en ades_reinscripcion_ciclo
        INSERT INTO ades_reinscripcion_ciclo
            (ciclo_origen_id, ciclo_destino_id, estudiante_id,
             estado, tiene_adeudos, monto_adeudado, bloqueantes, fecha_validacion)
        VALUES
            (p_ciclo_origen_id, p_ciclo_destino_id, v_rec.estudiante_id,
             v_estado, v_adeudo > 0, v_adeudo, v_bloqueantes, now())
        ON CONFLICT (estudiante_id, ciclo_destino_id) DO UPDATE
            SET estado             = EXCLUDED.estado,
                tiene_adeudos      = EXCLUDED.tiene_adeudos,
                monto_adeudado     = EXCLUDED.monto_adeudado,
                bloqueantes        = EXCLUDED.bloqueantes,
                fecha_validacion   = EXCLUDED.fecha_validacion,
                fecha_modificacion = now(),
                row_version        = ades_reinscripcion_ciclo.row_version + 1;

    END LOOP;

    RETURN jsonb_build_object(
        'total',      v_total,
        'validados',  v_validados,
        'bloqueados', v_bloqueados,
        'listos_para_aprobar', v_validados
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Trigger de auditoría
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_trigger('ades_reinscripcion_ciclo');

COMMIT;

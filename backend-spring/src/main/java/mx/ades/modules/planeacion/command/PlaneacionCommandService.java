package mx.ades.modules.planeacion.command;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planeacion.domain.model.EstadoTema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Escrituras de planeación — valida transiciones de EstadoTema antes de persistir.
 */
@Service
@RequiredArgsConstructor
public class PlaneacionCommandService {

    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String, Object> crearPlaneacion(UUID grupoId, UUID temaId,
                                               LocalDate fecha, String descripcion, String recursos) {
        String sql = """
            INSERT INTO ades_planeacion_clases
                (grupo_id, tema_id, fecha_planeada, descripcion_actividades, recursos_didacticos)
            VALUES (?::uuid, ?::uuid, ?, ?, ?)
            ON CONFLICT (grupo_id, tema_id) WHERE is_active = TRUE
                DO UPDATE SET fecha_planeada           = EXCLUDED.fecha_planeada,
                              descripcion_actividades   = EXCLUDED.descripcion_actividades,
                              recursos_didacticos       = EXCLUDED.recursos_didacticos
            RETURNING id
            """;
        UUID id = jdbc.queryForObject(sql, UUID.class,
                grupoId.toString(), temaId.toString(), fecha, descripcion, recursos);
        return Map.of("id", id, "estado", EstadoTema.PLANEADO.name());
    }

    /**
     * Transición PLANEADO → IMPARTIDO.
     * Valida que la planeacion_clase exista antes de registrar el avance.
     */
    @Transactional
    public Map<String, Object> completarTema(UUID planeacionId, UUID claseId,
                                             LocalDate fecha, String comentarios) {
        boolean existe = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_planeacion_clases WHERE id = ?::uuid AND is_active = TRUE)",
                Boolean.class, planeacionId.toString()));

        if (!existe) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeacion clase no encontrada: " + planeacionId);

        String sql = """
            INSERT INTO ades_avance_planificacion
                (planeacion_clase_id, clase_id, fecha_ejecucion, es_completado, comentarios_profesor)
            VALUES (?::uuid, ?::uuid, ?, TRUE, ?)
            ON CONFLICT (planeacion_clase_id)
                DO UPDATE SET es_completado        = TRUE,
                              fecha_ejecucion       = EXCLUDED.fecha_ejecucion,
                              comentarios_profesor  = EXCLUDED.comentarios_profesor
            RETURNING id
            """;
        UUID avanceId = jdbc.queryForObject(sql, UUID.class,
                planeacionId.toString(), claseId != null ? claseId.toString() : null,
                fecha, comentarios);
        return Map.of("avance_id", avanceId, "estado", EstadoTema.IMPARTIDO.name());
    }

    @Transactional
    public void eliminarPlaneacion(UUID planeacionId) {
        jdbc.update("UPDATE ades_planeacion_clases SET is_active = FALSE WHERE id = ?::uuid",
                planeacionId.toString());
    }

    /**
     * OA-012: cuando una clase se marca SUSPENDIDA, marca como pendiente_reprogramar
     * los temas planeados para esa fecha+grupo — en vez de perderse silenciosamente.
     * Llamado desde ClaseService al detectar el cambio de estatus_clase.
     */
    @Transactional
    public void marcarPendientesPorSuspension(UUID grupoId, LocalDate fecha) {
        jdbc.update("""
            UPDATE ades_planeacion_clases
               SET pendiente_reprogramar = TRUE
             WHERE grupo_id = ?::uuid AND fecha_planeada = ? AND is_active = TRUE
            """, grupoId.toString(), fecha);
    }

    /** OA-012: reprograma un tema pendiente a una nueva fecha, limpiando el flag. */
    @Transactional
    public void reprogramar(UUID planeacionId, LocalDate nuevaFecha) {
        int rows = jdbc.update("""
            UPDATE ades_planeacion_clases
               SET fecha_planeada = ?, pendiente_reprogramar = FALSE
             WHERE id = ?::uuid AND is_active = TRUE
            """, nuevaFecha, planeacionId.toString());
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeacion clase no encontrada: " + planeacionId);
    }

    /**
     * FASE 2: Crear planeación con campos semanales (trimestre, semana, modalidad, competencia).
     */
    @Transactional
    public Map<String, Object> crearPlaneacionSemanal(
            UUID grupoId, UUID temaId, UUID competenciaId,
            Integer numeroTrimestre, Integer numeroSemana, String modalidad,
            LocalDate fechaPlaneada, LocalDate fechaFin,
            String descripcion, String recursos) {

        // Validar trimestre y semana
        if (numeroTrimestre == null || numeroTrimestre < 1 || numeroTrimestre > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trimestre debe ser 1, 2 o 3");
        }
        if (numeroSemana == null || numeroSemana < 1 || numeroSemana > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semana debe estar entre 1 y 40");
        }
        if (modalidad == null || !modalidad.matches("PRESENCIAL|VIRTUAL|HIBRIDA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidad inválida");
        }

        String sql = """
            INSERT INTO ades_planeacion_clases
                (grupo_id, tema_id, competencia_id, numero_trimestre, numero_semana,
                 modalidad, fecha_planeada, fecha_fin, descripcion_actividades, recursos_didacticos)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        UUID id = jdbc.queryForObject(sql, UUID.class,
                grupoId.toString(), temaId.toString(), competenciaId != null ? competenciaId.toString() : null,
                numeroTrimestre, numeroSemana, modalidad, fechaPlaneada, fechaFin, descripcion, recursos);

        return Map.of(
            "id", id,
            "trimestre", numeroTrimestre,
            "semana", numeroSemana,
            "modalidad", modalidad,
            "estado", EstadoTema.PLANEADO.name()
        );
    }

    /**
     * FASE 2: Actualizar planeación semanal (trimestre, semana, modalidad, competencia).
     */
    @Transactional
    public Map<String, Object> actualizarPlaneacionSemanal(
            UUID planeacionId,
            Integer numeroTrimestre, Integer numeroSemana, String modalidad,
            LocalDate fechaFin, String descripcion, String recursos) {

        // Validar parámetros
        if (numeroTrimestre != null && (numeroTrimestre < 1 || numeroTrimestre > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trimestre debe ser 1, 2 o 3");
        }
        if (numeroSemana != null && (numeroSemana < 1 || numeroSemana > 40)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semana debe estar entre 1 y 40");
        }
        if (modalidad != null && !modalidad.matches("PRESENCIAL|VIRTUAL|HIBRIDA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidad inválida");
        }

        StringBuilder sql = new StringBuilder("""
            UPDATE ades_planeacion_clases
            SET """);

        java.util.List<Object> params = new java.util.ArrayList<>();

        boolean primeroCampo = true;
        if (numeroTrimestre != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("numero_trimestre = ?");
            params.add(numeroTrimestre);
            primeroCampo = false;
        }
        if (numeroSemana != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("numero_semana = ?");
            params.add(numeroSemana);
            primeroCampo = false;
        }
        if (modalidad != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("modalidad = ?");
            params.add(modalidad);
            primeroCampo = false;
        }
        if (fechaFin != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("fecha_fin = ?");
            params.add(fechaFin);
            primeroCampo = false;
        }
        if (descripcion != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("descripcion_actividades = ?");
            params.add(descripcion);
            primeroCampo = false;
        }
        if (recursos != null) {
            if (!primeroCampo) sql.append(", ");
            sql.append("recursos_didacticos = ?");
            params.add(recursos);
            primeroCampo = false;
        }

        sql.append(" WHERE id = ?::uuid AND is_active = TRUE");
        params.add(planeacionId.toString());

        int rows = jdbc.update(sql.toString(), params.toArray());
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Planeación no encontrada");
        }

        return Map.of("id", planeacionId, "actualizado", true);
    }

    /**
     * FASE 2: Crear planeación semanal con aprendizajes esperados.
     *
     * Crea múltiples planeaciones (una por tema) y vincula aprendizajes esperados.
     *
     * @param grupoId          UUID del grupo
     * @param materiaId        UUID de la materia
     * @param trimestre        1-3
     * @param semana           1-40
     * @param modalidad        PRESENCIAL, VIRTUAL, HIBRIDA
     * @param fechaInicio      Fecha inicio semana
     * @param temasSeleccionados Array de temas con aprendizajes
     * @return Map con IDs de planeaciones creadas
     */
    @Transactional
    public Map<String, Object> crearPlaneacionSemanal(
            UUID grupoId,
            UUID materiaId,
            Integer trimestre,
            Integer semana,
            String modalidad,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            java.util.List<Map<String, Object>> temasSeleccionados
    ) {
        java.util.List<UUID> planeacionesCreadas = new java.util.ArrayList<>();

        // Para cada tema seleccionado
        for (Map<String, Object> tema : temasSeleccionados) {
            UUID temaId = UUID.fromString((String) tema.get("tema_id"));
            @SuppressWarnings("unchecked")
            java.util.List<String> aprendizajesIds = (java.util.List<String>) tema.get("aprendizajes_ids");

            // 1. Crear planeación de clase
            String sqlPlaneacion = """
                INSERT INTO ades_planeacion_clases
                    (grupo_id, tema_id, numero_trimestre, numero_semana, modalidad, fecha_planeada, fecha_fin)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (grupo_id, tema_id) WHERE is_active = TRUE
                    DO UPDATE SET numero_trimestre = EXCLUDED.numero_trimestre,
                                  numero_semana = EXCLUDED.numero_semana,
                                  modalidad = EXCLUDED.modalidad,
                                  fecha_planeada = EXCLUDED.fecha_planeada,
                                  fecha_fin = EXCLUDED.fecha_fin
                RETURNING ref
                """;
            UUID planeacionId = jdbc.queryForObject(sqlPlaneacion, UUID.class,
                    grupoId.toString(), temaId.toString(),
                    trimestre, semana, modalidad, fechaInicio, fechaFin);

            planeacionesCreadas.add(planeacionId);

            // 2. Vincular aprendizajes esperados a la planeación
            if (aprendizajesIds != null && !aprendizajesIds.isEmpty()) {
                for (String aprendizajeIdStr : aprendizajesIds) {
                    String sqlAprendizaje = """
                        INSERT INTO ades_planeacion_aprendizajes
                            (planeacion_clase_id, aprendizaje_esperado_id)
                        VALUES (?::uuid, ?::uuid)
                        ON CONFLICT DO NOTHING
                        """;
                    jdbc.update(sqlAprendizaje, planeacionId.toString(), aprendizajeIdStr);
                }
            }
        }

        return Map.of(
            "planeaciones_creadas", planeacionesCreadas.size(),
            "ids", planeacionesCreadas,
            "trimestre", trimestre,
            "semana", semana
        );
    }

    // ── FASE 3: Tareas Vinculadas a Planeación ────────────────────────────────

    /**
     * FASE 3: Crear tarea vinculada a una planeación semanal.
     *
     * La tarea hereda automáticamente:
     * - planeacion_clase_id (explícito)
     * - grupo_id (de la planeación)
     * - materia_id (del tema en la planeación)
     * - aprendizajes_esperados[] (via trigger fn_heredar_aprendizajes_desde_planeacion)
     *
     * @param planeacionClaseId UUID de la planeación
     * @param titulo Título de la tarea
     * @param descripcion Descripción
     * @param fechaEntrega Fecha de entrega
     * @param puntajeMaximo Puntaje máximo (default 10)
     * @param permiteEntregaTarde Si permite entregas tardías
     * @param instruccionesUrl URL con instrucciones
     * @return Map con ID de tarea creada y contexto
     */
    @Transactional
    public Map<String, Object> crearTareaDesdeplanneacion(
            UUID planeacionClaseId,
            String titulo,
            String descripcion,
            LocalDate fechaEntrega,
            Double puntajeMaximo,
            Boolean permiteEntregaTarde,
            String instruccionesUrl
    ) {
        // Verificar que la planeación existe y obtener contexto
        String sqlGetContext = """
            SELECT pc.grupo_id, t.materia_id
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            WHERE pc.ref = ?::uuid AND pc.is_active = TRUE
            """;

        Map<String, Object> context;
        try {
            context = jdbc.queryForMap(sqlGetContext, planeacionClaseId.toString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeación clase no encontrada: " + planeacionClaseId);
        }

        UUID grupoId = (UUID) context.get("grupo_id");
        UUID materiaId = (UUID) context.get("materia_id");

        // Crear tarea — el trigger heredará automáticamente aprendizajes_esperados[]
        String sqlCreateTarea = """
            INSERT INTO ades_tareas
                (grupo_id, materia_id, titulo, descripcion, planeacion_clase_id,
                 fecha_asignacion, fecha_entrega, puntaje_maximo, permite_entrega_tarde,
                 instrucciones_url, tipo_item, origen)
            VALUES
                (?::uuid, ?::uuid, ?, ?, ?::uuid, CURRENT_DATE, ?, ?, ?, ?, 'tarea', 'PLANEACION')
            RETURNING ref
            """;

        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(grupoId.toString());
        params.add(materiaId.toString());
        params.add(titulo);
        params.add(descripcion);
        params.add(planeacionClaseId.toString());
        params.add(fechaEntrega);
        params.add(puntajeMaximo != null ? puntajeMaximo : 10.0);
        params.add(permiteEntregaTarde != null ? permiteEntregaTarde : false);
        params.add(instruccionesUrl);

        UUID tareaId = jdbc.queryForObject(sqlCreateTarea, UUID.class, params.toArray());

        return Map.of(
            "tarea_id", tareaId,
            "planeacion_clase_id", planeacionClaseId,
            "grupo_id", grupoId,
            "materia_id", materiaId,
            "titulo", titulo,
            "mensaje", "Tarea creada. Aprendizajes esperados heredados automáticamente."
        );
    }

    /**
     * FASE 3: Crear examen (evaluación) vinculada a una planeación semanal.
     * Similar a crearTareaDesdeplanneacion pero crea entrada en ades_evaluaciones.
     *
     * @param planeacionClaseId UUID de la planeación
     * @param nombreEvaluacion Nombre del examen
     * @param descripcion Descripción
     * @param fecha Fecha de la evaluación
     * @param puntajeMaximo Puntaje máximo
     * @return Map con ID de evaluación creada
     */
    @Transactional
    public Map<String, Object> crearExamenDesdeplanneacion(
            UUID planeacionClaseId,
            String nombreEvaluacion,
            String descripcion,
            LocalDate fecha,
            Double puntajeMaximo
    ) {
        // Verificar que la planeación existe y obtener contexto
        String sqlGetContext = """
            SELECT pc.grupo_id, t.materia_id
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            WHERE pc.ref = ?::uuid AND pc.is_active = TRUE
            """;

        Map<String, Object> context;
        try {
            context = jdbc.queryForMap(sqlGetContext, planeacionClaseId.toString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Planeación clase no encontrada: " + planeacionClaseId);
        }

        UUID grupoId = (UUID) context.get("grupo_id");

        // Crear evaluación (examen)
        String sqlCreateEval = """
            INSERT INTO ades_evaluaciones
                (grupo_id, nombre, descripcion, fecha, puntaje_maximo, planeacion_clase_id)
            VALUES (?::uuid, ?, ?, ?, ?, ?::uuid)
            RETURNING id
            """;

        java.util.UUID evaluacionId = jdbc.queryForObject(sqlCreateEval, UUID.class,
                grupoId.toString(), nombreEvaluacion, descripcion, fecha,
                puntajeMaximo != null ? puntajeMaximo : 10.0,
                planeacionClaseId.toString());

        return Map.of(
            "evaluacion_id", evaluacionId,
            "planeacion_clase_id", planeacionClaseId,
            "nombre", nombreEvaluacion,
            "mensaje", "Examen creado. Aprendizajes esperados heredados automáticamente."
        );
    }
}

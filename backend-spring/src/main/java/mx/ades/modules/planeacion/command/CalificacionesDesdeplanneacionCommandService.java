package mx.ades.modules.planeacion.command;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * FASE 4: Guardar calificaciones de tareas y exámenes
 *
 * Maneja:
 * - Guardar calificación de tarea (insert o update)
 * - Guardar calificación de examen
 * - Validaciones
 */
@Service
@RequiredArgsConstructor
public class CalificacionesDesdeplanneacionCommandService {

    private final JdbcTemplate jdbc;

    /**
     * FASE 4: Guardar calificación de una tarea.
     *
     * @param tareaId UUID de la tarea
     * @param alumnoId UUID del alumno
     * @param calificacion Nota (0-10)
     * @param comentarios Comentarios opcionales
     * @return Map con resultado
     */
    @Transactional
    public Map<String, Object> guardarCalificacionTarea(
            UUID tareaId,
            UUID alumnoId,
            Double calificacion,
            String comentarios
    ) {
        // Validar que la tarea existe
        boolean tareaExiste = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_tareas WHERE ref = ?::uuid AND is_active = TRUE)",
                Boolean.class, tareaId.toString()));

        if (!tareaExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada");
        }

        // Validar que el alumno existe
        boolean alumnoExiste = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_estudiantes WHERE ref = ?::uuid AND is_active = TRUE)",
                Boolean.class, alumnoId.toString()));

        if (!alumnoExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        }

        // Validar rango de calificación
        if (calificacion < 0 || calificacion > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificación debe estar entre 0 y 10");
        }

        // Insertar o actualizar calificación
        String sql = """
            INSERT INTO ades_calificaciones_tareas
                (tarea_id, alumno_id, calificacion, comentarios)
            VALUES (?::uuid, ?::uuid, ?, ?)
            ON CONFLICT (tarea_id, alumno_id)
                DO UPDATE SET calificacion = EXCLUDED.calificacion,
                              comentarios = EXCLUDED.comentarios,
                              fecha_modificacion = NOW()
            RETURNING ref
            """;

        UUID calificacionId = jdbc.queryForObject(sql, UUID.class,
                tareaId.toString(), alumnoId.toString(), calificacion, comentarios);

        return Map.of(
            "calificacion_id", calificacionId,
            "tarea_id", tareaId,
            "alumno_id", alumnoId,
            "calificacion", calificacion,
            "mensaje", "Calificación de tarea guardada"
        );
    }

    /**
     * FASE 4: Guardar calificación de una evaluación (examen).
     *
     * @param evaluacionId UUID de la evaluación
     * @param estudianteId UUID del estudiante
     * @param calificacion Nota (0-10)
     * @param comentarios Comentarios opcionales
     * @return Map con resultado
     */
    @Transactional
    public Map<String, Object> guardarCalificacionEvaluacion(
            UUID evaluacionId,
            UUID estudianteId,
            Double calificacion,
            String comentarios
    ) {
        // Validar que la evaluación existe
        boolean evaluacionExiste = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_evaluaciones WHERE ref = ?::uuid AND is_active = TRUE)",
                Boolean.class, evaluacionId.toString()));

        if (!evaluacionExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada");
        }

        // Validar que el estudiante existe
        boolean estudianteExiste = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ades_estudiantes WHERE ref = ?::uuid AND is_active = TRUE)",
                Boolean.class, estudianteId.toString()));

        if (!estudianteExiste) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado");
        }

        // Validar rango de calificación
        if (calificacion < 0 || calificacion > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificación debe estar entre 0 y 10");
        }

        // Insertar o actualizar calificación
        // NOTA: ades_calificaciones_evaluaciones tiene UNIQUE (evaluacion_id, estudiante_id)
        String sql = """
            INSERT INTO ades_calificaciones_evaluaciones
                (evaluacion_id, estudiante_id, calificacion, comentarios)
            VALUES (?::uuid, ?::uuid, ?, ?)
            ON CONFLICT (evaluacion_id, estudiante_id)
                DO UPDATE SET calificacion = EXCLUDED.calificacion,
                              comentarios = EXCLUDED.comentarios,
                              fecha_modificacion = NOW()
            RETURNING ref
            """;

        UUID calificacionId = jdbc.queryForObject(sql, UUID.class,
                evaluacionId.toString(), estudianteId.toString(), calificacion, comentarios);

        return Map.of(
            "calificacion_id", calificacionId,
            "evaluacion_id", evaluacionId,
            "estudiante_id", estudianteId,
            "calificacion", calificacion,
            "mensaje", "Calificación de evaluación guardada"
        );
    }

    /**
     * FASE 4: Guardar múltiples calificaciones de una tarea (batch).
     *
     * @param tareaId UUID de la tarea
     * @param calificaciones List de {alumno_id, calificacion, comentarios}
     * @return Map con conteo de registros guardados
     */
    @Transactional
    public Map<String, Object> guardarCalificacionesTareaBatch(
            UUID tareaId,
            java.util.List<Map<String, Object>> calificaciones
    ) {
        int contador = 0;

        for (Map<String, Object> cal : calificaciones) {
            UUID alumnoId = UUID.fromString((String) cal.get("alumno_id"));
            Double calificacion = ((Number) cal.get("calificacion")).doubleValue();
            String comentarios = (String) cal.getOrDefault("comentarios", "");

            try {
                guardarCalificacionTarea(tareaId, alumnoId, calificacion, comentarios);
                contador++;
            } catch (Exception e) {
                // Log pero continúa con el siguiente
                System.err.println("Error guardando calificación para alumno " + alumnoId + ": " + e.getMessage());
            }
        }

        return Map.of(
            "tarea_id", tareaId,
            "registros_guardados", contador,
            "total_intentos", calificaciones.size(),
            "mensaje", contador + " de " + calificaciones.size() + " calificaciones guardadas"
        );
    }

    /**
     * FASE 4: Guardar múltiples calificaciones de una evaluación (batch).
     *
     * @param evaluacionId UUID de la evaluación
     * @param calificaciones List de {estudiante_id, calificacion, comentarios}
     * @return Map con conteo de registros guardados
     */
    @Transactional
    public Map<String, Object> guardarCalificacionesEvaluacionBatch(
            UUID evaluacionId,
            java.util.List<Map<String, Object>> calificaciones
    ) {
        int contador = 0;

        for (Map<String, Object> cal : calificaciones) {
            UUID estudianteId = UUID.fromString((String) cal.get("estudiante_id"));
            Double calificacion = ((Number) cal.get("calificacion")).doubleValue();
            String comentarios = (String) cal.getOrDefault("comentarios", "");

            try {
                guardarCalificacionEvaluacion(evaluacionId, estudianteId, calificacion, comentarios);
                contador++;
            } catch (Exception e) {
                System.err.println("Error guardando calificación para estudiante " + estudianteId + ": " + e.getMessage());
            }
        }

        return Map.of(
            "evaluacion_id", evaluacionId,
            "registros_guardados", contador,
            "total_intentos", calificaciones.size(),
            "mensaje", contador + " de " + calificaciones.size() + " calificaciones guardadas"
        );
    }
}

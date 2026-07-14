package mx.ades.modules.eval_docente;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DP-016: genera un plan de mejora por reglas (no IA) a partir de los criterios
 * con calificación baja de una evaluación docente 360°. Umbral configurable —
 * la escala de evaluación docente es 1-5 (ver feedback_eval360_patterns).
 */
@Service
public class PlanMejoraService {

    private static final int UMBRAL_CALIFICACION_BAJA = 3;

    /** Valores permitidos por el CHECK de ades_planes_mejora_docente.estado. */
    private static final java.util.Set<String> ESTADOS_VALIDOS =
            java.util.Set.of("PENDIENTE", "EN_PROGRESO", "COMPLETADO");

    /** Mapeo criterio→recomendación — extensible sin tocar lógica de negocio. */
    private static final Map<String, String> RECOMENDACIONES = Map.of(
            "Puntualidad y asistencia", "Revisar con dirección los horarios de entrada/salida y registrar causas de inasistencia recurrente.",
            "Dominio de contenidos", "Participar en capacitación de actualización disciplinar del área correspondiente.",
            "Planeación didáctica", "Usar la plantilla de planeación semanal del módulo de Planeación y solicitar retroalimentación del coordinador académico antes de impartir.",
            "Uso de estrategias activas", "Incorporar al menos una estrategia de aprendizaje activo (trabajo colaborativo, aprendizaje basado en problemas) por semana.",
            "Evaluación formativa", "Aplicar evaluaciones formativas de cierre de tema (no solo exámenes), usando el módulo de Gradebook.",
            "Clima de aula y disciplina", "Revisar con el módulo de Conducta los incidentes recientes del grupo y coordinar con el coordinador de disciplina un plan de manejo de grupo.",
            "Atención a alumnos en riesgo", "Revisar el listado de alumnos en riesgo académico (módulo IA) y documentar acciones de apoyo específicas."
    );

    private static final String RECOMENDACION_GENERICA =
            "Coordinar con el coordinador académico un plan de acompañamiento específico para este criterio.";

    private final JdbcTemplate jdbc;

    public PlanMejoraService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<Map<String, Object>> generar(UUID evaluacionId) {
        List<Map<String, Object>> criteriosBajos = jdbc.queryForList("""
            SELECT c.criterio_id, cr.nombre_criterio, c.calificacion
            FROM ades_eval_docente_criterios c
            JOIN ades_criterios_eval_docente cr ON cr.id = c.criterio_id
            WHERE c.evaluacion_id = ? AND c.calificacion <= ?
            """, evaluacionId, UMBRAL_CALIFICACION_BAJA);

        for (Map<String, Object> c : criteriosBajos) {
            String nombreCriterio = (String) c.get("nombre_criterio");
            String recomendacion = RECOMENDACIONES.getOrDefault(nombreCriterio, RECOMENDACION_GENERICA);
            jdbc.update("""
                INSERT INTO ades_planes_mejora_docente (evaluacion_id, criterio_debil, calificacion, recomendacion)
                VALUES (?, ?, ?, ?)
                """, evaluacionId, nombreCriterio, c.get("calificacion"), recomendacion);
        }
        return listar(evaluacionId);
    }

    public List<Map<String, Object>> listar(UUID evaluacionId) {
        return jdbc.queryForList("""
            SELECT id, criterio_debil, calificacion, recomendacion, fecha_seguimiento, estado
            FROM ades_planes_mejora_docente
            WHERE evaluacion_id = ? AND is_active = TRUE
            ORDER BY calificacion ASC
            """, evaluacionId);
    }

    @Transactional
    public void actualizarEstado(UUID id, String estado) {
        // Validación previa al UPDATE: el CHECK de ades_planes_mejora_docente.estado
        // solo acepta PENDIENTE/EN_PROGRESO/COMPLETADO — sin esto, un valor inválido
        // llegaba crudo hasta la BD y disparaba una DataIntegrityViolationException
        // (409 engañoso vía GlobalExceptionHandler) en vez de un 400 claro
        // (hallazgo de auditoría de consistencia BD↔backend).
        if (estado == null || !ESTADOS_VALIDOS.contains(estado)) {
            throw new IllegalArgumentException(
                    "estado inválido: " + estado + ". Válidos: " + ESTADOS_VALIDOS);
        }
        int filas = jdbc.update("UPDATE ades_planes_mejora_docente SET estado = ? WHERE id = ? AND is_active = TRUE", estado, id);
        if (filas == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Plan de mejora no encontrado");
        }
    }
}

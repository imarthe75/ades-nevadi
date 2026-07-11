package mx.ades.modules.learning_paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IA-009: ajuste dinámico de la ruta de aprendizaje según el desempeño reciente
 * del alumno. Reusa el progreso ya trackeado en ades_lp_progreso — sin IA/LLM,
 * lógica basada en reglas: si el promedio de calificación de recursos
 * completados cae bajo el umbral, los recursos de tipo REFUERZO pendientes se
 * reordenan para aparecer primero en la ruta.
 */
@Service
public class AjusteDinamicoService {

    private static final double UMBRAL_DESEMPENO = 7.0;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AjusteDinamicoService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * IA-014: persiste la narrativa generada por IA para que sobreviva a un refresh de página.
     * Invocado tanto internamente desde {@link #ajustar} (ya dentro de su propia
     * transacción — este @Transactional no aplica por auto-invocación, pero tampoco
     * hace falta) como directamente desde el controller (invocación externa vía
     * proxy, donde este @Transactional sí es necesario para persistir).
     */
    @Transactional
    public void guardarNarrativa(UUID asignacionId, Map<?, ?> narrativa) {
        try {
            String json = objectMapper.writeValueAsString(narrativa);
            jdbc.update("UPDATE ades_lp_asignaciones SET ia_recomendacion = ?::jsonb WHERE id = ?",
                    json, asignacionId);
        } catch (Exception e) {
            // No bloquear la respuesta al usuario si falla solo la persistencia.
        }
    }

    @Transactional
    public Map<String, Object> ajustar(UUID estudianteId) {
        List<Map<String, Object>> asignaciones = jdbc.queryForList("""
            SELECT id, path_id FROM ades_lp_asignaciones
            WHERE estudiante_id = ? AND is_active = TRUE AND estatus != 'COMPLETADO'
            """, estudianteId);

        if (asignaciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El alumno no tiene rutas de aprendizaje activas");
        }

        int ajustadas = 0;
        for (Map<String, Object> asig : asignaciones) {
            UUID asignacionId = (UUID) asig.get("id");
            UUID pathId = (UUID) asig.get("path_id");

            Double promedio = jdbc.queryForObject("""
                SELECT AVG(calificacion) FROM ades_lp_progreso
                WHERE asignacion_id = ? AND completado = TRUE AND calificacion IS NOT NULL
                """, Double.class, asignacionId);

            if (promedio == null) continue;

            Map<String, Object> resultado;
            if (promedio < UMBRAL_DESEMPENO) {
                Integer minOrdenPendiente = jdbc.queryForObject("""
                    SELECT MIN(r.orden) FROM ades_lp_recursos r
                    WHERE r.path_id = ? AND r.is_active = TRUE
                      AND NOT EXISTS (SELECT 1 FROM ades_lp_progreso p
                                      WHERE p.asignacion_id = ? AND p.recurso_id = r.id AND p.completado = TRUE)
                    """, Integer.class, pathId, asignacionId);

                if (minOrdenPendiente != null) {
                    int nuevosPromovidos = jdbc.update("""
                        UPDATE ades_lp_recursos SET orden = ? - 1
                        WHERE path_id = ? AND tipo = 'REFUERZO' AND is_active = TRUE
                          AND orden > ?
                          AND NOT EXISTS (SELECT 1 FROM ades_lp_progreso p
                                          WHERE p.asignacion_id = ? AND p.recurso_id = ades_lp_recursos.id AND p.completado = TRUE)
                        """, minOrdenPendiente, pathId, minOrdenPendiente, asignacionId);
                    ajustadas++;
                    resultado = Map.of("promedio", promedio, "recursos_refuerzo_promovidos", nuevosPromovidos);
                } else {
                    resultado = Map.of("promedio", promedio, "recursos_refuerzo_promovidos", 0);
                }
            } else {
                resultado = Map.of("promedio", promedio, "recursos_refuerzo_promovidos", 0, "estado", "DESEMPENO_ADECUADO");
            }

            guardarNarrativa(asignacionId, resultado);
        }

        return Map.of("estudiante_id", estudianteId.toString(), "asignaciones_evaluadas", asignaciones.size(),
                "asignaciones_ajustadas", ajustadas);
    }
}

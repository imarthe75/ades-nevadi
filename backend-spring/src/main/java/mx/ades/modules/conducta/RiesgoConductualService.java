package mx.ades.modules.conducta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SB-016: análisis de patrones de conducta (factores de riesgo) — basado en
 * reglas (frecuencia + severidad de faltas en una ventana de tiempo), sin
 * IA/LLM. Mismo enfoque que el scoring de riesgo académico (IA-005).
 */
@Service
public class RiesgoConductualService {

    private static final int VENTANA_DIAS = 90;
    // Pesos: LEVE=1, GRAVE=3, MUY_GRAVE=6 — una MUY_GRAVE pesa como 6 LEVES.
    private static final Map<String, Integer> PESO_FALTA = Map.of("LEVE", 1, "GRAVE", 3, "MUY_GRAVE", 6);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RiesgoConductualService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Recalcula el riesgo conductual de todos los alumnos de un grupo (uso: dashboard director). */
    @Transactional
    public List<Map<String, Object>> recalcularGrupo(UUID grupoId) {
        List<UUID> estudiantes = jdbc.queryForList(
                "SELECT DISTINCT estudiante_id FROM ades_reportes_conducta WHERE grupo_id = ? AND is_active = TRUE",
                UUID.class, grupoId);
        for (UUID estudianteId : estudiantes) {
            calcular(estudianteId);
        }
        return jdbc.queryForList("""
            SELECT DISTINCT ON (estudiante_id) estudiante_id, score_riesgo, nivel_riesgo,
                   total_incidentes, incidentes_graves, fecha_calculo
            FROM ades_riesgo_conductual
            WHERE estudiante_id = ANY(?)
            ORDER BY estudiante_id, fecha_calculo DESC
            """, (Object) estudiantes.toArray(new UUID[0]));
    }

    @Transactional
    public Map<String, Object> calcular(UUID estudianteId) {
        List<Map<String, Object>> faltas = jdbc.queryForList("""
            SELECT tipo_falta, fecha_reporte FROM ades_reportes_conducta
            WHERE estudiante_id = ? AND is_active = TRUE
              AND fecha_reporte >= CURRENT_DATE - (? || ' days')::interval
            """, estudianteId, VENTANA_DIAS);

        int totalIncidentes = faltas.size();
        int incidentesGraves = 0;
        double score = 0;
        for (Map<String, Object> f : faltas) {
            String tipo = (String) f.get("tipo_falta");
            score += PESO_FALTA.getOrDefault(tipo, 1);
            if ("GRAVE".equals(tipo) || "MUY_GRAVE".equals(tipo)) incidentesGraves++;
        }

        String nivelRiesgo = score >= 12 ? "ALTO" : score >= 5 ? "MEDIO" : "BAJO";

        Map<String, Object> indicadores = Map.of(
                "total_incidentes", totalIncidentes,
                "incidentes_graves", incidentesGraves,
                "ventana_dias", VENTANA_DIAS
        );

        try {
            String json = objectMapper.writeValueAsString(indicadores);
            jdbc.update("""
                INSERT INTO ades_riesgo_conductual
                    (estudiante_id, score_riesgo, nivel_riesgo, total_incidentes, incidentes_graves, ventana_dias, indicadores_json)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, estudianteId, score, nivelRiesgo, totalIncidentes, incidentesGraves, VENTANA_DIAS, json);
        } catch (Exception e) {
            // No bloquear si falla la serialización — el score en memoria se retorna igual.
        }

        return Map.of("estudiante_id", estudianteId.toString(), "score_riesgo", score,
                "nivel_riesgo", nivelRiesgo, "total_incidentes", totalIncidentes, "incidentes_graves", incidentesGraves);
    }

    public Map<String, Object> obtenerUltimo(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT score_riesgo, nivel_riesgo, total_incidentes, incidentes_graves, fecha_calculo
            FROM ades_riesgo_conductual WHERE estudiante_id = ? ORDER BY fecha_calculo DESC LIMIT 1
            """, estudianteId);
        return rows.isEmpty() ? Map.of("nivel_riesgo", "SIN_DATOS") : rows.get(0);
    }
}

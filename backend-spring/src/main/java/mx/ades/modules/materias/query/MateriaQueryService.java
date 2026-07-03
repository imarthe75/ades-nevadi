package mx.ades.modules.materias.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo materias.
 * Consulta el catálogo de materias con filtros por nivel educativo, grupo o tipo.
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class MateriaQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listar(UUID nivelEducativoId, UUID grupoId, String tipo, boolean incluirInactivas) {
        StringBuilder sql = new StringBuilder(
            "SELECT m.id, m.nombre_materia, m.clave_materia, m.nivel_educativo_id, " +
            "  m.horas_semana, m.tipo_materia, m.es_inglés AS es_ingles, m.is_active, " +
            "  ne.nombre_nivel " +
            "FROM ades_materias m " +
            "LEFT JOIN ades_niveles_educativos ne ON ne.id = m.nivel_educativo_id " +
            "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (!incluirInactivas) sql.append("AND m.is_active = TRUE ");
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND m.tipo_materia LIKE ? ");
            params.add(tipo.toUpperCase() + "%");
        }
        if (grupoId != null) {
            sql.append("AND m.nivel_educativo_id = (" +
                "SELECT gr.nivel_educativo_id FROM ades_grados gr " +
                "JOIN ades_grupos g ON g.grado_id = gr.id WHERE g.id = ?) ");
            params.add(grupoId);
        } else if (nivelEducativoId != null) {
            sql.append("AND m.nivel_educativo_id = ? ");
            params.add(nivelEducativoId);
        }
        sql.append("ORDER BY m.tipo_materia, m.nombre_materia");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> estadisticas(UUID materiaId) {
        Map<String, Object> materia = jdbc.queryForMap(
            "SELECT id AS materia_id, nombre_materia FROM ades_materias WHERE id = ?", materiaId);

        Integer gradosAsignados = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_materias_plan WHERE materia_id = ? AND is_active = TRUE",
            Integer.class, materiaId);

        Integer totalTareas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_tareas WHERE materia_id = ? AND is_active = TRUE",
            Integer.class, materiaId);

        Integer totalCalificaciones = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ades_calificaciones_periodo WHERE materia_id = ? AND is_active = TRUE",
            Integer.class, materiaId);

        Integer totalRubricas = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT rubrica_id) FROM ades_tareas " +
            "WHERE materia_id = ? AND rubrica_id IS NOT NULL AND is_active = TRUE",
            Integer.class, materiaId);

        java.math.BigDecimal promedio = jdbc.queryForObject(
            "SELECT ROUND(AVG(calificacion_final), 2) FROM ades_calificaciones_periodo " +
            "WHERE materia_id = ? AND is_active = TRUE",
            java.math.BigDecimal.class, materiaId);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("materia_id", materia.get("materia_id"));
        result.put("nombre_materia", materia.get("nombre_materia"));
        result.put("grados_asignados", gradosAsignados);
        result.put("total_tareas", totalTareas);
        result.put("total_calificaciones", totalCalificaciones);
        result.put("total_rubricas", totalRubricas);
        result.put("promedio_calificaciones", promedio);
        return result;
    }
}

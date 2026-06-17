package mx.ades.modules.learning_paths.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LearningPathQueryService {

    private final JdbcTemplate jdbc;

    public LearningPathQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarPaths(Boolean activos, String criterio) {
        return jdbc.queryForList("""
                SELECT lp.id, lp.nombre, lp.descripcion, lp.criterio_activacion,
                       lp.umbral_activacion, lp.is_active,
                       COUNT(r.id) AS total_recursos
                FROM ades_learning_paths lp
                LEFT JOIN ades_lp_recursos r ON r.path_id = lp.id AND r.is_active = TRUE
                WHERE (?::boolean IS NULL OR lp.is_active = ?)
                  AND (?::text IS NULL OR lp.criterio_activacion = ?)
                GROUP BY lp.id
                ORDER BY lp.nombre
                """, activos, activos, criterio, criterio);
    }

    public Map<String, Object> detallePath(UUID pathId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active
                FROM ades_learning_paths WHERE id = ?
                """, pathId);
        if (rows.isEmpty()) return null;

        Map<String, Object> path = new HashMap<>(rows.get(0));
        List<Map<String, Object>> recursos = jdbc.queryForList("""
                SELECT id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active
                FROM ades_lp_recursos WHERE path_id = ? AND is_active = TRUE ORDER BY orden
                """, pathId);
        path.put("total_recursos", recursos.size());
        path.put("recursos", recursos);
        return path;
    }

    public List<Map<String, Object>> listarAsignaciones(UUID estudianteId, String estatus) {
        return jdbc.queryForList("""
                SELECT a.id, a.path_id, lp.nombre AS path_nombre,
                       a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion
                FROM ades_lp_asignaciones a
                JOIN ades_learning_paths lp ON lp.id = a.path_id
                WHERE (?::uuid IS NULL OR a.estudiante_id = ?)
                  AND (?::text IS NULL OR a.estatus = ?)
                ORDER BY a.fecha_creacion DESC
                """, estudianteId, estudianteId, estatus, estatus);
    }

    public Map<String, Object> detalleAsignacion(UUID asigId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.path_id, lp.nombre AS path_nombre,
                       a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion
                FROM ades_lp_asignaciones a
                JOIN ades_learning_paths lp ON lp.id = a.path_id
                WHERE a.id = ?
                """, asigId);
        if (rows.isEmpty()) return null;

        Map<String, Object> response = new HashMap<>(rows.get(0));

        List<Map<String, Object>> progresoRows = jdbc.queryForList("""
                SELECT p.recurso_id, r.titulo, r.tipo, r.orden,
                       p.completado, p.tiempo_min, p.calificacion, p.fccompletado
                FROM ades_lp_progreso p
                JOIN ades_lp_recursos r ON r.id = p.recurso_id
                WHERE p.asignacion_id = ?
                ORDER BY r.orden
                """, asigId);

        Integer totalRecursos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_recursos
                WHERE path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?) AND is_active = TRUE
                """, Integer.class, asigId);

        long completados = progresoRows.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("completado"))).count();

        List<Map<String, Object>> progreso = new ArrayList<>();
        for (Map<String, Object> p : progresoRows) {
            Map<String, Object> pd = new HashMap<>();
            pd.put("recurso_id", p.get("recurso_id").toString());
            pd.put("titulo", p.get("titulo"));
            pd.put("tipo", p.get("tipo"));
            pd.put("orden", p.get("orden"));
            pd.put("completado", p.get("completado"));
            pd.put("tiempo_min", p.get("tiempo_min"));
            pd.put("calificacion", p.get("calificacion"));
            pd.put("fccompletado", p.get("fccompletado") != null ? p.get("fccompletado").toString() : null);
            progreso.add(pd);
        }

        response.put("recursos_completados", completados);
        response.put("total_recursos", totalRecursos != null ? totalRecursos : 0);
        response.put("progreso", progreso);
        return response;
    }
}

package mx.ades.modules.portal_familias.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PortalFamiliasQueryService {

    private final JdbcTemplate jdbc;

    public PortalFamiliasQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarTutores(UUID alumnoId) {
        String sql = "SELECT ta.id, ta.relacion, ta.prioridad, ta.puede_recoger, " +
                "ta.es_responsable_economico, ta.es_contacto_emergencia, " +
                "ta.nivel_acceso_portal, ta.is_active, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "p.telefono_principal, p.email " +
                "FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE ta.alumno_id = ? AND ta.is_active = TRUE " +
                "ORDER BY ta.prioridad";
        return jdbc.queryForList(sql, alumnoId);
    }

    public List<Map<String, Object>> misAlumnos(String email) {
        String sql = "SELECT e.id AS alumno_id, e.matricula AS numero_control, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "g.nombre_grupo, pl.nombre_plantel, " +
                "ta.relacion, ta.nivel_acceso_portal " +
                "FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas per ON per.email = ? " +
                "JOIN ades_personas p ON p.id = (SELECT persona_id FROM ades_estudiantes WHERE id = ta.alumno_id) " +
                "JOIN ades_estudiantes e ON e.id = ta.alumno_id " +
                "LEFT JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = g.plantel_id " +
                "WHERE ta.persona_id = per.id AND ta.is_active = TRUE AND e.is_active = TRUE";
        return jdbc.queryForList(sql, email);
    }

    public Map<String, Object> resumenAcademico(UUID alumnoId) {
        List<Map<String, Object>> calif = jdbc.queryForList(
                "SELECT m.nombre_materia, c.calificacion_final, pe.nombre_periodo AS periodo " +
                "FROM ades_calificaciones_periodo c " +
                "JOIN ades_materias m ON m.id = c.materia_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = c.periodo_evaluacion_id " +
                "WHERE c.estudiante_id = ? AND c.is_active = TRUE " +
                "ORDER BY m.nombre_materia, pe.numero_periodo",
                alumnoId
        );

        List<Map<String, Object>> asist = jdbc.queryForList(
                "SELECT COUNT(*) AS total, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END), 0) AS presentes, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'AUSENTE' THEN 1 ELSE 0 END), 0) AS faltas, " +
                "COALESCE(SUM(CASE WHEN estatus_asistencia = 'TARDE' THEN 1 ELSE 0 END), 0) AS tardanzas " +
                "FROM ades_asistencias " +
                "WHERE estudiante_id = ? AND is_active = TRUE",
                alumnoId
        );

        List<Map<String, Object>> cond = jdbc.queryForList(
                "SELECT COUNT(*) AS total_incidentes " +
                "FROM ades_incidentes_conducta " +
                "WHERE estudiante_id = ? AND is_active = TRUE",
                alumnoId
        );

        Map<String, Object> response = new HashMap<>();
        response.put("calificaciones", calif);
        response.put("asistencias", asist.isEmpty() ? Collections.emptyMap() : asist.get(0));
        response.put("conducta", cond.isEmpty() ? Map.of("total_incidentes", 0) : cond.get(0));
        return response;
    }
}

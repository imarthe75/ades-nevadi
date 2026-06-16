package mx.ades.modules.padres.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PadresQueryService {

    private final JdbcTemplate jdbc;

    public PadresQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> misAlumnos(UUID personaId) {
        if (personaId == null) return List.of();
        return jdbc.queryForList(
                "SELECT cf.estudiante_id, " +
                "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo, " +
                "e.matricula, " +
                "COALESCE(ne.nombre_nivel, '—') AS nivel, " +
                "COALESCE(gr.nombre_grado, '—') AS grado, " +
                "COALESCE(g.nombre_grupo, '—') AS grupo, " +
                "COALESCE(pl.nombre_plantel, '—') AS plantel, " +
                "cf.parentesco, " +
                "cf.es_tutor_legal " +
                "FROM ades_contactos_familiares cf " +
                "JOIN ades_estudiantes e ON e.id = cf.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones ins ON ins.estudiante_id = e.id AND ins.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "LEFT JOIN ades_grados gr ON gr.id = g.grado_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
                "LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE cf.persona_id = ? AND cf.is_active = TRUE AND e.is_active = TRUE",
                personaId);
    }

    public boolean esContactoDeAlumno(UUID personaId, UUID estudianteId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_contactos_familiares " +
                "WHERE persona_id = ? AND estudiante_id = ? AND is_active = TRUE",
                Integer.class, personaId, estudianteId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> calificaciones(UUID estudianteId) {
        return jdbc.queryForList(
                "SELECT m.nombre_materia AS materia, " +
                "pe.nombre_periodo AS periodo, " +
                "cp.calificacion_final AS calificacion, " +
                "cp.es_acreditado " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_materias m ON m.id = cp.materia_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "WHERE cp.estudiante_id = ? AND cp.is_active = TRUE " +
                "ORDER BY pe.fecha_inicio, m.nombre_materia",
                estudianteId);
    }
}

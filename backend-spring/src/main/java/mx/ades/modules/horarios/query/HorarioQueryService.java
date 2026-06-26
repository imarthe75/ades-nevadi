package mx.ades.modules.horarios.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo horarios.
 * Consulta horarios por grupo, profesor o plantel con datos enriquecidos de materia y aula.
 *
 * @author ADES
 * @since 2026
 */
@Service
public class HorarioQueryService {

    private static final String SELECT =
        "SELECT h.id, h.grupo_id, h.materia_id, h.profesor_id, h.aula_id, " +
        "  h.corrida_id, h.ciclo_escolar_id, h.dia_semana, h.hora_inicio, h.hora_fin, h.origen, h.fijado, " +
        "  h.is_active, h.row_version, " +
        "  m.nombre_materia, " +
        "  p2.nombre || ' ' || p2.apellido_paterno AS nombre_profesor, " +
        "  a.nombre_aula, " +
        "  g.nombre_grupo, " +
        "  gr.nombre_grado, gr.numero_grado, gr.plantel_id " +
        "FROM ades_horarios h " +
        "JOIN ades_grupos g ON g.id = h.grupo_id " +
        "JOIN ades_grados gr ON gr.id = g.grado_id " +
        "JOIN ades_materias m ON m.id = h.materia_id " +
        "JOIN ades_profesores pr ON pr.id = h.profesor_id " +
        "JOIN ades_personas p2 ON p2.id = pr.persona_id " +
        "LEFT JOIN ades_aulas a ON a.id = h.aula_id ";

    private final JdbcTemplate jdbc;

    public HorarioQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> porGrupo(UUID grupoId, UUID cicloId) {
        StringBuilder where = new StringBuilder("WHERE h.grupo_id = ? AND h.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        params.add(grupoId);
        if (cicloId != null) {
            where.append(" AND h.ciclo_escolar_id = ?");
            params.add(cicloId);
        }
        where.append(" ORDER BY h.dia_semana, h.hora_inicio");
        return jdbc.queryForList(SELECT + where, params.toArray());
    }

    public List<Map<String, Object>> porProfesor(UUID profesorId, UUID cicloId) {
        StringBuilder where = new StringBuilder("WHERE h.profesor_id = ? AND h.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        params.add(profesorId);
        if (cicloId != null) {
            where.append(" AND h.ciclo_escolar_id = ?");
            params.add(cicloId);
        }
        where.append(" ORDER BY h.dia_semana, h.hora_inicio");
        return jdbc.queryForList(SELECT + where, params.toArray());
    }

    public List<Map<String, Object>> listar(UUID grupoId, UUID plantelId, UUID cicloId) {
        StringBuilder where = new StringBuilder("WHERE h.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        if (grupoId != null) { where.append(" AND h.grupo_id = ?"); params.add(grupoId); }
        if (plantelId != null) { where.append(" AND gr.plantel_id = ?"); params.add(plantelId); }
        if (cicloId != null) { where.append(" AND h.ciclo_escolar_id = ?"); params.add(cicloId); }
        where.append(" ORDER BY h.dia_semana, h.hora_inicio");
        return jdbc.queryForList(SELECT + where, params.toArray());
    }

    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + "WHERE h.id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> porCorrida(UUID corridaId) {
        return jdbc.queryForList(SELECT + "WHERE h.corrida_id = ? AND h.is_active = TRUE " +
            "ORDER BY h.dia_semana, h.hora_inicio", corridaId);
    }
}

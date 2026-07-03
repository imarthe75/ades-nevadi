package mx.ades.modules.planes_estudio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CQRS read-side de planes de estudio alternativos/reducidos (NEE) — AC-014. */
@Service
public class PlanAltQueryService {

    private final JdbcTemplate jdbc;

    public PlanAltQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarPorEstudiante(UUID estudianteId) {
        return jdbc.queryForList(
            "SELECT pa.id, pa.motivo, pa.estudiante_id, pa.grupo_id, pa.fecha_creacion " +
            "FROM ades_planes_estudio_alt pa WHERE pa.estudiante_id = ? AND pa.is_active = TRUE " +
            "ORDER BY pa.fecha_creacion DESC", estudianteId);
    }

    public List<Map<String, Object>> listarPorGrupo(UUID grupoId) {
        return jdbc.queryForList(
            "SELECT pa.id, pa.motivo, pa.estudiante_id, pa.grupo_id, pa.fecha_creacion " +
            "FROM ades_planes_estudio_alt pa WHERE pa.grupo_id = ? AND pa.is_active = TRUE " +
            "ORDER BY pa.fecha_creacion DESC", grupoId);
    }

    public List<Map<String, Object>> materias(UUID planAltId) {
        return jdbc.queryForList(
            "SELECT am.id, am.materia_id, m.nombre_materia, am.horas_semana " +
            "FROM ades_planes_estudio_alt_materias am " +
            "JOIN ades_materias m ON m.id = am.materia_id " +
            "WHERE am.plan_alt_id = ? AND am.is_active = TRUE " +
            "ORDER BY m.nombre_materia", planAltId);
    }
}

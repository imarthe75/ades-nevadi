package mx.ades.modules.grupos.query;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GrupoQueryService {

    private static final String SELECT =
        "SELECT g.id, g.nombre_grupo, g.grado_id, g.ciclo_escolar_id, g.capacidad_maxima, " +
        "  g.turno, g.is_active, g.profesor_titular_id, g.aula_id, " +
        "  gr.nombre_grado, gr.numero_grado, gr.plantel_id, " +
        "  ne.id AS nivel_id, ne.nombre_nivel, " +
        "  pl.nombre_plantel, pl.clave_ct, " +
        "  c.nombre_ciclo, c.es_vigente, " +
        "  COUNT(i.id) AS inscritos " +
        "FROM ades_grupos g " +
        "JOIN ades_grados gr ON gr.id = g.grado_id " +
        "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
        "JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
        "JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id " +
        "LEFT JOIN ades_inscripciones i ON i.grupo_id = g.id AND i.is_active = TRUE ";

    private static final String GROUP_BY =
        " GROUP BY g.id, gr.nombre_grado, gr.numero_grado, gr.plantel_id, " +
        "ne.id, ne.nombre_nivel, pl.nombre_plantel, pl.clave_ct, c.nombre_ciclo, c.es_vigente " +
        "ORDER BY ne.nombre_nivel, gr.numero_grado, g.nombre_grupo";

    private final JdbcTemplate jdbc;

    public GrupoQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listar(UUID plantelId, UUID cicloId, UUID gradoId, UUID nivelId,
                                             boolean soloActivos, boolean cicloVigente) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (soloActivos) where.append(" AND g.is_active = TRUE");
        if (plantelId != null) { where.append(" AND gr.plantel_id = ?"); params.add(plantelId); }
        if (cicloId != null) { where.append(" AND g.ciclo_escolar_id = ?"); params.add(cicloId); }
        if (gradoId != null) {
            where.append(" AND g.grado_id IN (SELECT id FROM ades_grados WHERE (numero_grado, nivel_educativo_id) = (SELECT numero_grado, nivel_educativo_id FROM ades_grados WHERE id = ?))");
            params.add(gradoId);
        }
        if (nivelId != null) { where.append(" AND gr.nivel_educativo_id = ?"); params.add(nivelId); }
        if (cicloVigente) where.append(" AND c.es_vigente = TRUE");
        return jdbc.queryForList(SELECT + where + GROUP_BY, params.toArray());
    }

    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            SELECT + " WHERE g.id = ? " + GROUP_BY, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        return rows.get(0);
    }
}

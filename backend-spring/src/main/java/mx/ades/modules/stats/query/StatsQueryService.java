package mx.ades.modules.stats.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatsQueryService {

    private final NamedParameterJdbcTemplate namedJdbc;

    public StatsQueryService(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    public Map<String, Object> resumen(UUID plantelId) {
        String sql = """
            SELECT
              (SELECT COUNT(*) FROM ades_estudiantes
               WHERE is_active = true
                 AND (:plantelId IS NULL OR plantel_id = :plantelId))        AS total_alumnos,
              (SELECT COUNT(*) FROM ades_profesores
               WHERE is_active = true
                 AND (:plantelId IS NULL OR plantel_id = :plantelId))        AS total_profesores,
              (SELECT COUNT(*) FROM ades_grupos g
               JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id
               JOIN ades_grados gr          ON gr.id = g.grado_id
               WHERE g.is_active = true AND c.es_vigente = true
                 AND (:plantelId IS NULL OR gr.plantel_id = :plantelId))     AS total_grupos_activos,
              (SELECT COUNT(*) FROM ades_clases
               WHERE fecha_clase = CURRENT_DATE)                             AS total_clases_hoy
            """;

        var params = new MapSqlParameterSource();
        params.addValue("plantelId", plantelId, java.sql.Types.OTHER);
        return namedJdbc.queryForMap(sql, params);
    }

    public List<Map<String, Object>> distribucion(UUID plantelId) {
        String sql = """
            SELECT
                n.nombre_nivel,
                COUNT(DISTINCT i.estudiante_id) AS total_alumnos,
                COUNT(DISTINCT g.id)             AS total_grupos
            FROM ades_niveles_educativos n
            LEFT JOIN ades_grados gr         ON gr.nivel_educativo_id = n.id
                                             AND (:plantelId IS NULL OR gr.plantel_id = :plantelId)
            LEFT JOIN ades_grupos g           ON g.grado_id = gr.id AND g.is_active = true
            LEFT JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id AND c.es_vigente = true
            LEFT JOIN ades_inscripciones i    ON i.grupo_id = g.id AND i.is_active = true
            WHERE n.is_active = true
            GROUP BY n.nombre_nivel
            ORDER BY n.nombre_nivel
            """;

        var params2 = new MapSqlParameterSource();
        params2.addValue("plantelId", plantelId, java.sql.Types.OTHER);
        return namedJdbc.queryForList(sql, params2);
    }

    public long databaseSizeBytes() {
        Long size = namedJdbc.queryForObject(
                "SELECT pg_database_size(current_database())",
                new MapSqlParameterSource(), Long.class);
        return size != null ? size : 0L;
    }
}

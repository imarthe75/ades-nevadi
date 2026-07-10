package mx.ades.modules.planes_estudio.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanesEstudioQueryService {

    private final JdbcTemplate jdbc;

    @Cacheable(value = "catalogos", key = "'plan_' + #cicloId + '_' + #gradoId + '_' + #nivelId")
    public List<Map<String, Object>> listar(UUID cicloId, UUID gradoId, UUID nivelId) {
        StringBuilder sql = new StringBuilder("""
            SELECT mp.id, mp.materia_id, mp.grado_id, mp.ciclo_escolar_id,
                   mp.horas_semana, mp.es_obligatoria, mp.orden, mp.is_active,
                   m.nombre_materia, m.clave_materia,
                   g.nombre_grado, g.numero_grado,
                   ce.nombre_ciclo,
                   ne.id AS nivel_educativo_id, ne.nombre_nivel
            FROM ades_materias_plan mp
            JOIN ades_materias m ON m.id = mp.materia_id
            JOIN ades_grados g ON g.id = mp.grado_id
            JOIN ades_ciclos_escolares ce ON ce.id = mp.ciclo_escolar_id
            JOIN ades_niveles_educativos ne ON ne.id = g.nivel_educativo_id
            WHERE mp.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        if (cicloId != null) {
            sql.append(" AND mp.ciclo_escolar_id = ?");
            params.add(cicloId);
        }
        if (gradoId != null) {
            sql.append(" AND mp.grado_id IN (SELECT id FROM ades_grados WHERE (numero_grado, nivel_educativo_id) = (SELECT numero_grado, nivel_educativo_id FROM ades_grados WHERE id = ?))");
            params.add(gradoId);
        }
        if (nivelId != null) {
            sql.append(" AND g.nivel_educativo_id = ?");
            params.add(nivelId);
        }
        sql.append(" ORDER BY g.numero_grado, mp.orden, m.nombre_materia");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}

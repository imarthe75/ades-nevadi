package mx.ades.modules.evaluaciones.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.RubricaCriterio;
import mx.ades.modules.evaluaciones.RubricaCriterioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RubricaQueryService {

    private final JdbcTemplate jdbc;
    private final RubricaCriterioRepository criterioRepository;

    public List<Map<String, Object>> listar(UUID materiaId, UUID nivelEducativoId, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT r.id, r.nombre_rubrica, r.descripcion, " +
            "r.materia_id, m.nombre_materia, " +
            "r.nivel_educativo_id, ne.nombre_nivel, " +
            "COUNT(rc.id) AS total_criterios, " +
            "ROUND(COALESCE(SUM(rc.ponderacion), 0), 1) AS ponderacion_total " +
            "FROM ades_rubricas r " +
            "LEFT JOIN ades_materias m ON m.id = r.materia_id " +
            "LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id " +
            "LEFT JOIN ades_rubrica_criterios rc ON rc.rubrica_id = r.id AND rc.is_active = TRUE " +
            "WHERE r.is_active = TRUE");

        List<Object> params = new ArrayList<>();
        if (materiaId != null) {
            sql.append(" AND r.materia_id = ?");
            params.add(materiaId);
        }
        if (nivelEducativoId != null) {
            sql.append(" AND r.nivel_educativo_id = ?");
            params.add(nivelEducativoId);
        }
        sql.append(" GROUP BY r.id, m.nombre_materia, ne.nombre_nivel");
        sql.append(" ORDER BY r.fecha_creacion DESC LIMIT ?");
        params.add(limit);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> detalle(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.*, m.nombre_materia, ne.nombre_nivel " +
            "FROM ades_rubricas r " +
            "LEFT JOIN ades_materias m ON m.id = r.materia_id " +
            "LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id " +
            "WHERE r.id = ? AND r.is_active = TRUE", id);
        if (rows.isEmpty()) return null;
        Map<String, Object> rubrica = rows.get(0);
        List<RubricaCriterio> criterios = criterioRepository.findByRubricaIdAndIsActiveTrueOrderByOrdenAsc(id);
        rubrica.put("criterios", criterios);
        return rubrica;
    }
}

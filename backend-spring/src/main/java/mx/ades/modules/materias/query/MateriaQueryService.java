package mx.ades.modules.materias.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}

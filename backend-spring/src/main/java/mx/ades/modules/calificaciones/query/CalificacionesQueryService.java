package mx.ades.modules.calificaciones.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de consulta (lado lectura CQRS) para el módulo calificaciones.
 *
 * <p>Provee el listado de períodos de evaluación ({@code ades_periodos_evaluacion})
 * con filtro opcional por ciclo escolar.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class CalificacionesQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> periodos(UUID cicloId) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id, " +
            "fecha_inicio, fecha_fin, fecha_entrega_boletas " +
            "FROM ades_periodos_evaluacion WHERE is_active = TRUE");
        Object[] params;
        if (cicloId != null) {
            sql.append(" AND ciclo_escolar_id = ?");
            params = new Object[]{cicloId};
        } else {
            params = new Object[0];
        }
        sql.append(" ORDER BY numero_periodo");
        return jdbc.queryForList(sql.toString(), params);
    }
}

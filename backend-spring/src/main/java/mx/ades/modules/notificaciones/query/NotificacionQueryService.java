package mx.ades.modules.notificaciones.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NotificacionQueryService {

    private final JdbcTemplate jdbc;

    public NotificacionQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> misNotificaciones(UUID userId, boolean soloNoLeidas, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, titulo, mensaje AS cuerpo, tipo, leido, fecha_creacion " +
                "FROM ades_notificaciones_sistema " +
                "WHERE usuario_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (soloNoLeidas) {
            sql.append("AND leido = FALSE ");
        }
        sql.append("ORDER BY fecha_creacion DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        rows.forEach(r -> { if (r.get("id") != null) r.put("id", r.get("id").toString()); });
        return rows;
    }

    public long contarNoLeidas(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_notificaciones_sistema WHERE usuario_id = ? AND leido = FALSE",
                Long.class, userId);
        return count != null ? count : 0L;
    }
}

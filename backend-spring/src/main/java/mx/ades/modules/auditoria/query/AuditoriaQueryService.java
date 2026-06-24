package mx.ades.modules.auditoria.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de consulta (lado lectura CQRS) para el módulo auditoria.
 *
 * <p>Consulta el log de auditoría en {@code ades_audit_log} con filtros por entidad,
 * acción y usuario, limitando el número de resultados para paginación ligera.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class AuditoriaQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listar(String entidad, String accion, UUID usuarioId, int limite) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, usuario_id, nombre_usuario, ip_origen, accion, entidad, entidad_id, " +
            "endpoint, metodo_http, codigo_respuesta, duracion_ms, fecha_creacion, row_version " +
            "FROM ades_audit_log WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (entidad != null && !entidad.isBlank()) {
            sql.append("AND entidad = ? ");
            params.add(entidad);
        }
        if (accion != null && !accion.isBlank()) {
            sql.append("AND accion = ? ");
            params.add(accion);
        }
        if (usuarioId != null) {
            sql.append("AND usuario_id = ? ");
            params.add(usuarioId);
        }
        sql.append("ORDER BY fecha_creacion DESC LIMIT ?");
        params.add(limite);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}

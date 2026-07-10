package mx.ades.modules.comunicados.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio de lectura CQRS para el módulo comunicados.
 * <p>Expone consultas de listado, detalle, comunicados recurrentes pendientes
 * y reporte de lectura con porcentaje de acuses.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
public class ComunicadoQueryService {

    private final JdbcTemplate jdbc;

    public ComunicadoQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listar(UUID userId, UUID plantelId, UUID nivelEducativoId,
                                             String tipo, boolean soloVigentes, int limit) {
        StringBuilder q = new StringBuilder(
                "SELECT c.id, c.titulo, c.contenido, c.tipo_comunicado, " +
                "c.plantel_id, c.nivel_educativo_id, c.grupo_id, c.requiere_acuse, " +
                "c.fecha_publicacion, c.fecha_vencimiento, " +
                "COUNT(a.id) FILTER (WHERE a.id IS NOT NULL) AS total_acuses, " +
                "BOOL_OR(a.usuario_id = ?) AS acusado_por_mi, " +
                "u.nombre_usuario AS creado_por_nombre " +
                "FROM ades_comunicados c " +
                "LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id " +
                "LEFT JOIN ades_usuarios u ON u.id = c.creado_por_id " +
                "WHERE c.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (plantelId != null) { q.append("AND (c.plantel_id IS NULL OR c.plantel_id = ?) "); params.add(plantelId); }
        if (nivelEducativoId != null) { q.append("AND (c.nivel_educativo_id IS NULL OR c.nivel_educativo_id = ?) "); params.add(nivelEducativoId); }
        if (tipo != null && !tipo.isBlank()) { q.append("AND c.tipo_comunicado = ? "); params.add(tipo); }
        if (soloVigentes) { q.append("AND (c.fecha_vencimiento IS NULL OR c.fecha_vencimiento > NOW()) "); }

        q.append("GROUP BY c.id, u.nombre_usuario ORDER BY c.fecha_publicacion DESC LIMIT ?");
        params.add(limit);

        return jdbc.queryForList(q.toString(), params.toArray());
    }

    public Optional<Map<String, Object>> detalle(UUID id, UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.*, " +
                "COUNT(a.id) AS total_acuses, " +
                "BOOL_OR(a.usuario_id = ?) AS acusado_por_mi " +
                "FROM ades_comunicados c " +
                "LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id " +
                "WHERE c.id = ? AND c.is_active = TRUE " +
                "GROUP BY c.id",
                userId, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> recurrentesPendientes(int pagina, int porPagina) {
        return jdbc.queryForList(
                "SELECT id, titulo, tipo_comunicado, periodicidad, proximo_envio, " +
                "fecha_publicacion, plantel_id, nivel_educativo_id " +
                "FROM ades_comunicados WHERE es_recurrente = TRUE AND is_active = TRUE " +
                "ORDER BY proximo_envio ASC NULLS LAST LIMIT ? OFFSET ?",
                porPagina, (pagina - 1) * porPagina);
    }

    public Map<String, Object> reporteLectura(UUID id, String titulo, long totalDestinatarios) {
        Map<String, Object> acuses = jdbc.queryForMap(
                "SELECT COUNT(*) AS leidos, COUNT(DISTINCT ac.usuario_id) AS usuarios_distintos " +
                "FROM ades_acuses_comunicado ac WHERE ac.comunicado_id = ? AND ac.is_active = TRUE", id);

        long leidos = ((Number) acuses.get("leidos")).longValue();
        double pct = totalDestinatarios > 0
                ? Math.round(((double) leidos / totalDestinatarios * 100.0) * 10.0) / 10.0 : 0.0;

        List<Map<String, Object>> detalle = jdbc.queryForList(
                "SELECT u.nombre_usuario, ac.fecha_acuse, ac.ip_origen " +
                "FROM ades_acuses_comunicado ac JOIN ades_usuarios u ON u.id = ac.usuario_id " +
                "WHERE ac.comunicado_id = ? AND ac.is_active = TRUE ORDER BY ac.fecha_acuse DESC LIMIT 200", id);

        Map<String, Object> result = new HashMap<>();
        result.put("comunicado_id", id);
        result.put("titulo", titulo);
        result.put("total_destinatarios", totalDestinatarios);
        result.put("total_leidos", leidos);
        result.put("pct_lectura", pct);
        result.put("detalle", detalle);
        return result;
    }
}

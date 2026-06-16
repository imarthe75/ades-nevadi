package mx.ades.modules.geo.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeoQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> estados() {
        try {
            return jdbc.queryForList(
                "SELECT id, clave, nombre FROM sepomex.ctestados WHERE vigente = TRUE ORDER BY nombre");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> municipios(int estadoId) {
        try {
            return jdbc.queryForList(
                "SELECT id, clave, nombre FROM sepomex.ctmunicipios WHERE estado_id = ? AND vigente = TRUE ORDER BY nombre",
                estadoId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> coloniasPorCp(String cp) {
        try {
            return jdbc.queryForList(
                "SELECT a.id, a.nombre AS colonia, cp.codigo_postal, m.nombre AS municipio, e.nombre AS estado " +
                "FROM sepomex.ctasentamientos a " +
                "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                "JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id " +
                "JOIN sepomex.ctestados e ON e.id = m.estado_id " +
                "WHERE cp.codigo_postal = ? AND a.vigente = TRUE ORDER BY a.nombre", cp);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> coloniasPorMunicipio(int municipioId) {
        try {
            return jdbc.queryForList(
                "SELECT a.id, a.nombre AS colonia, cp.codigo_postal " +
                "FROM sepomex.ctasentamientos a " +
                "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                "WHERE a.municipio_id = ? AND a.vigente = TRUE ORDER BY a.nombre", municipioId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public Map<String, Object> buscarPorCp(String cp) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.nombre AS estado, e.id AS estado_id, m.nombre AS municipio, m.id AS municipio_id, " +
                "cp.codigo_postal AS cp, " +
                "json_agg(json_build_object('id', a.id, 'colonia', a.nombre) ORDER BY a.nombre) AS colonias " +
                "FROM sepomex.ctasentamientos a " +
                "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                "JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id " +
                "JOIN sepomex.ctestados e ON e.id = m.estado_id " +
                "WHERE cp.codigo_postal = ? AND a.vigente = TRUE " +
                "GROUP BY e.nombre, e.id, m.nombre, m.id, cp.codigo_postal", cp);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}

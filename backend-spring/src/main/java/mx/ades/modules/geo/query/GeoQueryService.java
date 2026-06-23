package mx.ades.modules.geo.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.geo.domain.port.out.GeoQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultas geográficas SEPOMEX sobre las tablas reales {@code public.ades_*}.
 * (El esquema legacy {@code sepomex.ct*} nunca existió en esta BD; el catálogo
 *  poblado vive en ades_estados / ades_municipios / ades_localidades /
 *  ades_codigos_postales — ver task Celery sepomex.sync_sepomex_weekly y mig 054.)
 */
@Service
@RequiredArgsConstructor
public class GeoQueryService implements GeoQueryPort {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> estados() {
        try {
            return jdbc.queryForList(
                "SELECT id, nombre_estado AS nombre, clave_estado AS clave " +
                "FROM ades_estados WHERE is_active = TRUE ORDER BY nombre_estado");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> municipios(UUID estadoId) {
        try {
            return jdbc.queryForList(
                "SELECT id, nombre_municipio AS nombre, clave_municipio AS clave " +
                "FROM ades_municipios WHERE estado_id = ? AND is_active = TRUE ORDER BY nombre_municipio",
                estadoId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> coloniasPorCp(String cp) {
        try {
            return jdbc.queryForList(
                "SELECT l.id, l.nombre_localidad AS colonia, cp.codigo_postal, " +
                "       m.nombre_municipio AS municipio, e.nombre_estado AS estado " +
                "FROM ades_codigos_postales cp " +
                "JOIN ades_localidades l ON l.id = cp.localidad_id " +
                "JOIN ades_municipios m ON m.id = cp.municipio_id " +
                "JOIN ades_estados e ON e.id = cp.estado_id " +
                "WHERE cp.codigo_postal = ? ORDER BY l.nombre_localidad", cp);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> coloniasPorMunicipio(UUID municipioId) {
        try {
            return jdbc.queryForList(
                "SELECT DISTINCT l.id, l.nombre_localidad AS colonia, cp.codigo_postal " +
                "FROM ades_codigos_postales cp " +
                "JOIN ades_localidades l ON l.id = cp.localidad_id " +
                "WHERE cp.municipio_id = ? ORDER BY l.nombre_localidad", municipioId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public Map<String, Object> buscarPorCp(String cp) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT e.nombre_estado AS estado, e.id AS estado_id, " +
                "       m.nombre_municipio AS municipio, m.id AS municipio_id, " +
                "       cp.codigo_postal AS cp " +
                "FROM ades_codigos_postales cp " +
                "JOIN ades_municipios m ON m.id = cp.municipio_id " +
                "JOIN ades_estados e ON e.id = cp.estado_id " +
                "WHERE cp.codigo_postal = ? LIMIT 1", cp);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> result = rows.get(0);
            // colonias como List Java real → Jackson la serializa como array JSON
            result.put("colonias", coloniasPorCp(cp).stream()
                .map(r -> Map.of("id", r.get("id"), "colonia", r.get("colonia")))
                .toList());
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}

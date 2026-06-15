package mx.ades.modules.geo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {

    private final JdbcTemplate jdbc;

    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> listarEstados() {
        try {
            String sql = "SELECT id, clave, nombre FROM sepomex.ctestados WHERE vigente = TRUE ORDER BY nombre";
            return ResponseEntity.ok(jdbc.queryForList(sql));
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/municipios")
    public ResponseEntity<List<Map<String, Object>>> listarMunicipios(
            @RequestParam("estado_id") int estadoId) {
        try {
            String sql = "SELECT id, clave, nombre FROM sepomex.ctmunicipios WHERE estado_id = ? AND vigente = TRUE ORDER BY nombre";
            return ResponseEntity.ok(jdbc.queryForList(sql, estadoId));
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/colonias")
    public ResponseEntity<List<Map<String, Object>>> buscarColonias(
            @RequestParam(value = "cp", required = false) String cp,
            @RequestParam(value = "municipio_id", required = false) Integer municipioId) {
        try {
            if (cp != null && !cp.isBlank()) {
                String sql = "SELECT a.id, a.nombre AS colonia, cp.codigo_postal, m.nombre AS municipio, e.nombre AS estado " +
                        "FROM sepomex.ctasentamientos a " +
                        "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                        "JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id " +
                        "JOIN sepomex.ctestados e ON e.id = m.estado_id " +
                        "WHERE cp.codigo_postal = ? AND a.vigente = TRUE " +
                        "ORDER BY a.nombre";
                return ResponseEntity.ok(jdbc.queryForList(sql, cp));
            } else if (municipioId != null) {
                String sql = "SELECT a.id, a.nombre AS colonia, cp.codigo_postal " +
                        "FROM sepomex.ctasentamientos a " +
                        "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                        "WHERE a.municipio_id = ? AND a.vigente = TRUE " +
                        "ORDER BY a.nombre";
                return ResponseEntity.ok(jdbc.queryForList(sql, municipioId));
            } else {
                return ResponseEntity.ok(Collections.emptyList());
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/buscar-cp/{cp}")
    public ResponseEntity<Map<String, Object>> buscarPorCp(
            @PathVariable("cp") String cp) {
        try {
            String sql = "SELECT e.nombre AS estado, e.id AS estado_id, m.nombre AS municipio, m.id AS municipio_id, cp.codigo_postal AS cp, " +
                    "json_agg(json_build_object('id', a.id, 'colonia', a.nombre) ORDER BY a.nombre) AS colonias " +
                    "FROM sepomex.ctasentamientos a " +
                    "JOIN sepomex.ctcodigospostales cp ON cp.id = a.codigo_postal_id " +
                    "JOIN sepomex.ctmunicipios m ON m.id = a.municipio_id " +
                    "JOIN sepomex.ctestados e ON e.id = m.estado_id " +
                    "WHERE cp.codigo_postal = ? AND a.vigente = TRUE " +
                    "GROUP BY e.nombre, e.id, m.nombre, m.id, cp.codigo_postal";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, cp);
            if (rows.isEmpty()) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(rows.get(0));
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }
}

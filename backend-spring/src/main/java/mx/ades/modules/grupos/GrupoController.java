package mx.ades.modules.grupos;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    private static final String GRUPO_SELECT =
            "SELECT g.id, g.nombre_grupo, g.grado_id, g.ciclo_escolar_id, g.capacidad_maxima, " +
            "  g.turno, g.is_active, g.profesor_titular_id, g.aula_id, " +
            "  gr.nombre_grado, gr.numero_grado, gr.plantel_id, " +
            "  ne.id AS nivel_id, ne.nombre_nivel, " +
            "  pl.nombre_plantel, pl.clave_ct, " +
            "  c.nombre_ciclo, c.es_vigente, " +
            "  COUNT(i.id) AS inscritos " +
            "FROM ades_grupos g " +
            "JOIN ades_grados gr ON gr.id = g.grado_id " +
            "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
            "JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
            "JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id " +
            "LEFT JOIN ades_inscripciones i ON i.grupo_id = g.id AND i.is_active = TRUE ";

    private static final String GRUPO_GROUP =
            " GROUP BY g.id, gr.nombre_grado, gr.numero_grado, gr.plantel_id, " +
            "ne.id, ne.nombre_nivel, pl.nombre_plantel, pl.clave_ct, c.nombre_ciclo, c.es_vigente " +
            "ORDER BY ne.nombre_nivel, gr.numero_grado, g.nombre_grupo";

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "solo_activos", required = false, defaultValue = "true") boolean soloActivos,
            @RequestParam(name = "ciclo_vigente", required = false, defaultValue = "false") boolean cicloVigente) {

        userService.resolveUser(jwt);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (soloActivos) {
            where.append(" AND g.is_active = TRUE");
        }
        if (plantelId != null) {
            where.append(" AND gr.plantel_id = ?");
            params.add(plantelId);
        }
        if (cicloId != null) {
            where.append(" AND g.ciclo_escolar_id = ?");
            params.add(cicloId);
        }
        if (cicloVigente) {
            where.append(" AND c.es_vigente = TRUE");
        }

        return ResponseEntity.ok(jdbc.queryForList(
                GRUPO_SELECT + where + GRUPO_GROUP, params.toArray()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList(
                GRUPO_SELECT + " WHERE g.id = ? " + GRUPO_GROUP, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }
}

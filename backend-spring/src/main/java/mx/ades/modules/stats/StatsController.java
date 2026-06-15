package mx.ades.modules.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final JdbcTemplate jdbc;

    @GetMapping("/resumen")
    public Map<String, Object> resumen(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {

        UUID pid = resolveUUID(jwt.getClaimAsString("plantel"), plantelId);

        String sql = """
            SELECT
              (SELECT COUNT(*) FROM ades_estudiantes
               WHERE is_active = true
                 AND (:pid::uuid IS NULL OR plantel_id = :pid::uuid))        AS total_alumnos,
              (SELECT COUNT(*) FROM ades_profesores
               WHERE is_active = true
                 AND (:pid::uuid IS NULL OR plantel_id = :pid::uuid))        AS total_profesores,
              (SELECT COUNT(*) FROM ades_grupos g
               JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id
               JOIN ades_grados gr          ON gr.id = g.grado_id
               WHERE g.is_active = true AND c.es_vigente = true
                 AND (:pid::uuid IS NULL OR gr.plantel_id = :pid::uuid))     AS total_grupos_activos,
              (SELECT COUNT(*) FROM ades_clases
               WHERE fecha_clase = CURRENT_DATE)                             AS total_clases_hoy
            """;

        return jdbc.queryForMap(sql.replace(":pid", pid != null ? "'" + pid + "'" : "NULL"));
    }

    @GetMapping("/distribucion")
    public List<Map<String, Object>> distribucion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {

        UUID pid = resolveUUID(jwt.getClaimAsString("plantel"), plantelId);
        String pidFilter = pid != null ? "AND gr.plantel_id = '" + pid + "'::uuid" : "";

        String sql = """
            SELECT
                n.nombre_nivel,
                COUNT(DISTINCT i.estudiante_id) AS total_alumnos,
                COUNT(DISTINCT g.id)             AS total_grupos
            FROM ades_niveles_educativos n
            LEFT JOIN ades_grados gr         ON gr.nivel_educativo_id = n.id %s
            LEFT JOIN ades_grupos g           ON g.grado_id = gr.id AND g.is_active = true
            LEFT JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id AND c.es_vigente = true
            LEFT JOIN ades_inscripciones i    ON i.grupo_id = g.id AND i.is_active = true
            WHERE n.is_active = true
            GROUP BY n.nombre_nivel
            ORDER BY n.nombre_nivel
            """.formatted(pidFilter);

        return jdbc.queryForList(sql);
    }

    @GetMapping("/servidor")
    public Map<String, Object> servidor(@AuthenticationPrincipal Jwt jwt) {
        Object nivel = jwt.getClaim("nivel_acceso");
        int nivelAcceso = nivel instanceof Number n ? n.intValue() : 99;
        if (nivelAcceso > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo directores y administradores pueden ver la telemetría");
        }

        Long dbSize = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);

        return Map.of(
                "database_size_bytes", dbSize != null ? dbSize : 0L,
                "database_size_mb", dbSize != null ? dbSize / 1_048_576.0 : 0.0
        );
    }

    private UUID resolveUUID(String claim, UUID fallback) {
        if (claim != null && !claim.isBlank()) {
            try { return UUID.fromString(claim); } catch (IllegalArgumentException ignored) {}
        }
        return fallback;
    }
}

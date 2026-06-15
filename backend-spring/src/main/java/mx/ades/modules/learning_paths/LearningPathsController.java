package mx.ades.modules.learning_paths;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/learning-paths")
@RequiredArgsConstructor
public class LearningPathsController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/learning-paths";

    @Data
    public static class PathRequest {
        private String nombre;
        private String descripcion;
        private UUID nivelEducativoId;
        private UUID materiaId;
        private String criterioActivacion;
        private Double umbralActivacion;
    }

    @Data
    public static class RecursoRequest {
        private Integer orden;
        private String tipo;
        private String titulo;
        private String descripcion;
        private String urlRecurso;
        private Integer duracionMin;
        private Boolean obligatorio;
    }

    @Data
    public static class AsignacionRequest {
        private UUID estudianteId;
        private String motivo;
    }

    @Data
    public static class ProgresoRequest {
        private Integer tiempoMin;
        private Double calificacion;
    }

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarPaths(
            @RequestParam(value = "activos", required = false) Boolean activos,
            @RequestParam(value = "criterio", required = false) String criterio,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT lp.id, lp.nombre, lp.descripcion, lp.criterio_activacion, " +
                "lp.umbral_activacion, lp.is_active, " +
                "COUNT(r.id) AS total_recursos " +
                "FROM ades_learning_paths lp " +
                "LEFT JOIN ades_lp_recursos r ON r.path_id = lp.id AND r.is_active = TRUE " +
                "WHERE (? IS NULL OR lp.is_active = ?) " +
                "AND (? IS NULL OR lp.criterio_activacion = ?) " +
                "GROUP BY lp.id " +
                "ORDER BY lp.nombre";

        return ResponseEntity.ok(jdbc.queryForList(sql, activos, activos, criterio, criterio));
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> crearPath(
            @RequestBody PathRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_learning_paths " +
                "(id, nombre, descripcion, nivel_educativo_id, materia_id, " +
                "criterio_activacion, umbral_activacion, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)",
                id, body.getNombre(), body.getDescripcion(), body.getNivelEducativoId(),
                body.getMateriaId(), body.getCriterioActivacion(), body.getUmbralActivacion()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("nombre", body.getNombre());
        response.put("descripcion", body.getDescripcion());
        response.put("criterio_activacion", body.getCriterioActivacion());
        response.put("umbral_activacion", body.getUmbralActivacion());
        response.put("is_active", true);
        response.put("total_recursos", 0);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{path_id}")
    public ResponseEntity<Map<String, Object>> detallePath(
            @PathVariable("path_id") UUID pathId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> pathRows = jdbc.queryForList(
                "SELECT id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active " +
                "FROM ades_learning_paths WHERE id = ?", pathId);
        if (pathRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");
        }
        Map<String, Object> path = new HashMap<>(pathRows.get(0));

        List<Map<String, Object>> recursos = jdbc.queryForList(
                "SELECT id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active " +
                "FROM ades_lp_recursos WHERE path_id = ? AND is_active = TRUE ORDER BY orden", pathId);

        path.put("total_recursos", recursos.size());
        path.put("recursos", recursos);

        return ResponseEntity.ok(path);
    }

    @PostMapping("/{path_id}/recursos")
    public ResponseEntity<Map<String, Object>> agregarRecurso(
            @PathVariable("path_id") UUID pathId,
            @RequestBody RecursoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> pathRows = jdbc.queryForList("SELECT id FROM ades_learning_paths WHERE id = ?", pathId);
        if (pathRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_lp_recursos " +
                "(id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, pathId, body.getOrden(), body.getTipo(), body.getTitulo(),
                body.getDescripcion(), body.getUrlRecurso(), body.getDuracionMin(), body.getObligatorio()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("orden", body.getOrden());
        response.put("tipo", body.getTipo());
        response.put("titulo", body.getTitulo());
        response.put("descripcion", body.getDescripcion());
        response.put("url_recurso", body.getUrlRecurso());
        response.put("duracion_min", body.getDuracionMin());
        response.put("obligatorio", body.getObligatorio());
        response.put("is_active", true);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/asignaciones")
    public ResponseEntity<List<Map<String, Object>>> listarAsignaciones(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "estatus", required = false) String estatus,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT a.id, a.path_id, lp.nombre AS path_nombre, " +
                "a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion " +
                "FROM ades_lp_asignaciones a " +
                "JOIN ades_learning_paths lp ON lp.id = a.path_id " +
                "WHERE (? IS NULL OR a.estudiante_id = ?) " +
                "AND (? IS NULL OR a.estatus = ?) " +
                "ORDER BY a.fecha_creacion DESC";

        return ResponseEntity.ok(jdbc.queryForList(sql, estudianteId, estudianteId, estatus, estatus));
    }

    @PostMapping("/{path_id}/asignar")
    public ResponseEntity<Map<String, Object>> asignarPath(
            @PathVariable("path_id") UUID pathId,
            @RequestBody AsignacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> pathRows = jdbc.queryForList("SELECT nombre FROM ades_learning_paths WHERE id = ?", pathId);
        if (pathRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");
        }
        String pathNombre = (String) pathRows.get(0).get("nombre");

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_lp_asignaciones " +
                "(id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW()) " +
                "ON CONFLICT (path_id, estudiante_id) DO UPDATE " +
                "SET estatus = EXCLUDED.estatus, fecha_modificacion = NOW()",
                id, pathId, body.getEstudianteId(), user.getId(), body.getMotivo()
        );

        List<Map<String, Object>> asigRows = jdbc.queryForList(
                "SELECT id, path_id, estudiante_id, motivo, estatus, pct_completado, fecha_creacion " +
                "FROM ades_lp_asignaciones WHERE path_id = ? AND estudiante_id = ? LIMIT 1", pathId, body.getEstudianteId());
        
        Map<String, Object> response = new HashMap<>(asigRows.get(0));
        response.put("path_nombre", pathNombre);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/asignaciones/{asig_id}")
    public ResponseEntity<Map<String, Object>> detalleAsignacion(
            @PathVariable("asig_id") UUID asigId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> asigRows = jdbc.queryForList(
                "SELECT a.id, a.path_id, lp.nombre AS path_nombre, " +
                "a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fecha_creacion " +
                "FROM ades_lp_asignaciones a " +
                "JOIN ades_learning_paths lp ON lp.id = a.path_id " +
                "WHERE a.id = ?", asigId);
        if (asigRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada");
        }
        Map<String, Object> response = new HashMap<>(asigRows.get(0));

        List<Map<String, Object>> progresoRows = jdbc.queryForList(
                "SELECT p.recurso_id, r.titulo, r.tipo, r.orden, " +
                "p.completado, p.tiempo_min, p.calificacion, p.fccompletado " +
                "FROM ades_lp_progreso p " +
                "JOIN ades_lp_recursos r ON r.id = p.recurso_id " +
                "WHERE p.asignacion_id = ? " +
                "ORDER BY r.orden", asigId);

        Integer totalRecursos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_lp_recursos " +
                "WHERE path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?) AND is_active = TRUE",
                Integer.class, asigId);

        long completados = progresoRows.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("completado")))
                .count();

        List<Map<String, Object>> progreso = new ArrayList<>();
        for (Map<String, Object> p : progresoRows) {
            Map<String, Object> pData = new HashMap<>();
            pData.put("recurso_id", p.get("recurso_id").toString());
            pData.put("titulo", p.get("titulo"));
            pData.put("tipo", p.get("tipo"));
            pData.put("orden", p.get("orden"));
            pData.put("completado", p.get("completado"));
            pData.put("tiempo_min", p.get("tiempo_min"));
            pData.put("calificacion", p.get("calificacion"));
            pData.put("fccompletado", p.get("fccompletado") != null ? p.get("fccompletado").toString() : null);
            progreso.add(pData);
        }

        response.put("recursos_completados", completados);
        response.put("total_recursos", totalRecursos != null ? totalRecursos : 0);
        response.put("progreso", progreso);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/asignaciones/{asig_id}/progreso/{recurso_id}")
    public ResponseEntity<Map<String, String>> registrarProgreso(
            @PathVariable("asig_id") UUID asigId,
            @PathVariable("recurso_id") UUID recursoId,
            @RequestBody ProgresoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        jdbc.update(
                "INSERT INTO ades_lp_progreso " +
                "(asignacion_id, recurso_id, completado, tiempo_min, calificacion, fccompletado) " +
                "VALUES (?, ?, TRUE, ?, ?, NOW()) " +
                "ON CONFLICT (asignacion_id, recurso_id) DO UPDATE " +
                "SET completado = TRUE, " +
                "tiempo_min = COALESCE(EXCLUDED.tiempo_min, ades_lp_progreso.tiempo_min), " +
                "calificacion = COALESCE(EXCLUDED.calificacion, ades_lp_progreso.calificacion), " +
                "fccompletado = NOW()",
                asigId, recursoId, body.getTiempoMin(), body.getCalificacion()
        );

        jdbc.update(
                "UPDATE ades_lp_asignaciones " +
                "SET pct_completado = ( " +
                "        SELECT ROUND( " +
                "            100.0 * COUNT(CASE WHEN pr.completado THEN 1 END) " +
                "            / NULLIF((SELECT COUNT(*) FROM ades_lp_recursos r2 " +
                "                       WHERE r2.path_id = a.path_id AND r2.is_active = TRUE), 0) " +
                "        , 1) " +
                "          FROM ades_lp_progreso pr " +
                "          JOIN ades_lp_asignaciones a ON a.id = pr.asignacion_id " +
                "         WHERE pr.asignacion_id = ? " +
                "    ), " +
                "    estatus = CASE " +
                "        WHEN ( " +
                "            SELECT COUNT(CASE WHEN pr2.completado THEN 1 END) " +
                "              FROM ades_lp_progreso pr2 " +
                "             WHERE pr2.asignacion_id = ? " +
                "        ) >= ( " +
                "            SELECT COUNT(*) FROM ades_lp_recursos r3 " +
                "             WHERE r3.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?) " +
                "               AND r3.is_active = TRUE AND r3.obligatorio = TRUE " +
                "        ) THEN 'COMPLETADO' " +
                "        ELSE 'EN_PROGRESO' " +
                "    END, " +
                "    fccompletado = CASE " +
                "        WHEN estatus = 'COMPLETADO' THEN NOW() ELSE fccompletado END, " +
                "    fecha_modificacion = NOW() " +
                "WHERE id = ?",
                asigId, asigId, asigId, asigId
        );

        return ResponseEntity.ok(Map.of("mensaje", "Progreso registrado"));
    }

    @PostMapping("/asignar-automatico/{grupo_id}")
    public ResponseEntity<Map<String, Object>> asignarAutomatico(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> alertas = jdbc.queryForList(
                "SELECT a.estudiante_id, a.tipo_alerta " +
                "FROM ades_alertas_academicas a " +
                "WHERE a.grupo_id = ? AND a.atendida = FALSE", grupoId);

        if (alertas.isEmpty()) {
            return ResponseEntity.ok(Map.of("asignadas", 0, "mensaje", "Sin alertas activas en el grupo"));
        }

        List<Map<String, Object>> paths = jdbc.queryForList(
                "SELECT id, criterio_activacion FROM ades_learning_paths " +
                "WHERE criterio_activacion IN ('REPROBACION', 'AUSENTISMO', 'RIESGO_ALTO') " +
                "AND is_active = TRUE");

        Map<String, String> tipoACriterio = new HashMap<>();
        tipoACriterio.put("RIESGO_REPROBACION", "REPROBACION");
        tipoACriterio.put("AUSENTISMO_CRITICO", "AUSENTISMO");

        Map<String, UUID> pathPorCriterio = new HashMap<>();
        for (Map<String, Object> p : paths) {
            pathPorCriterio.put((String) p.get("criterio_activacion"), (UUID) p.get("id"));
        }

        int asignadas = 0;
        for (Map<String, Object> alerta : alertas) {
            String criterio = tipoACriterio.get((String) alerta.get("tipo_alerta"));
            if (criterio == null) continue;
            UUID pathId = pathPorCriterio.get(criterio);
            if (pathId == null) continue;

            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO ades_lp_asignaciones " +
                    "(id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio) " +
                    "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW()) " +
                    "ON CONFLICT (path_id, estudiante_id) DO NOTHING",
                    id, pathId, (UUID) alerta.get("estudiante_id"), user.getId(), "AUTO_" + alerta.get("tipo_alerta")
            );
            asignadas++;
        }

        return ResponseEntity.ok(Map.of("asignadas", asignadas, "grupo_id", grupoId.toString()));
    }

    @PostMapping("/asignaciones/{asig_id}/recomendar-ia")
    public ResponseEntity<Map<String, Object>> recomendarIa(
            @PathVariable("asig_id") UUID asigId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/asignaciones/" + asigId + "/recomendar-ia");
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al recomendar IA en microservicio FastAPI: " + e.getMessage());
        }
    }

    @PostMapping("/ajustar-dinamico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> ajustarDinamico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/ajustar-dinamico/" + estudianteId);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al ajustar dinámicamente en microservicio FastAPI: " + e.getMessage());
        }
    }
}

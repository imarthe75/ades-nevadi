package mx.ades.modules.learning_paths;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.query.LearningPathQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/learning-paths")
@RequiredArgsConstructor
public class LearningPathsController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RegistrarProgresoUseCase registrarProgreso;
    private final LearningPathQueryService queryService;

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

    // ── READS (delegadas a QueryService) ─────────────────────────────────────

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarPaths(
            @RequestParam(value = "activos", required = false) Boolean activos,
            @RequestParam(value = "criterio", required = false) String criterio,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarPaths(activos, criterio));
    }

    @GetMapping("/{path_id}")
    public ResponseEntity<Map<String, Object>> detallePath(
            @PathVariable("path_id") UUID pathId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Map<String, Object> path = queryService.detallePath(pathId);
        if (path == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");
        return ResponseEntity.ok(path);
    }

    @GetMapping("/asignaciones")
    public ResponseEntity<List<Map<String, Object>>> listarAsignaciones(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "estatus", required = false) String estatus,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarAsignaciones(estudianteId, estatus));
    }

    @GetMapping("/asignaciones/{asig_id}")
    public ResponseEntity<Map<String, Object>> detalleAsignacion(
            @PathVariable("asig_id") UUID asigId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Map<String, Object> asig = queryService.detalleAsignacion(asigId);
        if (asig == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada");
        return ResponseEntity.ok(asig);
    }

    // ── WRITES ────────────────────────────────────────────────────────────────

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> crearPath(
            @RequestBody PathRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ades_learning_paths
                    (id, nombre, descripcion, nivel_educativo_id, materia_id,
                     criterio_activacion, umbral_activacion, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """, id, body.getNombre(), body.getDescripcion(), body.getNivelEducativoId(),
                body.getMateriaId(), body.getCriterioActivacion(), body.getUmbralActivacion());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id, "nombre", body.getNombre(), "descripcion", body.getDescripcion(),
                "criterio_activacion", body.getCriterioActivacion(),
                "umbral_activacion", body.getUmbralActivacion(), "is_active", true, "total_recursos", 0));
    }

    @PostMapping("/{path_id}/recursos")
    public ResponseEntity<Map<String, Object>> agregarRecurso(
            @PathVariable("path_id") UUID pathId,
            @RequestBody RecursoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_learning_paths WHERE id = ?", Integer.class, pathId);
        if (exists == null || exists == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ades_lp_recursos
                    (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, pathId, body.getOrden(), body.getTipo(), body.getTitulo(),
                body.getDescripcion(), body.getUrlRecurso(), body.getDuracionMin(), body.getObligatorio());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id, "orden", body.getOrden(), "tipo", body.getTipo(),
                "titulo", body.getTitulo(), "url_recurso", body.getUrlRecurso(), "is_active", true));
    }

    @PostMapping("/{path_id}/asignar")
    public ResponseEntity<Map<String, Object>> asignarPath(
            @PathVariable("path_id") UUID pathId,
            @RequestBody AsignacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<Map<String, Object>> pathRows = jdbc.queryForList(
                "SELECT nombre FROM ades_learning_paths WHERE id = ?", pathId);
        if (pathRows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning path no encontrado");
        String pathNombre = (String) pathRows.get(0).get("nombre");

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ades_lp_asignaciones
                    (id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW())
                ON CONFLICT (path_id, estudiante_id) DO UPDATE
                SET estatus = EXCLUDED.estatus, fecha_modificacion = NOW()
                """, id, pathId, body.getEstudianteId(), user.getId(), body.getMotivo());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, path_id, estudiante_id, motivo, estatus, pct_completado, fecha_creacion
                FROM ades_lp_asignaciones WHERE path_id = ? AND estudiante_id = ? LIMIT 1
                """, pathId, body.getEstudianteId());
        Map<String, Object> response = new HashMap<>(rows.get(0));
        response.put("path_nombre", pathNombre);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/asignaciones/{asig_id}/progreso/{recurso_id}")
    public ResponseEntity<Map<String, Object>> registrarProgresoEndpoint(
            @PathVariable("asig_id") UUID asigId,
            @PathVariable("recurso_id") UUID recursoId,
            @RequestBody ProgresoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        RegistrarProgresoUseCase.Result result = registrarProgreso.ejecutar(
                new RegistrarProgresoUseCase.Command(asigId, recursoId, body.getTiempoMin(), body.getCalificacion()));
        return ResponseEntity.ok(Map.of(
                "mensaje", "Progreso registrado",
                "estatus", result.estatus(),
                "pct_completado", result.pctCompletado()));
    }

    @PostMapping("/asignar-automatico/{grupo_id}")
    public ResponseEntity<Map<String, Object>> asignarAutomatico(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> alertas = jdbc.queryForList("""
                SELECT a.estudiante_id, a.tipo_alerta FROM ades_alertas_academicas a
                WHERE a.grupo_id = ? AND a.atendida = FALSE
                """, grupoId);
        if (alertas.isEmpty())
            return ResponseEntity.ok(Map.of("asignadas", 0, "mensaje", "Sin alertas activas en el grupo"));

        List<Map<String, Object>> paths = jdbc.queryForList("""
                SELECT id, criterio_activacion FROM ades_learning_paths
                WHERE criterio_activacion IN ('REPROBACION', 'AUSENTISMO', 'RIESGO_ALTO') AND is_active = TRUE
                """);

        Map<String, String> tipoACriterio = Map.of(
                "RIESGO_REPROBACION", "REPROBACION",
                "AUSENTISMO_CRITICO", "AUSENTISMO");
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
            jdbc.update("""
                    INSERT INTO ades_lp_asignaciones
                        (id, path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                    VALUES (?, ?, ?, ?, ?, 'PENDIENTE', 0, NOW())
                    ON CONFLICT (path_id, estudiante_id) DO NOTHING
                    """, UUID.randomUUID(), pathId, alerta.get("estudiante_id"),
                    user.getId(), "AUTO_" + alerta.get("tipo_alerta"));
            asignadas++;
        }

        return ResponseEntity.ok(Map.of("asignadas", asignadas, "grupo_id", grupoId.toString()));
    }

    // ── FastAPI proxy ─────────────────────────────────────────────────────────

    @PostMapping("/asignaciones/{asig_id}/recomendar-ia")
    public ResponseEntity<Map<String, Object>> recomendarIa(
            @PathVariable("asig_id") UUID asigId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/asignaciones/" + asigId + "/recomendar-ia");
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al recomendar IA en microservicio FastAPI: " + e.getMessage());
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
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al ajustar dinámicamente en microservicio FastAPI: " + e.getMessage());
        }
    }
}

package mx.ades.modules.learning_paths;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.learning_paths.application.service.LearningPathApplicationService;
import mx.ades.modules.learning_paths.domain.port.in.AsignarPathUseCase;
import mx.ades.modules.learning_paths.domain.port.in.CrearLearningPathUseCase;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.query.LearningPathQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.*;
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
    private final RegistrarProgresoUseCase registrarProgreso;
    private final CrearLearningPathUseCase crearLearningPath;
    private final AsignarPathUseCase asignarPath;
    private final LearningPathApplicationService learningPathService;
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

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> crearPath(
            @RequestBody PathRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        try {
            var cmd = new CrearLearningPathUseCase.Command(
                body.getNombre(), body.getDescripcion(), body.getNivelEducativoId(),
                body.getMateriaId(), body.getCriterioActivacion(), body.getUmbralActivacion()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(crearLearningPath.crear(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PostMapping("/{path_id}/recursos")
    public ResponseEntity<Map<String, Object>> agregarRecurso(
            @PathVariable("path_id") UUID pathId,
            @RequestBody RecursoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(
                learningPathService.agregarRecurso(pathId, body.getOrden(), body.getTipo(),
                    body.getTitulo(), body.getDescripcion(), body.getUrlRecurso(),
                    body.getDuracionMin(), body.getObligatorio())
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{path_id}/asignar")
    public ResponseEntity<Map<String, Object>> asignarPathEndpoint(
            @PathVariable("path_id") UUID pathId,
            @RequestBody AsignacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            var cmd = new AsignarPathUseCase.Command(pathId, body.getEstudianteId(), user.getId(), body.getMotivo());
            return ResponseEntity.status(HttpStatus.CREATED).body(asignarPath.asignar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
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
        int asignadas = learningPathService.asignarAutomatico(grupoId, user.getId());
        if (asignadas == 0) {
            return ResponseEntity.ok(Map.of("asignadas", 0, "mensaje", "Sin alertas activas en el grupo"));
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

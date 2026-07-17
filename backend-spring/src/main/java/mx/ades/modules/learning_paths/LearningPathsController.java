package mx.ades.modules.learning_paths;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Adaptador REST para la gestión de rutas de aprendizaje personalizado.
 * Expone endpoints bajo /api/v1/learning-paths para listar y detallar paths,
 * crear paths y agregar recursos, asignar paths manualmente o de forma automática
 * (basada en alertas de riesgo del grupo), registrar progreso por recurso y
 * proxear solicitudes de recomendación IA y ajuste dinámico al microservicio FastAPI.
 * Requiere JWT válido; las operaciones de asignación y proxy propagan el token de autorización.
 *
 * @author ADES
 * @since 2026
 */
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
    private final AjusteDinamicoService ajusteDinamicoService;
    private final JdbcTemplate jdbc;

    private final RestClient restClient = RestClient.builder().build();
    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/learning-paths";
    private static final String IA_AVANZADA_URL = "http://ades-api:8000/api/v1/ia-avanzada";

    @Data
    public static class PathRequest {
        @NotBlank(message = "nombre es obligatorio")
        @Size(max = 200, message = "nombre máximo 200 caracteres")
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
        @NotNull(message = "estudianteId es obligatorio")
        private UUID estudianteId;
        private String motivo;
    }

    @Data
    public static class ProgresoRequest {
        private Integer tiempoMin;
        private Double calificacion;
    }

    /**
     * BOLA (OWASP API1) — para no-admins, fuerza que el estudiante consultado
     * pertenezca al plantel del usuario. Hallazgo de auditoría 2026-07-04, corregido
     * 2026-07-06. (Corregido 2026-07-16 — decisión explícita del usuario: solo
     * ADMIN_GLOBAL exento, ver AdesUserService#getEffectivePlantelId.)
     */
    private void verificarAccesoEstudiante(AdesUser user, UUID estudianteId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado");
        userService.verificarPlantel(user, rows.get(0), "El estudiante no pertenece a su plantel");
    }

    private void verificarAccesoAsignacion(AdesUser user, UUID asignacionId) {
        UUID estudianteId = jdbc.queryForObject(
                "SELECT estudiante_id FROM ades_lp_asignaciones WHERE id = ?", UUID.class, asignacionId);
        verificarAccesoEstudiante(user, estudianteId);
    }

    /**
     * BFLA fix (auditoría 2026-07-15): crear/asignar rutas de aprendizaje y disparar
     * el ajuste automático masivo por grupo son operaciones de personal escolar
     * (nivelAcceso &le;4); antes solo se verificaba scoping por plantel
     * (verificarAccesoEstudiante/Grupo), lo cual permitía a un padre o alumno del
     * mismo plantel crear paths o asignarlos a cualquier estudiante del plantel.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
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
        AdesUser user = userService.resolveUser(jwt);
        if (estudianteId != null) verificarAccesoEstudiante(user, estudianteId);
        return ResponseEntity.ok(queryService.listarAsignaciones(estudianteId, estatus));
    }

    @GetMapping("/asignaciones/{asig_id}")
    public ResponseEntity<Map<String, Object>> detalleAsignacion(
            @PathVariable("asig_id") UUID asigId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAsignacion(user, asigId);
        Map<String, Object> asig = queryService.detalleAsignacion(asigId);
        if (asig == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada");
        return ResponseEntity.ok(asig);
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> crearPath(
            @RequestBody @Valid PathRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
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
        requireStaff(userService.resolveUser(jwt));
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
            @RequestBody @Valid AsignacionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        verificarAccesoEstudiante(user, body.getEstudianteId());
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
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAsignacion(user, asigId);
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
        requireStaff(user);
        userService.verificarAccesoGrupo(user, grupoId);
        int asignadas = learningPathService.asignarAutomatico(grupoId, user.getId());
        if (asignadas == 0) {
            return ResponseEntity.ok(Map.of("asignadas", 0, "mensaje", "Sin alertas activas en el grupo"));
        }
        return ResponseEntity.ok(Map.of("asignadas", asignadas, "grupo_id", grupoId.toString()));
    }

    /**
     * IA-014: proxy al endpoint real de narrativa pedagógica en FastAPI
     * (antes apuntaba a una ruta de FastAPI que nunca se implementó — corregido
     * 2026-07-03, ver app/api/v1/ia_avanzada.py#learning_path_narrativa).
     */
    @PostMapping("/asignaciones/{asig_id}/recomendar-ia")
    public ResponseEntity<Map<String, Object>> recomendarIa(
            @PathVariable("asig_id") UUID asigId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAsignacion(user, asigId);
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(IA_AVANZADA_URL + "/learning-path-narrativa/" + asigId);
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            Map narrativa = request.retrieve().body(Map.class);
            ajusteDinamicoService.guardarNarrativa(asigId, narrativa);
            return ResponseEntity.ok(Map.of("ia_recomendacion", narrativa));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al recomendar IA en microservicio FastAPI: " + e.getMessage());
        }
    }

    /**
     * IA-009: ajuste dinámico de la ruta según desempeño — implementado localmente
     * en Spring (basado en reglas sobre ades_lp_progreso, sin IA/LLM), no requiere
     * FastAPI. Antes este endpoint proxeaba a una ruta de FastAPI que nunca se
     * implementó — corregido 2026-07-03.
     */
    @PostMapping("/ajustar-dinamico/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> ajustarDinamico(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoEstudiante(user, estudianteId);
        return ResponseEntity.ok(ajusteDinamicoService.ajustar(estudianteId));
    }
}

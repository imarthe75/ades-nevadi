package mx.ades.modules.eval_docente;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.EnviarEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import mx.ades.modules.eval_docente.query.EvalDocenteQueryService;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptador REST para la evaluación docente 360°.
 * Expone endpoints bajo /api/v1/eval-docente para crear evaluaciones, guardar
 * calificaciones por criterio y enviar (cerrar) una evaluación. Soporta los
 * cuatro tipos de evaluador: AUTO, PAR, COORDINADOR y DIRECTOR. La escala de
 * calificación por criterio es 1-5 (7 criterios ponderados). El endpoint de
 * criterios lista las dimensiones de evaluación con sus ponderaciones. El resumen
 * por profesor agrega promedios ponderados filtrable por ciclo escolar.
 * Toda operación requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/eval-docente")
@RequiredArgsConstructor
public class EvalDocenteController {

    private final AdesUserService userService;
    private final CrearEvaluacionUseCase crearEvaluacionUseCase;
    private final GuardarCriteriosUseCase guardarCriteriosUseCase;
    private final EnviarEvaluacionUseCase enviarEvaluacionUseCase;
    private final EvalDocenteQueryService queryService;
    private final PlanMejoraService planMejoraService;
    private final EvalDocenteRepositoryPort repo;

    @Data
    public static class EvaluacionCreate {
        private UUID profesorId;
        private UUID cicloEscolarId;
        private UUID evaluadorId;
        private String tipoEvaluador;
        private String comentarios;
    }

    @Data
    public static class CriterioCalificacionDto {
        private UUID criterioId;
        private Integer calificacion;
        private String observacion;
    }

    @GetMapping("/criterios")
    public ResponseEntity<List<Map<String, Object>>> listarCriterios(
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarCriterios());
    }

    @GetMapping("/profesor/{profesorId}/resumen")
    public ResponseEntity<Map<String, Object>> resumenDocente(
            @PathVariable("profesorId") UUID profesorId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        // BFLA/BOLA fix: el resumen consolidado de desempeño es dato de personal (dossier
        // laboral). Antes solo llamaba resolveUser() sin verificar nivelAcceso ni ownership —
        // cualquier usuario autenticado (incl. padres/alumnos nivelAcceso=5) podía consultar
        // el resumen de evaluación 360° de CUALQUIER profesor por path param.
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(profesorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar su propio resumen");
        }
        return ResponseEntity.ok(queryService.resumenProfesor(profesorId, cicloId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearEvaluacion(
            @RequestBody EvaluacionCreate data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(data.getEvaluadorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede crear evaluaciones donde usted sea el evaluador");
        }

        CrearEvaluacionUseCase.Command cmd;
        try {
            cmd = new CrearEvaluacionUseCase.Command(
                    data.getProfesorId(), data.getCicloEscolarId(), data.getEvaluadorId(),
                    data.getTipoEvaluador(), data.getComentarios(), user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(crearEvaluacionUseCase.crear(cmd));
    }

    @PostMapping("/{evalId}/criterios")
    public ResponseEntity<Map<String, Object>> guardarCriterios(
            @PathVariable("evalId") UUID evalId,
            @RequestBody List<CriterioCalificacionDto> criterios,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        Map<String, Object> eval = repo.fetchEvaluacion(evalId);
        if (user.getNivelAcceso() == 4 && eval != null) {
            UUID evaluadorId = (UUID) eval.get("evaluador_id");
            if (evaluadorId != null && !user.getPersonaId().equals(evaluadorId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede modificar sus propias evaluaciones");
            }
        }

        List<GuardarCriteriosUseCase.CriterioCalificacion> domainCriterios = criterios.stream()
                .map(c -> new GuardarCriteriosUseCase.CriterioCalificacion(
                        c.getCriterioId(), c.getCalificacion(), c.getObservacion()))
                .collect(Collectors.toList());

        GuardarCriteriosUseCase.Command cmd;
        try {
            cmd = new GuardarCriteriosUseCase.Command(evalId, domainCriterios);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(guardarCriteriosUseCase.guardar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PatchMapping("/{evalId}/enviar")
    public ResponseEntity<Map<String, Object>> enviarEvaluacion(
            @PathVariable("evalId") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        Map<String, Object> eval = repo.fetchEvaluacion(evalId);
        if (user.getNivelAcceso() == 4 && eval != null) {
            UUID evaluadorId = (UUID) eval.get("evaluador_id");
            if (evaluadorId != null && !user.getPersonaId().equals(evaluadorId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede enviar sus propias evaluaciones");
            }
        }

        try {
            return ResponseEntity.ok(enviarEvaluacionUseCase.enviar(
                    new EnviarEvaluacionUseCase.Command(evalId, user.getUsername())));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ── DP-016: Plan de mejora (basado en reglas, no IA) ──────────────────────
    // Datos de desempeño de personal — restringido a Coordinador Académico o superior.

    private static final int NIVEL_COORD_ACADEMICO = 3;

    private void requireCoordAcademico(AdesUser user) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > NIVEL_COORD_ACADEMICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Coordinador Académico o superior");
        }
    }

    @PostMapping("/{evalId}/plan-mejora")
    public ResponseEntity<List<Map<String, Object>>> generarPlanMejora(
            @PathVariable("evalId") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordAcademico(userService.resolveUser(jwt));
        return ResponseEntity.ok(planMejoraService.generar(evalId));
    }

    @GetMapping("/{evalId}/plan-mejora")
    public ResponseEntity<List<Map<String, Object>>> obtenerPlanMejora(
            @PathVariable("evalId") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordAcademico(userService.resolveUser(jwt));
        return ResponseEntity.ok(planMejoraService.listar(evalId));
    }

    @PatchMapping("/plan-mejora/{id}")
    public ResponseEntity<Map<String, Object>> actualizarEstadoPlanMejora(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordAcademico(userService.resolveUser(jwt));
        planMejoraService.actualizarEstado(id, body.get("estado"));
        return ResponseEntity.ok(Map.of("id", id.toString(), "estado", body.get("estado")));
    }
}

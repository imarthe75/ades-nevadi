package mx.ades.modules.boletas;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.boletas.domain.port.in.GenerarBoletaUseCase;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la generación de boletas de calificaciones en PDF.
 * Expone endpoints bajo /api/v1/boletas que actúan como proxy hacia el
 * microservicio FastAPI (Python/WeasyPrint). No genera documentos directamente:
 * delega la renderización al use case {@code GenerarBoletaUseCase}.
 * Soporta boleta NEM (primaria/secundaria SEP, escala 6-10 o cualitativa A/B/C/D),
 * boleta UAEMEX (preparatoria CBU, escala 0-10 con ordinario/extra/definitiva)
 * y generación batch por grupo con seguimiento de tarea asíncrona (taskId).
 * Toda solicitud requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/boletas")
@RequiredArgsConstructor
public class BoletasController {

    private final AdesUserService userService;
    private final GenerarBoletaUseCase generarUseCase;
    private final JdbcTemplate jdbc;

    /**
     * BOLA fix: antes de este chequeo, cualquier cuenta autenticada (incluyendo un
     * alumno o padre, nivelAcceso &gt;=5) podía descargar la boleta de CUALQUIER
     * estudiante con solo conocer su UUID — la boleta contiene calificaciones (PII
     * académica) de otro menor. Personal escolar (nivelAcceso &le;4) conserva alcance
     * institucional; alumnos/padres solo pueden ver la boleta de sí mismos o de un
     * alumno del que son tutor activo (mismo criterio que
     * CalificacionesController#boleta / GradebookController#requireAccesoAlumno).
     */
    private void verificarAccesoAlumno(AdesUser user, UUID estudianteId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            return;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes e WHERE e.id = ? AND (" +
                "  e.persona_id = ? OR EXISTS (" +
                "    SELECT 1 FROM ades_tutores_alumnos ta JOIN ades_personas p ON p.id = ta.persona_id " +
                "    WHERE ta.alumno_id = e.id AND ta.is_active = TRUE AND p.email_personal = ?" +
                "  )" +
                ")", Integer.class, estudianteId, user.getPersonaId(), user.getEmail());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para ver la boleta de este estudiante");
        }
    }

    /**
     * BFLA fix: generar boletas en batch para TODO un grupo expone las calificaciones
     * de todos sus alumnos de una sola llamada — antes de este chequeo cualquier
     * usuario autenticado (incluido un alumno/padre) podía disparar el batch de un
     * grupo ajeno. Se restringe a personal escolar.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    @GetMapping("/{estudiante_id}")
    public ResponseEntity<byte[]> generarBoleta(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, estudianteId);
        return generarUseCase.generar(estudianteId, cicloId, authHeader);
    }

    @PostMapping("/grupo/{grupo_id}/batch")
    public ResponseEntity<Map<String, Object>> encolarBoletasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        return generarUseCase.encolarGrupo(grupoId, cicloId, authHeader);
    }

    @GetMapping("/tarea/{task_id}")
    public ResponseEntity<Map<String, Object>> estadoTarea(
            @PathVariable("task_id") String taskId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return generarUseCase.estadoTarea(taskId, authHeader);
    }

    @GetMapping("/uaemex/{estudiante_id}")
    public ResponseEntity<byte[]> generarConstanciaUaemex(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, estudianteId);
        return generarUseCase.generarUaemex(estudianteId, cicloId, authHeader);
    }
}

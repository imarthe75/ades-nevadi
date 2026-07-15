package mx.ades.modules.portal;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.portal.query.PortalQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para el portal académico del alumno (vista integrada).
 * Expone endpoints bajo /api/v1/portal para búsqueda de alumnos por nombre/matrícula,
 * resumen académico (datos del alumno + grupo + ciclo activo), calificaciones del ciclo,
 * estadísticas de asistencia y tareas pendientes o completadas.
 * Todos los endpoints resuelven el ciclo escolar activo automáticamente cuando no se
 * especifica, y requieren JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalController {

    private final AdesUserService userService;
    private final PortalQueryService queryService;
    private final JdbcTemplate jdbc;

    /**
     * BOLA (OWASP API1) — este portal de búsqueda/consulta expone calificaciones,
     * asistencias y tareas por {@code estudiante_id}; sin este chequeo cualquier
     * cuenta autenticada (docente/padre/alumno de OTRO plantel) podía consultar el
     * expediente académico de cualquier alumno del sistema. Mismo patrón que
     * {@code LearningPathsController#verificarAccesoEstudiante}.
     */
    private void verificarAccesoEstudiante(AdesUser user, UUID estudianteId) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() <= 1) return;
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes WHERE id = ? AND plantel_id = ?",
                Long.class, estudianteId, user.getPlantelId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El estudiante no pertenece a su plantel");
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Map<String, Object>>> buscarAlumnos(
            @RequestParam("q") String q,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (q == null || q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'q' debe tener al menos 2 caracteres");
        }
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(queryService.buscarAlumnos(q, effectivePlantel, cicloId));
    }

    @GetMapping("/{estudiante_id}/resumen")
    public ResponseEntity<Map<String, Object>> resumenAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoEstudiante(user, estudianteId);

        String cicloRefStr = (cicloId != null) ? cicloId.toString() : queryService.getCicloActivo(estudianteId);
        if (cicloRefStr == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno sin inscripciones");
        }
        UUID cicloRef = UUID.fromString(cicloRefStr);

        Map<String, Object> result = queryService.resumenAlumno(estudianteId, cicloRef);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado en el ciclo indicado");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{estudiante_id}/calificaciones")
    public ResponseEntity<List<Map<String, Object>>> calificacionesAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoEstudiante(user, estudianteId);

        String cicloRefStr = (cicloId != null) ? cicloId.toString() : queryService.getCicloActivo(estudianteId);
        if (cicloRefStr == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(queryService.calificacionesAlumno(estudianteId, UUID.fromString(cicloRefStr)));
    }

    @GetMapping("/{estudiante_id}/asistencias")
    public ResponseEntity<Map<String, Object>> asistenciasAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoEstudiante(user, estudianteId);

        String cicloRefStr = (cicloId != null) ? cicloId.toString() : queryService.getCicloActivo(estudianteId);
        if (cicloRefStr == null) {
            return ResponseEntity.ok(Map.of("resumen", Collections.emptyMap(), "detalle", Collections.emptyList()));
        }
        return ResponseEntity.ok(queryService.asistenciasAlumno(estudianteId, UUID.fromString(cicloRefStr)));
    }

    @GetMapping("/{estudiante_id}/tareas")
    public ResponseEntity<List<Map<String, Object>>> tareasAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "solo_pendientes", defaultValue = "false") boolean soloPendientes,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoEstudiante(user, estudianteId);

        String cicloRefStr = (cicloId != null) ? cicloId.toString() : queryService.getCicloActivo(estudianteId);
        if (cicloRefStr == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(queryService.tareasAlumno(estudianteId, UUID.fromString(cicloRefStr), soloPendientes));
    }
}

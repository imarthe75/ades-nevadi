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
     *
     * <p>BOLA fix (auditoría fbatch_02): el chequeo original solo acotaba por
     * plantel_id para CUALQUIER nivelAcceso &gt;1 — es decir, un padre o alumno
     * (nivelAcceso &ge;5) podía ver el resumen/calificaciones/asistencias/tareas de
     * CUALQUIER OTRO alumno de su mismo plantel, no solo de sus propios hijos, ya
     * que nunca se verificaba la relación de tutoría (tabla
     * {@code ades_tutores_alumnos}). Se aplica ahora el mismo criterio dual que
     * {@code PortalFamiliasController#verificarAccesoAlumno}/
     * {@code EntregasController#requireAccesoAlumno}: personal escolar (nivelAcceso
     * &le;4) acotado por plantel; padres/alumnos (nivelAcceso &ge;5) solo si son
     * tutor activo del alumno.
     */
    private void verificarAccesoEstudiante(AdesUser user, UUID estudianteId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso <= 0) return;
        if (nivelAcceso <= 4) {
            // Corregido 2026-07-16: el umbral original (`<= 1`, exento) dejaba a
            // ADMIN_PLANTEL sin restricción — inconsistente con el "mismo criterio"
            // que este Javadoc afirma compartir con PortalFamiliasController
            // (que ya usa nivelAcceso 0 como único exento).
            List<UUID> rows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            userService.verificarPlantel(user, rows.get(0), "El estudiante no pertenece a su plantel");
            return;
        }
        String email = user.getEmail();
        Integer count = email == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE p.email_personal = ? AND ta.alumno_id = ? AND ta.is_active = TRUE",
                Integer.class, email, estudianteId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este alumno");
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Map<String, Object>>> buscarAlumnos(
            @RequestParam("q") String q,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (auditoría fbatch_02): este buscador de alumnos (nombre, matrícula,
        // grupo, plantel) es un typeahead de uso exclusivamente de personal escolar
        // (movilidad, optativas, padres-admin, y la vista de simulación de admin en
        // padres.component) — un padre/alumno real (nivelAcceso &ge;5) no tiene ningún
        // flujo legítimo que lo requiera y podía usarlo para enumerar el nombre y
        // matrícula de CUALQUIER OTRO alumno de su plantel, no solo de sus propios hijos.
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
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

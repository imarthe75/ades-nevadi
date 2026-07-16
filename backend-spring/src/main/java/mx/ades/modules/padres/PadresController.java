package mx.ades.modules.padres;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.padres.query.PadresQueryService;
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

/**
 * Adaptador REST para el portal de padres/tutores (nivelAcceso &ge;5, ver mig. 090).
 * Expone endpoints bajo /api/v1/padres para que un padre o tutor autenticado consulte
 * la lista de sus alumnos vinculados y las calificaciones de un alumno específico.
 * Implementa validación IDOR: si el usuario tiene personaId y nivelAcceso &gt; 3
 * (es decir, no es personal escolar con alcance institucional: admin/director/coordinador),
 * verifica que sea contacto registrado del alumno antes de devolver calificaciones (HTTP 403 si no).
 * Requiere JWT válido en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/padres")
@RequiredArgsConstructor
public class PadresController {

    private final AdesUserService userService;
    private final PadresQueryService queryService;
    private final JdbcTemplate jdbc;

    @GetMapping("/mis-alumnos")
    public ResponseEntity<List<Map<String, Object>>> misAlumnos(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.misAlumnos(user.getPersonaId()));
    }

    @GetMapping("/calificaciones/{estudiante_id}")
    public ResponseEntity<List<Map<String, Object>>> calificacionesAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        // BOLA fix (2026-07-16): para nivelAcceso 1-3 (Admin_Plantel/Director/
        // Coordinador, "personal escolar con alcance institucional") no había NINGÚN
        // chequeo — cross-plantel libre sobre calificaciones de cualquier alumno. Solo
        // nivelAcceso 0 mantiene alcance libre.
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 0 && user.getNivelAcceso() <= 3) {
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            userService.verificarPlantel(user, plantelRows.get(0), "El alumno no pertenece a su plantel");
        } else if (user.getPersonaId() != null && user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            if (!queryService.esContactoDeAlumno(user.getPersonaId(), estudianteId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a este alumno");
            }
        }

        return ResponseEntity.ok(queryService.calificaciones(estudianteId));
    }
}

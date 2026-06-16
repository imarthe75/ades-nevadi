package mx.ades.modules.portal;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.portal.query.PortalQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalController {

    private final AdesUserService userService;
    private final PortalQueryService queryService;

    @GetMapping("/buscar")
    public ResponseEntity<List<Map<String, Object>>> buscarAlumnos(
            @RequestParam("q") String q,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (q == null || q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'q' debe tener al menos 2 caracteres");
        }
        return ResponseEntity.ok(queryService.buscarAlumnos(q, plantelId, cicloId));
    }

    @GetMapping("/{estudiante_id}/resumen")
    public ResponseEntity<Map<String, Object>> resumenAlumno(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

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
        userService.resolveUser(jwt);

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
        userService.resolveUser(jwt);

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
        userService.resolveUser(jwt);

        String cicloRefStr = (cicloId != null) ? cicloId.toString() : queryService.getCicloActivo(estudianteId);
        if (cicloRefStr == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(queryService.tareasAlumno(estudianteId, UUID.fromString(cicloRefStr), soloPendientes));
    }
}

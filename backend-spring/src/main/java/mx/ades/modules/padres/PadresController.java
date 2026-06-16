package mx.ades.modules.padres;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.padres.query.PadresQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/padres")
@RequiredArgsConstructor
public class PadresController {

    private final AdesUserService userService;
    private final PadresQueryService queryService;

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

        if (user.getPersonaId() != null && user.getNivelAcceso() > 3) {
            if (!queryService.esContactoDeAlumno(user.getPersonaId(), estudianteId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a este alumno");
            }
        }

        return ResponseEntity.ok(queryService.calificaciones(estudianteId));
    }
}

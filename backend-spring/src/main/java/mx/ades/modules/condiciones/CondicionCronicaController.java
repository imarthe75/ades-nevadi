package mx.ades.modules.condiciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.condiciones.domain.model.TipoCondicion;
import mx.ades.modules.condiciones.domain.port.in.ActualizarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.EliminarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.RegistrarCondicionUseCase;
import mx.ades.modules.condiciones.query.CondicionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/condiciones-cronicas")
@RequiredArgsConstructor
public class CondicionCronicaController {

    private final AdesUserService            userService;
    private final RegistrarCondicionUseCase  registrarCondicion;
    private final ActualizarCondicionUseCase actualizarCondicion;
    private final EliminarCondicionUseCase   eliminarCondicion;
    private final CondicionQueryService      query;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "alumno_id",       required = false) UUID alumnoId,
            @RequestParam(value = "tipo_condicion",  required = false) String tipoCondicion,
            @RequestParam(value = "solo_activas", defaultValue = "true") boolean soloActivas,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.list(alumnoId, tipoCondicion, soloActivas));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new RegistrarCondicionUseCase.Command(
                body.getAlumnoId(),
                TipoCondicion.of(body.getTipoCondicion()),
                body.getDescripcion(),
                body.getMedicacionNombre(),
                body.getDosis(),
                body.getFrecuencia(),
                body.getAlergias(),
                body.getMedicoResponsable(),
                body.getTelefonoMedico(),
                nivel
        );
        UUID id = registrarCondicion.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/alumno/{alumnoId}/alerta")
    public ResponseEntity<List<Map<String, Object>>> alertaEmergencia(
            @PathVariable("alumnoId") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.alertaEmergencia(alumnoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondicionCronica> obtener(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.findById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new ActualizarCondicionUseCase.Command(
                id,
                body.getDescripcion(),
                body.getMedicacionNombre(),
                body.getDosis(),
                body.getFrecuencia(),
                body.getAlergias(),
                body.getMedicoResponsable(),
                body.getTelefonoMedico(),
                body.getActiva(),
                nivel
        );
        actualizarCondicion.actualizar(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        eliminarCondicion.eliminar(id, nivel);
    }
}

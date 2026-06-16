package mx.ades.modules.horarios;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.application.service.HorarioApplicationService;
import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.query.HorarioQueryService;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final AdesUserService userService;
    private final HorarioQueryService queryService;
    private final CrearHorarioUseCase crearHorarioUseCase;
    private final ActualizarHorarioUseCase actualizarHorarioUseCase;
    private final HorarioApplicationService horarioService;

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> porGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porGrupo(grupoId));
    }

    @GetMapping("/profesor/{profesor_id}")
    public ResponseEntity<List<Map<String, Object>>> porProfesor(
            @PathVariable("profesor_id") UUID profesorId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porProfesor(profesorId));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, plantelId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            UUID id = crearHorarioUseCase.crear(new CrearHorarioUseCase.Command(
                body.getGrupoId(), body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getCicloEscolarId(), body.getDiaSemana(),
                body.getHoraInicio(), body.getHoraFin(), body.getOrigen(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(queryService.obtener(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            actualizarHorarioUseCase.actualizar(new ActualizarHorarioUseCase.Command(
                id, body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getDiaSemana(), body.getHoraInicio(), body.getHoraFin(),
                body.getOrigen(), body.getMotivoCambio(), user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok(queryService.obtener(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        horarioService.eliminar(id);
    }

    @Data
    public static class HorarioPayload {
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID aulaId;
        private UUID cicloEscolarId;
        private Integer diaSemana;
        private String horaInicio;
        private String horaFin;
        private String origen;
        private String motivoCambio;
    }
}

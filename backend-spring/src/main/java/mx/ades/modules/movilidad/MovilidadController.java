package mx.ades.modules.movilidad;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.movilidad.domain.model.TipoMovilidad;
import mx.ades.modules.movilidad.domain.port.in.RegistrarBajaUseCase;
import mx.ades.modules.movilidad.domain.port.in.RegistrarCambioGrupoUseCase;
import mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort;
import mx.ades.modules.movilidad.query.MovilidadQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/movilidad")
@RequiredArgsConstructor
public class MovilidadController {

    private final AdesUserService userService;
    private final MovilidadQueryService queryService;
    private final RegistrarCambioGrupoUseCase registrarCambioGrupo;
    private final RegistrarBajaUseCase registrarBajaMovilidad;
    private final MovilidadRepositoryPort movilidadRepository;

    // ── Request DTOs ─────────────────────────────────────────────────────────

    @Data
    public static class CambioGrupoRequest {
        private UUID grupoDestinoId;
        private String motivo;
        private UUID cicloEscolarId;
    }

    @Data
    public static class TrasladoRequest {
        private UUID plantelDestinoId;
        private UUID grupoDestinoId;
        private String motivo;
        private String plantelDestinoNombre;
        private String claveCtDestino;
    }

    @Data
    public static class BajaRequest {
        private String motivo;
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

    @Data
    public static class ReactivarRequest {
        private UUID grupoId;
        private UUID cicloEscolarId;
        private String observaciones;
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void requireAcceso(AdesUser user, TipoMovilidad tipo) {
        if (user.getNivelAcceso() == null || !tipo.permitePara(user.getNivelAcceso())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Se requiere nivel " + (tipo.nivelAccesoMinimo() <= 2 ? "Director" : "Coordinador") + " o superior");
        }
    }

    // ── WRITES (delegadas a use cases) ────────────────────────────────────────

    @PostMapping("/cambio-grupo/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> cambioGrupo(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody CambioGrupoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.CAMBIO_GRUPO);

        var result = registrarCambioGrupo.ejecutar(new RegistrarCambioGrupoUseCase.Command(
                estudianteId, body.getGrupoDestinoId(), body.getMotivo(),
                body.getCicloEscolarId(), user.getId(), user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Cambio de grupo registrado",
                "grupo_anterior", result.grupoAnterior(),
                "grupo_nuevo", result.grupoNuevo(),
                "cambio_id", result.cambioId().toString()
        ));
    }

    @PostMapping("/traslado/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> traslado(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody TrasladoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.TRASLADO);

        var result = registrarBajaMovilidad.ejecutar(new RegistrarBajaUseCase.Command(
                estudianteId, TipoMovilidad.TRASLADO, body.getMotivo(),
                null, null,
                body.getPlantelDestinoNombre(), body.getClaveCtDestino(), null,
                body.getGrupoDestinoId(), user.getId(), user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", result.mensaje(), "tipo", result.tipo().name()));
    }

    @PostMapping("/baja-temporal/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> bajaTemporal(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.BAJA_TEMPORAL);

        var result = registrarBajaMovilidad.ejecutar(new RegistrarBajaUseCase.Command(
                estudianteId, TipoMovilidad.BAJA_TEMPORAL, body.getMotivo(),
                body.getFechaEfectiva(), body.getFechaReingreso(),
                null, null, body.getObservaciones(),
                null, user.getId(), user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", result.mensaje(),
                "fecha_reingreso_estimada",
                body.getFechaReingreso() != null ? body.getFechaReingreso().toString() : null
        ));
    }

    @PostMapping("/baja-definitiva/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> bajaDefinitiva(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.BAJA_DEFINITIVA);

        var result = registrarBajaMovilidad.ejecutar(new RegistrarBajaUseCase.Command(
                estudianteId, TipoMovilidad.BAJA_DEFINITIVA, body.getMotivo(),
                body.getFechaEfectiva(), null,
                body.getPlantelDestino(), body.getClaveCtDestino(), body.getObservaciones(),
                null, user.getId(), user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", result.mensaje()));
    }

    @PostMapping("/reactivar/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> reactivar(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody ReactivarRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.REACTIVACION);

        UUID bajaId = movilidadRepository.findActiveBajaTemporal(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El alumno no tiene baja temporal activa"));

        movilidadRepository.cerrarBajaTemporal(bajaId, user.getUsername());
        movilidadRepository.activarEstudiante(estudianteId, user.getUsername());
        movilidadRepository.crearInscripcion(estudianteId, body.getGrupoId(), body.getCicloEscolarId(),
                user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Alumno reactivado e inscrito en nuevo grupo"));
    }

    // ── READS (delegadas a QueryService) ─────────────────────────────────────

    @GetMapping("/historial/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> historial(@PathVariable("estudiante_id") UUID estudianteId) {
        return ResponseEntity.ok(queryService.historial(estudianteId));
    }

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo_baja", required = false) String tipoBaja,
            @RequestParam(value = "solo_activas", defaultValue = "true") boolean soloActivas,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.BAJA_TEMPORAL);
        return ResponseEntity.ok(queryService.listarBajas(plantelId, tipoBaja, soloActivas, skip, limit));
    }

    @GetMapping("/cambios-grupo")
    public ResponseEntity<List<Map<String, Object>>> listarCambiosGrupo(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelFiltro = (user.getNivelAcceso() != null && user.getNivelAcceso() > 1)
                ? user.getPlantelId() : null;
        return ResponseEntity.ok(
                queryService.listarCambiosGrupo(estudianteId, plantelId, plantelFiltro, skip, limit));
    }
}

package mx.ades.modules.movilidad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

/**
 * Adaptador REST para la gestión de movilidad estudiantil.
 * Expone endpoints bajo /api/v1/movilidad para registrar cambios de grupo, traslados
 * inter-plantel, bajas temporales, bajas definitivas y reactivaciones de alumnos.
 * Cada tipo de movimiento valida el nivel de acceso mínimo requerido mediante
 * {@code TipoMovilidad#permitePara(nivelAcceso)}. Los endpoints de consulta listan
 * el historial de movilidad y bajas activas, con scoping por plantel para no-admins.
 * Requiere JWT válido en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
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
        @NotNull(message = "grupoDestinoId es obligatorio")
        private UUID grupoDestinoId;
        private String motivo;
        private UUID cicloEscolarId;
    }

    @Data
    public static class CambioGrupoMasivoRequest {
        private List<UUID> estudianteIds;
        @NotNull(message = "grupoDestinoId es obligatorio")
        private UUID grupoDestinoId;
        private String motivo;
        private UUID cicloEscolarId;
    }

    @Data
    public static class TrasladoRequest {
        private UUID plantelDestinoId;
        private UUID grupoDestinoId;
        @NotBlank(message = "motivo es obligatorio")
        private String motivo;
        @NotBlank(message = "plantelDestinoNombre es obligatorio")
        private String plantelDestinoNombre;
        private String claveCtDestino;
    }

    @Data
    public static class BajaRequest {
        @NotBlank(message = "motivo es obligatorio")
        private String motivo;
        @NotNull(message = "fechaEfectiva es obligatorio")
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

    @Data
    public static class ReactivarRequest {
        @NotNull(message = "grupoId es obligatorio")
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
            @RequestBody @Valid CambioGrupoRequest body,
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

    /**
     * Asigna un lote de alumnos al mismo grupo destino en una sola operación. Reusa
     * {@link RegistrarCambioGrupoUseCase} por alumno (mismo camino que el cambio individual,
     * con su propio registro de auditoría en ades_cambio_grupo) para no duplicar reglas de
     * negocio; un fallo en un alumno no aborta el resto del lote.
     */
    @PostMapping("/cambio-grupo-masivo")
    public ResponseEntity<Map<String, Object>> cambioGrupoMasivo(
            @RequestBody @Valid CambioGrupoMasivoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.CAMBIO_GRUPO);

        if (body.getEstudianteIds() == null || body.getEstudianteIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estudianteIds no puede estar vacío");
        }

        List<Map<String, Object>> fallidos = new java.util.ArrayList<>();
        int exitosos = 0;

        for (UUID estudianteId : body.getEstudianteIds()) {
            try {
                registrarCambioGrupo.ejecutar(new RegistrarCambioGrupoUseCase.Command(
                        estudianteId, body.getGrupoDestinoId(), body.getMotivo(),
                        body.getCicloEscolarId(), user.getId(), user.getUsername()));
                exitosos++;
            } catch (Exception e) {
                fallidos.add(Map.of("estudianteId", estudianteId.toString(),
                        "error", e.getMessage() != null ? e.getMessage() : "Error desconocido"));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "total", body.getEstudianteIds().size(),
                "exitosos", exitosos,
                "fallidos", fallidos
        ));
    }

    @PostMapping("/traslado/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> traslado(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody @Valid TrasladoRequest body,
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
            @RequestBody @Valid BajaRequest body,
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
            @RequestBody @Valid BajaRequest body,
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
            @RequestBody @Valid ReactivarRequest body,
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
    public ResponseEntity<Map<String, Object>> historial(
            @PathVariable("estudiante_id") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        // Faltaba resolveUser(): el historial de movilidad (cambios de grupo, bajas,
        // traslados) de cualquier alumno quedaba accesible a cualquier cuenta
        // autenticada sin verificación de rol. Se exige el mismo nivel mínimo que
        // el resto de las lecturas de movilidad (listarBajas) para prevenir BOLA/BFLA.
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoMovilidad.BAJA_TEMPORAL);
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
        requireAcceso(user, TipoMovilidad.CAMBIO_GRUPO);
        UUID plantelFiltro = (user.getNivelAcceso() != null && user.getNivelAcceso() > 1)
                ? user.getPlantelId() : null;
        return ResponseEntity.ok(
                queryService.listarCambiosGrupo(estudianteId, plantelId, plantelFiltro, skip, limit));
    }
}

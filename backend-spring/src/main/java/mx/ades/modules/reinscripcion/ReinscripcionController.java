package mx.ades.modules.reinscripcion;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.reinscripcion.domain.model.AccionReinscripcion;
import mx.ades.modules.reinscripcion.domain.port.in.ProcesarAccionReinscripcionUseCase;
import mx.ades.modules.reinscripcion.query.ReinscripcionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el proceso de reinscripción entre ciclos escolares.
 * Expone endpoints bajo /api/v1/reinscripcion para consultar el estado de reinscripción
 * con paginación, generar reportes consolidados, verificar no-adeudo de un alumno
 * y listar registros por ciclo destino. Las operaciones de escritura (validación masiva,
 * aprobación masiva, acción individual APROBAR/RECHAZAR) requieren nivelAcceso &le;3
 * (Director o Admin). Los endpoints heredados de JPA (aprobar/rechazar individuales)
 * no exigen JWT directamente. Requiere JWT válido para todas las rutas hexagonales.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/reinscripcion")
@RequiredArgsConstructor
public class ReinscripcionController {

    private final ReinscripcionService service;
    private final AdesUserService userService;
    private final ProcesarAccionReinscripcionUseCase procesarAccionReinscripcion;
    private final ReinscripcionQueryService queryService;

    private static final int NIVEL_ADMIN = 3;

    @Data
    public static class ValidarMasivaRequest {
        private UUID cicloOrigenId;
        private UUID cicloDestinoId;
    }

    @Data
    public static class RechazarRequest {
        private String razonRechazo;
    }

    @Data
    public static class AccionIndividualPayload {
        private String accion;
        private String razonRechazo;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @GetMapping("/{ciclo_destino_id}/estado")
    public ResponseEntity<Map<String, Object>> estadoReinscripcion(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.getEstado(cicloDestinoId, estado, plantelId, page, porPagina, user));
    }

    @GetMapping("/{ciclo_destino_id}/reporte")
    public ResponseEntity<Map<String, Object>> reporteReinscripcion(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.getReporte(cicloDestinoId));
    }

    @GetMapping("/no-adeudo/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> verificarNoAdeudo(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.verificarNoAdeudo(estudianteId, cicloEscolarId));
    }

    @GetMapping("/ciclo/{cicloDestinoId}")
    public ResponseEntity<List<ReinscripcionCiclo>> listarPorCicloDestino(
            @PathVariable("cicloDestinoId") UUID cicloDestinoId) {
        return ResponseEntity.ok(service.listarPorCicloDestino(cicloDestinoId));
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @PostMapping("/{ciclo_destino_id}/validar-masivo")
    public ResponseEntity<Map<String, Object>> validarMasivo(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam("ciclo_origen_id") UUID cicloOrigenId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        Map<String, Object> resumen = queryService.validarMasiva(cicloOrigenId, cicloDestinoId);
        return ResponseEntity.ok(Map.of("ok", true, "resumen", resumen));
    }

    @PostMapping("/{ciclo_destino_id}/aprobar-masivo")
    public ResponseEntity<Map<String, Object>> aprobarMasivo(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam("ciclo_origen_id") UUID cicloOrigenId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.ok(queryService.aprobarMasivo(cicloOrigenId, cicloDestinoId, user.getId()));
    }

    @PatchMapping("/{registro_id}")
    public ResponseEntity<Map<String, Object>> accionIndividual(
            @PathVariable("registro_id") UUID registroId,
            @RequestBody AccionIndividualPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        AccionReinscripcion accion = AccionReinscripcion.of(body.getAccion());
        ProcesarAccionReinscripcionUseCase.Result result = procesarAccionReinscripcion.ejecutar(
                new ProcesarAccionReinscripcionUseCase.Command(
                        registroId, accion, body.getRazonRechazo(), user.getId()));

        return ResponseEntity.ok(Map.of("id", result.registroId().toString(), "estado", result.estado()));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ReinscripcionCiclo> aprobar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        ReinscripcionCiclo r = service.aprobarReinscripcion(id, user.getId());
        return ResponseEntity.ok(r);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ReinscripcionCiclo> rechazar(
            @PathVariable("id") UUID id,
            @RequestBody RechazarRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        ReinscripcionCiclo r = service.rechazarReinscripcion(id, request.getRazonRechazo());
        return ResponseEntity.ok(r);
    }

    @PostMapping("/validar")
    public ResponseEntity<String> validarMasiva(
            @RequestBody ValidarMasivaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        String result = service.validarReinscripcionMasiva(request.getCicloOrigenId(), request.getCicloDestinoId());
        return ResponseEntity.ok(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void requireAdmin(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo DIRECTOR/ADMIN puede ejecutar esta acción");
        }
    }
}

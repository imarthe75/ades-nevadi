package mx.ades.modules.reinscripcion;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reinscripcion")
@RequiredArgsConstructor
public class ReinscripcionController {

    private final ReinscripcionService service;
    private final AdesUserService userService;

    private static final int ROLES_ADMIN = 3; // DIRECTOR o superior

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

    @GetMapping("/{ciclo_destino_id}/estado")
    public ResponseEntity<Map<String, Object>> estadoReinscripcion(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(service.getEstado(cicloDestinoId, estado, plantelId, page, porPagina, user));
    }

    @PostMapping("/{ciclo_destino_id}/validar-masivo")
    public ResponseEntity<Map<String, Object>> validarMasivo(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam("ciclo_origen_id") UUID cicloOrigenId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > ROLES_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo DIRECTOR/ADMIN puede ejecutar reinscripción masiva");
        }

        String resumen = service.validarReinscripcionMasiva(cicloOrigenId, cicloDestinoId);
        return ResponseEntity.ok(Map.of("ok", true, "resumen", resumen));
    }

    @PostMapping("/{ciclo_destino_id}/aprobar-masivo")
    public ResponseEntity<Map<String, Object>> aprobarMasivo(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @RequestParam("ciclo_origen_id") UUID cicloOrigenId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > ROLES_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo DIRECTOR/ADMIN puede aprobar reinscripción masiva");
        }

        Map<String, Object> result = service.aprobarMasivo(cicloOrigenId, cicloDestinoId, user.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{ciclo_destino_id}/reporte")
    public ResponseEntity<Map<String, Object>> reporteReinscripcion(
            @PathVariable("ciclo_destino_id") UUID cicloDestinoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(service.getReporte(cicloDestinoId));
    }

    @PatchMapping("/{registro_id}")
    public ResponseEntity<Map<String, Object>> accionIndividual(
            @PathVariable("registro_id") UUID registroId,
            @RequestBody AccionIndividualPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > ROLES_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para esta acción");
        }

        if (body.getAccion() == null || (!"APROBAR".equals(body.getAccion()) && !"RECHAZAR".equals(body.getAccion()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "accion debe ser APROBAR o RECHAZAR");
        }

        if ("RECHAZAR".equals(body.getAccion()) && (body.getRazonRechazo() == null || body.getRazonRechazo().isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "razon_rechazo es requerida al rechazar");
        }

        Map<String, Object> result = service.patchIndividual(registroId, body.getAccion(), body.getRazonRechazo(), user.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/no-adeudo/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> verificarNoAdeudo(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(service.verificarNoAdeudo(estudianteId, cicloEscolarId));
    }

    // Keep old API compatibility
    @PostMapping("/validar")
    public ResponseEntity<String> validarMasiva(@RequestBody ValidarMasivaRequest request) {
        String result = service.validarReinscripcionMasiva(request.getCicloOrigenId(), request.getCicloDestinoId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ReinscripcionCiclo> aprobar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        ReinscripcionCiclo r = service.aprobarReinscripcion(id, user.getId());
        return ResponseEntity.ok(r);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ReinscripcionCiclo> rechazar(@PathVariable("id") UUID id, @RequestBody RechazarRequest request) {
        ReinscripcionCiclo r = service.rechazarReinscripcion(id, request.getRazonRechazo());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/ciclo/{cicloDestinoId}")
    public ResponseEntity<List<ReinscripcionCiclo>> listarPorCicloDestino(@PathVariable("cicloDestinoId") UUID cicloDestinoId) {
        return ResponseEntity.ok(service.listarPorCicloDestino(cicloDestinoId));
    }
}

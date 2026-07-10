package mx.ades.modules.licencias;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.licencias.application.service.LicenciaApplicationService;
import mx.ades.modules.licencias.domain.model.TipoLicencia;
import mx.ades.modules.licencias.domain.port.in.ResolverLicenciaUseCase;
import mx.ades.modules.licencias.domain.port.in.SolicitarLicenciaUseCase;
import mx.ades.modules.licencias.domain.port.out.LicenciaRepositoryPort;
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
 * Adaptador REST para la gestión de licencias y permisos del personal docente y administrativo.
 * Expone endpoints bajo /api/v1/licencias para listar, crear, actualizar y cancelar licencias
 * (médica, maternidad, paternidad, personal, etc.), así como aprobar o rechazar solicitudes
 * con control de nivel de acceso del autorizador. Requiere JWT válido en todos los endpoints;
 * la aprobación y el rechazo consideran el {@code nivelAcceso} del usuario autenticado.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/licencias")
@RequiredArgsConstructor
public class LicenciaPersonalController {

    private final AdesUserService            userService;
    private final SolicitarLicenciaUseCase   solicitar;
    private final ResolverLicenciaUseCase    resolverLicencia;
    private final LicenciaApplicationService service;
    private final LicenciaRepositoryPort     repo;

    @Data
    public static class LicenciaCreateRequest {
        private UUID      personalId;
        private String    tipoLicencia;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String    motivo;
        private UUID      sustitutoId;
        private Boolean   conGoceSueldo = true;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "personal_id", required = false) UUID personalId,
            @RequestParam(value = "estado",      required = false) String estado,
            @RequestParam(value = "tipo",        required = false) String tipo,
            @RequestParam(value = "q",           required = false) String q,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "30") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(repo.list(personalId, estado, tipo, q, pagina, porPagina));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody LicenciaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new SolicitarLicenciaUseCase.Command(
                body.getPersonalId(),
                TipoLicencia.of(body.getTipoLicencia()),
                body.getFechaInicio(),
                body.getFechaFin(),
                body.getMotivo(),
                body.getSustitutoId(),
                Boolean.TRUE.equals(body.getConGoceSueldo()),
                user.getUsername()
        );
        UUID id = solicitar.solicitar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LicenciaPersonal> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody LicenciaPersonal body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        service.actualizar(id, body, user.getUsername());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<Map<String, Object>> aprobar(
            @PathVariable("id") UUID id,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new ResolverLicenciaUseCase.Command(
                id, ResolverLicenciaUseCase.Accion.APROBAR, observaciones,
                user.getId(), user.getUsername(), nivel);
        resolverLicencia.resolver(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Map<String, Object>> rechazar(
            @PathVariable("id") UUID id,
            @RequestParam("motivo_rechazo") String motivoRechazo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new ResolverLicenciaUseCase.Command(
                id, ResolverLicenciaUseCase.Accion.RECHAZAR, motivoRechazo,
                user.getId(), user.getUsername(), nivel);
        resolverLicencia.resolver(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        service.cancelar(id, user.getUsername());
    }
}

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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate               jdbc;

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
        AdesUser user = userService.resolveUser(jwt);
        // Sin este chequeo, cualquier cuenta autenticada (incluyendo alumnos/padres,
        // nivelAcceso 5) podía listar licencias de todo el personal — motivo,
        // observaciones_rh y fechas son datos de RH sensibles (LFPDPPP).
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        if (user.getNivelAcceso() == 4) {
            // DOCENTE/MEDICO/PREFECTO solo puede ver sus propias licencias, sin
            // importar qué personal_id venga en el query param (evita BOLA).
            personalId = user.getPersonaId();
        }
        // BOLA fix: Coordinador (nivelAcceso 3) veía licencias de personal de CUALQUIER
        // plantel — inconsistente con el mismo umbral (nivel>2 → scoping) ya usado en
        // Kardex/PersonalAdmin/EvaluacionAvanzada. Admin/Director (nivel<=2) mantienen
        // alcance institucional real, igual que en el resto del sistema.
        UUID plantelScope = (user.getNivelAcceso() > 2 && user.getNivelAcceso() != 4) ? user.getPlantelId() : null;
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(repo.list(personalId, estado, tipo, q, pagina, porPagina, plantelScope));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody LicenciaCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(body.getPersonalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede solicitar licencias para sí mismo");
        }
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
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        LicenciaPersonal lp = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(lp.getPersonalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar sus propias licencias");
        }
        verificarPlantelDeLicencia(user, lp);
        return ResponseEntity.ok(lp);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody LicenciaPersonal body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        LicenciaPersonal lp = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(lp.getPersonalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede actualizar sus propias licencias");
        }
        verificarPlantelDeLicencia(user, lp);
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
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        LicenciaPersonal lp = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(lp.getPersonalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede cancelar sus propias licencias");
        }
        verificarPlantelDeLicencia(user, lp);
        service.cancelar(id, user.getUsername());
    }

    /**
     * BOLA fix: Coordinador (nivelAcceso 3) solo puede operar sobre licencias de personal de
     * su propio plantel. Resuelve el plantel vía ades_profesores (ver nota en
     * LicenciaPersistenceAdapter#list — el módulo hoy solo cubre personal docente).
     */
    private void verificarPlantelDeLicencia(AdesUser user, LicenciaPersonal lp) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() != 3 || user.getPlantelId() == null) {
            return;
        }
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_profesores WHERE id = ?", UUID.class, lp.getPersonalId());
        UUID plantelPersonal = rows.isEmpty() ? null : rows.get(0);
        if (plantelPersonal != null && !user.getPlantelId().equals(plantelPersonal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El personal no pertenece a su plantel");
        }
    }
}

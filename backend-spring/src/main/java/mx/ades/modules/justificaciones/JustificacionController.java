package mx.ades.modules.justificaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.justificaciones.domain.model.AccionJustificacion;
import mx.ades.modules.justificaciones.domain.model.TipoJustificacion;
import mx.ades.modules.justificaciones.domain.port.in.RegistrarJustificacionUseCase;
import mx.ades.modules.justificaciones.domain.port.in.ResolverJustificacionUseCase;
import mx.ades.modules.justificaciones.query.JustificacionQueryService;
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

/**
 * Adaptador REST para la gestión de justificaciones de asistencia.
 * Expone endpoints bajo /api/v1/justificaciones para listar justificaciones con filtros
 * (alumno, estado, grupo), registrar una justificación (MEDICA, FAMILIAR, etc.) vinculada
 * a un registro de asistencia, resolver (APROBAR/RECHAZAR) con control de nivel de acceso
 * y consultar el detalle de una justificación individual.
 * Requiere JWT válido; la resolución valida {@code nivelAcceso} del usuario.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/justificaciones")
@RequiredArgsConstructor
public class JustificacionController {

    private final AdesUserService             userService;
    private final RegistrarJustificacionUseCase registrarJustificacion;
    private final ResolverJustificacionUseCase  resolverJustificacion;
    private final JustificacionQueryService     query;

    @Data
    public static class JustificacionCreate {
        @NotNull(message = "asistenciaId es obligatorio")
        private UUID   asistenciaId;
        private String tipoJustificacion = "MEDICA";

        @NotBlank(message = "motivo es obligatorio")
        private String motivo;
        private String documentoUrl;
    }

    @Data
    public static class ResolucionIn {
        @NotBlank(message = "accion es obligatoria")
        private String accion;
        private String motivoRechazo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarJustificaciones(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "estado",        required = false) String estado,
            @RequestParam(value = "grupo_id",      required = false) UUID grupoId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "30") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(query.list(estudianteId, estado, grupoId, pagina, porPagina));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearJustificacion(
            @RequestBody @Valid JustificacionCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new RegistrarJustificacionUseCase.Command(
                body.getAsistenciaId(),
                TipoJustificacion.of(body.getTipoJustificacion()),
                body.getMotivo(),
                body.getDocumentoUrl(),
                user.getId()
        );
        UUID id = registrarJustificacion.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString()));
    }

    @PostMapping("/{justificacionId}/resolver")
    public ResponseEntity<Map<String, Object>> resolverJustificacion(
            @PathVariable("justificacionId") UUID justificacionId,
            @RequestBody @Valid ResolucionIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new ResolverJustificacionUseCase.Command(
                justificacionId,
                AccionJustificacion.of(body.getAccion()),
                body.getMotivoRechazo(),
                user.getId(),
                user.getNivelAcceso()
        );
        String nuevoEstado = resolverJustificacion.resolver(cmd);
        return ResponseEntity.ok(Map.of("estado", nuevoEstado));
    }

    @GetMapping("/{justificacionId}")
    public ResponseEntity<Map<String, Object>> obtenerJustificacion(
            @PathVariable("justificacionId") UUID justificacionId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.findById(justificacionId));
    }
}

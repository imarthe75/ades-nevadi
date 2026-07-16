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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final JdbcTemplate jdbc;

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
        // BFLA fix (asimetría): resolverJustificacion() ya exige nivelAcceso<=3 (COORDINADOR+)
        // y el módulo completo está detrás de roleGuard(3) en el frontend (app.routes.ts); esta
        // lectura no verificaba nada — cualquier usuario autenticado (incl. padres/alumnos)
        // podía listar justificaciones (motivo, documentoUrl de tipo médico) de CUALQUIER
        // alumno del sistema, sin scoping alguno.
        AdesUser user = userService.resolveUser(jwt);
        requireCoordAcademico(user);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        if (estudianteId != null) {
            verificarAccesoEstudiante(user, estudianteId);
            return ResponseEntity.ok(query.list(estudianteId, estado, grupoId, pagina, porPagina, null));
        }
        UUID plantelId = userService.getEffectivePlantelId(user, null);
        return ResponseEntity.ok(query.list(null, estado, grupoId, pagina, porPagina, plantelId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearJustificacion(
            @RequestBody @Valid JustificacionCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BFLA fix (asimetría): mismo hallazgo que listarJustificaciones() — sin esto,
        // cualquier usuario autenticado podía registrar una justificación (con documento y
        // motivo médico/familiar) sobre la asistencia de CUALQUIER alumno.
        requireCoordAcademico(user);
        verificarAccesoAsistencia(user, body.getAsistenciaId());
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
        requireCoordAcademico(user);
        verificarAccesoJustificacion(user, justificacionId);
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
        // BFLA fix (asimetría): mismo hallazgo que listarJustificaciones().
        AdesUser user = userService.resolveUser(jwt);
        requireCoordAcademico(user);
        verificarAccesoJustificacion(user, justificacionId);
        return ResponseEntity.ok(query.findById(justificacionId));
    }

    /**
     * Todo el módulo de justificaciones está detrás de roleGuard(3) en el frontend
     * (app.routes.ts) y resolverJustificacion() ya exige este mismo umbral
     * (nivelAcceso &le; 3, COORDINADOR_ACADEMICO o superior) — se replica aquí para que
     * lectura y creación no dependan únicamente del guard de cliente (que no es una
     * frontera de seguridad real).
     */
    private void requireCoordAcademico(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sin permisos para justificaciones (requiere COORDINADOR+)");
        }
    }

    /**
     * BOLA fix (2026-07-16): requireCoordAcademico() solo verifica nivelAcceso — sin
     * ningún chequeo de plantel, Coordinador/Director/Admin_Plantel de un plantel
     * podía listar/crear/resolver/ver justificaciones (motivo, documento médico) de
     * CUALQUIER alumno de CUALQUIER plantel.
     */
    private void verificarAccesoEstudiante(AdesUser user, UUID estudianteId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        userService.verificarPlantel(user, rows.get(0), "El alumno no pertenece a su plantel");
    }

    private void verificarAccesoAsistencia(AdesUser user, UUID asistenciaId) {
        UUID estudianteId = query.estudianteDeAsistencia(asistenciaId);
        if (estudianteId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asistencia no encontrada");
        verificarAccesoEstudiante(user, estudianteId);
    }

    private void verificarAccesoJustificacion(AdesUser user, UUID justificacionId) {
        UUID asistenciaId = query.asistenciaDeJustificacion(justificacionId);
        if (asistenciaId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Justificación no encontrada");
        verificarAccesoAsistencia(user, asistenciaId);
    }
}

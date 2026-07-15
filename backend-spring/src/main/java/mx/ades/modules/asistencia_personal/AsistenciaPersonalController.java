package mx.ades.modules.asistencia_personal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService;
import mx.ades.modules.asistencia_personal.domain.model.TipoJornada;
import mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.query.AsistenciaPersonalQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Adaptador REST para el control de asistencia del personal docente y administrativo.
 * Expone endpoints bajo /api/v1/asistencia-personal para registrar, actualizar,
 * consultar y eliminar registros de asistencia. Soporta jornadas COMPLETA, MEDIA y
 * otros tipos definidos en {@code TipoJornada}, así como el marcado de retardos
 * (esRetardo/minutosRetardo) y justificaciones. El endpoint de reporte mensual
 * resume la asistencia de una persona por mes y año. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/asistencia-personal")
@RequiredArgsConstructor
public class AsistenciaPersonalController {

    private final AdesUserService userService;
    private final RegistrarAsistenciaUseCase registrarAsistenciaUseCase;
    private final ActualizarAsistenciaUseCase actualizarAsistenciaUseCase;
    private final AsistenciaPersonalApplicationService asistenciaService;
    private final AsistenciaPersonalQueryService queryService;

    /**
     * Registrar/eliminar asistencia de personal es una operación administrativa.
     * Sin este chequeo, cualquier cuenta autenticada (incluyendo padres/alumnos)
     * podía crear o borrar el registro de asistencia de CUALQUIER empleado — BFLA
     * (OWASP API5). El PATCH ya delega en ActualizarAsistenciaUseCase, que valida
     * nivelAcceso &le;3 específicamente para el campo "justificado".
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    @Data
    public static class AsistenciaCreate {
        @NotNull(message = "personaId es obligatorio")
        private UUID personaId;
        @NotNull(message = "fecha es obligatorio")
        private LocalDate fecha;
        private LocalTime horaEntrada;
        private LocalTime horaSalida;
        private String tipoJornada = "COMPLETA";
        private Boolean esRetardo = false;
        private Integer minutosRetardo = 0;
        private String observaciones;
    }

    @Data
    public static class AsistenciaPatch {
        private LocalTime horaEntrada;
        private LocalTime horaSalida;
        private String tipoJornada;
        private Boolean esRetardo;
        private Integer minutosRetardo;
        private Boolean justificado;
        private String justificacion;
        private String observaciones;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarAsistencias(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "fecha_inicio", required = false) LocalDate fechaInicio,
            @RequestParam(value = "fecha_fin", required = false) LocalDate fechaFin,
            @RequestParam(value = "tipo_jornada", required = false) String tipoJornada,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "30") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        // Datos de asistencia de PERSONAL (RH) — sin este chequeo cualquier cuenta
        // autenticada (incluidos alumnos/padres) podía listar la asistencia de cualquier
        // empleado (BOLA, OWASP API1).
        requireStaff(userService.resolveUser(jwt));
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.listar(personaId, fechaInicio, fechaFin, tipoJornada, q, pagina, porPagina));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrarAsistencia(
            @RequestBody @Valid AsistenciaCreate data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        TipoJornada tipoJornada;
        try {
            tipoJornada = TipoJornada.of(data.getTipoJornada());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_jornada inválido");
        }

        RegistrarAsistenciaUseCase.Command cmd = new RegistrarAsistenciaUseCase.Command(
                data.getPersonaId(), data.getFecha(), data.getHoraEntrada(), data.getHoraSalida(),
                tipoJornada,
                Boolean.TRUE.equals(data.getEsRetardo()),
                data.getMinutosRetardo() != null ? data.getMinutosRetardo() : 0,
                data.getObservaciones(), user.getUsername());

        Map<String, Object> result = registrarAsistenciaUseCase.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/reporte")
    public ResponseEntity<Map<String, Object>> reporteMensual(
            @RequestParam("persona_id") UUID personaId,
            @RequestParam("mes") int mes,
            @RequestParam("anio") int anio,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(queryService.reporte(personaId, mes, anio));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalleAsistencia(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        try {
            return ResponseEntity.ok(queryService.detalle(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarAsistencia(
            @PathVariable("id") UUID id,
            @RequestBody AsistenciaPatch data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // El use case solo valida nivelAcceso<=3 para el campo "justificado"; el resto
        // de los campos (horas, retardo, observaciones) no tenían ningún chequeo de rol.
        requireStaff(user);

        TipoJornada tipoJornada = null;
        if (data.getTipoJornada() != null) {
            try {
                tipoJornada = TipoJornada.of(data.getTipoJornada());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_jornada inválido");
            }
        }

        ActualizarAsistenciaUseCase.Patch patch = new ActualizarAsistenciaUseCase.Patch(
                data.getHoraEntrada(), data.getHoraSalida(), tipoJornada,
                data.getEsRetardo(), data.getMinutosRetardo(), data.getObservaciones(),
                data.getJustificado(), data.getJustificacion());

        ActualizarAsistenciaUseCase.Command cmd;
        try {
            cmd = new ActualizarAsistenciaUseCase.Command(id, patch, user.getId(), user.getUsername(), user.getNivelAcceso());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }

        try {
            return ResponseEntity.ok(actualizarAsistenciaUseCase.actualizar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsistencia(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        try {
            asistenciaService.eliminar(id, user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

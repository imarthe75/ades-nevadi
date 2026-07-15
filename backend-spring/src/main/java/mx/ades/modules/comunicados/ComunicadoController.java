package mx.ades.modules.comunicados;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.comunicados.application.service.ComunicadoApplicationService;
import mx.ades.modules.comunicados.domain.port.in.AcusarComunicadoUseCase;
import mx.ades.modules.comunicados.domain.port.in.CrearComunicadoUseCase;
import mx.ades.modules.comunicados.domain.port.in.ProgramarSiguienteUseCase;
import mx.ades.modules.comunicados.query.ComunicadoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de comunicados institucionales.
 * Expone endpoints bajo /api/v1/comunicados para crear, listar, consultar detalle,
 * eliminar y obtener reporte de lectura de comunicados. Soporta acuse de recibo
 * (PUT /{id}/acusar), comunicados recurrentes con programación del siguiente envío
 * y segmentación por plantel, nivel educativo o grupo. Los comunicados pueden
 * requerir acuse y tener fecha de vencimiento. El listado aplica scoping por
 * usuario autenticado. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/comunicados")
@RequiredArgsConstructor
public class ComunicadoController {

    private final AdesUserService             userService;
    private final CrearComunicadoUseCase      crearComunicado;
    private final AcusarComunicadoUseCase     acusarComunicado;
    private final ProgramarSiguienteUseCase   programarSiguiente;
    private final ComunicadoApplicationService service;
    private final ComunicadoQueryService       queryService;
    private final ComunicadoRepository         repository;

    @Data
    public static class ComunicadoCreateRequest {
        @NotBlank(message = "titulo es obligatorio")
        @Size(max = 255, message = "titulo máximo 255 caracteres")
        private String titulo;

        @NotBlank(message = "contenido es obligatorio")
        private String contenido;

        private String tipoComunicado = "GENERAL";
        private UUID plantelId;
        private UUID nivelEducativoId;
        private UUID grupoId;
        private Boolean requiereAcuse = false;
        private LocalDateTime fechaVencimiento;
        private Boolean esRecurrente = false;
        private String periodicidad;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "solo_vigentes", defaultValue = "true") boolean soloVigentes,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(
                queryService.listar(user.getId(), plantelId, nivelEducativoId, tipo, soloVigentes, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(
                queryService.detalle(id, user.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado")));
    }

    @GetMapping("/recurrentes/pendientes")
    public ResponseEntity<List<Map<String, Object>>> recurrentesPendientes(
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordinadorOSuperior(userService.resolveUser(jwt));
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.recurrentesPendientes(pagina, porPagina));
    }

    @GetMapping("/{id}/reporte-lectura")
    public ResponseEntity<Map<String, Object>> reporteLectura(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordinadorOSuperior(userService.resolveUser(jwt));
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
        return ResponseEntity.ok(queryService.reporteLectura(id, c.getTitulo(),
                c.getTotalDestinatarios() != null ? c.getTotalDestinatarios() : 0));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody @Valid ComunicadoCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireCoordinadorOSuperior(user);
        var cmd = new CrearComunicadoUseCase.Command(
                body.getTitulo(), body.getContenido(), body.getTipoComunicado(),
                body.getPlantelId(), body.getNivelEducativoId(), body.getGrupoId(),
                body.getRequiereAcuse(), body.getFechaVencimiento(),
                body.getEsRecurrente(), body.getPeriodicidad(), user.getId());
        UUID id = crearComunicado.crear(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/{id}/acusar")
    public ResponseEntity<Map<String, Object>> acusar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        acusarComunicado.acusar(id, user.getId());
        return ResponseEntity.ok(Map.of("ok", true, "comunicado_id", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireCoordinadorOSuperior(userService.resolveUser(jwt));
        service.eliminar(id);
    }

    @PostMapping("/{id}/programar-siguiente")
    public ResponseEntity<Map<String, Object>> programarSiguiente(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireCoordinadorOSuperior(userService.resolveUser(jwt));
        LocalDateTime siguiente = programarSiguiente.programarSiguiente(id);
        return ResponseEntity.ok(Map.of("proximo_envio", siguiente.toString()));
    }

    /**
     * BFLA fix: crear/eliminar/programar comunicados institucionales (y ver su reporte de
     * lectura o la cola de recurrentes) es operación de personal escolar — antes de este fix
     * ningún endpoint de este controller verificaba nivelAcceso, permitiendo a CUALQUIER
     * usuario autenticado (incluido un padre/alumno nivelAcceso=5) publicar o borrar
     * comunicados institucionales. Mismo umbral que {@code ForoController} para anuncios
     * (Coordinador o superior).
     */
    private void requireCoordinadorOSuperior(AdesUser user) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }
    }
}

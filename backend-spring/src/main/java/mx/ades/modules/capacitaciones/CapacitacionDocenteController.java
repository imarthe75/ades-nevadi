package mx.ades.modules.capacitaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService;
import mx.ades.modules.capacitaciones.domain.model.AreaFormacion;
import mx.ades.modules.capacitaciones.domain.model.ModalidadCapacitacion;
import mx.ades.modules.capacitaciones.domain.model.TipoCertificacion;
import mx.ades.modules.capacitaciones.domain.port.in.RegistrarCapacitacionUseCase;
import mx.ades.modules.capacitaciones.domain.port.in.ValidarCapacitacionUseCase;
import mx.ades.modules.capacitaciones.domain.port.out.CapacitacionRepositoryPort;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el registro y seguimiento de capacitaciones docentes.
 * Expone endpoints bajo /api/v1/capacitaciones para listar, crear, actualizar,
 * eliminar y validar (por RH) registros de capacitación. Soporta modalidades
 * presenciales y en línea (ModalidadCapacitacion), tipos de certificación
 * (TipoCertificacion) y áreas de formación (AreaFormacion). El endpoint
 * /resumen/{docenteId} agrega horas totales, eventos y distribución por tipo
 * y modalidad. La validación RH requiere nivelAcceso adecuado verificado
 * en el use case. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/capacitaciones")
@RequiredArgsConstructor
public class CapacitacionDocenteController {

    private final AdesUserService               userService;
    private final RegistrarCapacitacionUseCase  registrarCapacitacion;
    private final ValidarCapacitacionUseCase    validarCapacitacion;
    private final CapacitacionApplicationService service;
    private final CapacitacionRepositoryPort    repo;
    private final JdbcTemplate                  jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "docente_id", required = false) UUID docenteId,
            @RequestParam(value = "tipo",       required = false) String tipo,
            @RequestParam(value = "modalidad",  required = false) String modalidad,
            @RequestParam(value = "validado",   required = false) Boolean validado,
            @RequestParam(value = "q",          required = false) String q,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "30") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix: un docente (nivel 4) podía listar capacitaciones de CUALQUIER docente
        // pasando un docente_id ajeno (o ninguno, viendo todo el sistema) — inconsistente con
        // las escrituras de este mismo archivo, que ya restringen nivel 4 a sus propios
        // registros. Se fuerza el filtro a su propia persona.
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 4) {
            docenteId = user.getPersonaId();
        }
        // BOLA fix: Coordinador (nivelAcceso 3) veía capacitaciones de docentes de CUALQUIER
        // plantel — mismo criterio que Kardex/PersonalAdmin/Licencias.
        UUID plantelScope = (user.getNivelAcceso() != null && user.getNivelAcceso() == 3)
                ? user.getPlantelId() : null;
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(repo.list(docenteId, tipo, modalidad, validado, q, pagina, porPagina, plantelScope));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody CapacitacionDocente body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(body.getDocenteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede registrar sus propias capacitaciones");
        }
        var cmd = new RegistrarCapacitacionUseCase.Command(
                body.getDocenteId(),
                body.getNombre(),
                TipoCertificacion.of(body.getTipoCertificacion()),
                ModalidadCapacitacion.of(body.getModalidad()),
                AreaFormacion.ofNullable(body.getAreaFormacion()),
                body.getFechaInicio(),
                body.getFechaFin(),
                body.getDuracionHrs(),
                body.getInstitucion(),
                body.getFolioCertificado(),
                body.getCertificadoUrl(),
                user.getUsername()
        );
        UUID id = registrarCapacitacion.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/resumen/{docenteId}")
    public ResponseEntity<Map<String, Object>> resumen(
            @PathVariable("docenteId") UUID docenteId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 4
                && !user.getPersonaId().equals(docenteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar su propio resumen");
        }
        verificarPlantelDocente(user, docenteId);
        List<Map<String, Object>> records = repo.resumen(docenteId);

        double totalHrs = 0.0;
        int validadas = 0;
        Map<String, Double> porTipo = new HashMap<>();
        Map<String, Double> porModalidad = new HashMap<>();

        for (Map<String, Object> r : records) {
            double hrs = ((Number) r.get("duracion_hrs")).doubleValue();
            totalHrs += hrs;
            String t = (String) r.get("tipo_certificacion");
            porTipo.put(t, porTipo.getOrDefault(t, 0.0) + hrs);
            String m = (String) r.get("modalidad");
            porModalidad.put(m, porModalidad.getOrDefault(m, 0.0) + hrs);
            if (Boolean.TRUE.equals(r.get("validado_rh"))) validadas++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("docente_id", docenteId);
        response.put("total_hrs", Math.round(totalHrs * 10.0) / 10.0);
        response.put("total_eventos", records.size());
        response.put("por_tipo", porTipo);
        response.put("por_modalidad", porModalidad);
        response.put("validadas", validadas);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CapacitacionDocente> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        CapacitacionDocente cd = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Capacitación no encontrada"));
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 4
                && !user.getPersonaId().equals(cd.getDocenteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar sus propias capacitaciones");
        }
        verificarPlantelDocente(user, cd.getDocenteId());
        return ResponseEntity.ok(cd);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CapacitacionDocente> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody CapacitacionDocente body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        CapacitacionDocente cd = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Capacitación no encontrada"));
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(cd.getDocenteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede actualizar sus propias capacitaciones");
        }
        verificarPlantelDocente(user, cd.getDocenteId());

        if (body.getNombre() != null)       cd.setNombre(body.getNombre());
        if (body.getDescripcion() != null)  cd.setDescripcion(body.getDescripcion());
        if (body.getInstitucion() != null)  cd.setInstitucion(body.getInstitucion());
        if (body.getDuracionHrs() != null && body.getDuracionHrs().compareTo(java.math.BigDecimal.ZERO) > 0)
            cd.setDuracionHrs(body.getDuracionHrs());
        if (body.getFolioCertificado() != null) cd.setFolioCertificado(body.getFolioCertificado());
        if (body.getCertificadoUrl() != null)   cd.setCertificadoUrl(body.getCertificadoUrl());
        if (body.getAreaFormacion() != null)    cd.setAreaFormacion(body.getAreaFormacion().toUpperCase());
        cd.setUsuarioModificacion(user.getUsername());

        return ResponseEntity.ok(repo.save(cd));
    }

    @PostMapping("/{id}/validar")
    public ResponseEntity<Map<String, Object>> validar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new ValidarCapacitacionUseCase.Command(
                id, user.getId(), user.getUsername(), nivel);
        validarCapacitacion.validar(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        CapacitacionDocente cd = repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Capacitación no encontrada"));
        if (user.getNivelAcceso() == 4 && !user.getPersonaId().equals(cd.getDocenteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede eliminar sus propias capacitaciones");
        }
        verificarPlantelDocente(user, cd.getDocenteId());
        cd.setIsActive(false);
        cd.setUsuarioModificacion(user.getUsername());
        repo.save(cd);
    }

    /**
     * BOLA fix: Coordinador (nivelAcceso 3) solo puede operar sobre capacitaciones de
     * docentes de su propio plantel (docenteId aquí resuelve contra ades_profesores.id).
     */
    private void verificarPlantelDocente(AdesUser user, UUID docenteId) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() != 3 || user.getPlantelId() == null) {
            return;
        }
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_profesores WHERE id = ?", UUID.class, docenteId);
        UUID plantelDocente = rows.isEmpty() ? null : rows.get(0);
        if (plantelDocente != null && !user.getPlantelId().equals(plantelDocente)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El docente no pertenece a su plantel");
        }
    }
}

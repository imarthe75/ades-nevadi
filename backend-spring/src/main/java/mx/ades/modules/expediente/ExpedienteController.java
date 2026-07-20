package mx.ades.modules.expediente;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.expediente.domain.model.CalificacionExtra;
import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.port.in.*;
import mx.ades.modules.expediente.query.ExpedienteQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador REST para bajas, exámenes extraordinarios y constancias de alumnos.
 * El sub-recurso /expediente/* (documentos, OCR, análisis IA) vive en FastAPI
 * ({@code backend/app/api/v1/expediente.py}) — nginx enruta ese prefijo exclusivamente
 * ahí (ver location ~ ^/api/v1/(...|expediente|...) en infrastructure/nginx/nginx.conf).
 * Hasta 2026-07-20 este controller tenía 8 métodos duplicados bajo ese mismo prefijo,
 * inalcanzables en producción — eliminados para no confundir a futuros auditores.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExpedienteController {

    private final AdesUserService userService;
    private final ExpedienteWriteService writeService;
    private final JdbcTemplate jdbc;
    private final RegistrarBajaUseCase registrarBaja;
    private final CalificarExtraordinarioUseCase calificarExtraordinario;
    private final EmitirConstanciaUseCase emitirConstancia;
    private final ExpedienteQueryService queryService;

    @Data
    public static class BajaCreate {
        private String tipoBaja;
        private String motivo;
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

    @Data
    public static class ExtraordinarioCreate {
        private UUID materiaId;
        private UUID cicloEscolarId;
        private UUID grupoId;
        private String tipoExamen = "EXTRAORDINARIO";
        private Double calificacionPrevia;
        private LocalDate fechaExamen;
        private Double calificacion;
        private Boolean acredita;
        private String observaciones;
    }

    @Data
    public static class ConstanciaCreate {
        private String tipoConstancia;
        private UUID cicloEscolarId;
        private String solicitadaPor;
        private String proposito;
        private LocalDate fechaVencimiento;
        private String observaciones;
    }

    // ── Bajas ────────────────────────────────────────────────────────────────

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): este dato (bajas/movilidad de un alumno) no tenía scoping por
        // plantel, a diferencia de buscarDocumentosAlumno() de este mismo controller, que sí
        // verifica que el alumno pertenezca al plantel del usuario.
        verificarAccesoAlumno(user, estudianteId);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.listarBajas(estudianteId, pagina, porPagina));
    }

    @PostMapping("/bajas")
    public ResponseEntity<Map<String, Object>> registrarBaja(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody BajaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteId);

        // fecha_efectiva es NOT NULL en ades_bajas (sin default); antes de este fix no
        // había ningún chequeo y el INSERT fallaba con DataIntegrityViolationException
        // (409 genérico en vez de un 422 claro).
        if (body.getFechaEfectiva() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fechaEfectiva es obligatoria");
        }
        TipoBaja tipo = TipoBaja.of(body.getTipoBaja());
        RegistrarBajaUseCase.Result result = registrarBaja.ejecutar(
                new RegistrarBajaUseCase.Command(
                        estudianteId, tipo, body.getMotivo(),
                        body.getFechaEfectiva(), body.getFechaReingreso(),
                        body.getPlantelDestino(), body.getClaveCtDestino(),
                        body.getObservaciones(), user.getId(), user.getId().toString()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.bajaId(),
                "estudiante_id", estudianteId,
                "tipo_baja", tipo.name(),
                "motivo", body.getMotivo() != null ? body.getMotivo() : "",
                "fecha_efectiva", body.getFechaEfectiva(),
                "estudiante_desactivado", result.estudianteDesactivado()));
    }

    // ── Extraordinarios ───────────────────────────────────────────────────────

    @GetMapping("/extraordinarias")
    public ResponseEntity<List<Map<String, Object>>> listarExtraordinarias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteId);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.listarExtraordinarios(estudianteId, cicloId, pagina, porPagina));
    }

    @PostMapping("/extraordinarias")
    public ResponseEntity<Map<String, Object>> registrarExtraordinario(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ExtraordinarioCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteId);

        // materia_id y ciclo_escolar_id son NOT NULL en ades_extraordinarias; validar aquí
        // evita un 409 de integridad de datos confuso y da un 422 claro en su lugar.
        if (body.getMateriaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "materia_id es obligatorio");
        }
        if (body.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "ciclo_escolar_id es obligatorio");
        }

        UUID newId = writeService.insertExtraordinario(
            estudianteId, body.getMateriaId(), body.getCicloEscolarId(), body.getGrupoId(),
            body.getTipoExamen(), body.getCalificacionPrevia(), body.getFechaExamen(),
            body.getCalificacion(), body.getAcredita(), body.getObservaciones(),
            user.getId(), user.getId().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", newId,
                "estudiante_id", estudianteId,
                "materia_id", body.getMateriaId() != null ? body.getMateriaId() : "",
                "tipo_examen", body.getTipoExamen()));
    }

    @PatchMapping("/extraordinarias/{extra_id}")
    public ResponseEntity<Map<String, Object>> calificarExtraordinario(
            @PathVariable("extra_id") UUID extraId,
            @RequestParam("calificacion") Double calificacion,
            @RequestParam("acredita") Boolean acredita,
            @RequestParam(value = "fecha_examen", required = false) LocalDate fechaExamen,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteIdDeExtraordinario(extraId));

        calificarExtraordinario.ejecutar(new CalificarExtraordinarioUseCase.Command(
                extraId, CalificacionExtra.of(calificacion), acredita, fechaExamen,
                user.getId().toString()));

        List<Map<String, Object>> updated = queryService.fetchExtraordinarioById(extraId);
        return ResponseEntity.ok(updated.get(0));
    }

    // ── Constancias ───────────────────────────────────────────────────────────

    @GetMapping("/constancias")
    public ResponseEntity<List<Map<String, Object>>> listarConstancias(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteId);
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.listarConstancias(estudianteId, pagina, porPagina));
    }

    @PostMapping("/constancias")
    public ResponseEntity<Map<String, Object>> emitirConstancia(
            @RequestParam("estudiante_id") UUID estudianteId,
            @RequestBody ConstanciaCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteId);

        EmitirConstanciaUseCase.Result result = emitirConstancia.ejecutar(
                new EmitirConstanciaUseCase.Command(
                        estudianteId, body.getTipoConstancia(), body.getCicloEscolarId(),
                        body.getSolicitadaPor(), body.getProposito(), body.getFechaVencimiento(),
                        body.getObservaciones(), user.getId(), user.getId().toString()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.constanciaId(),
                "estudiante_id", estudianteId,
                "tipo_constancia", body.getTipoConstancia(),
                "folio", result.folio(),
                "fecha_emision", LocalDate.now()));
    }

    @PatchMapping("/constancias/{constancia_id}/entregar")
    public ResponseEntity<Map<String, Object>> marcarEntregada(
            @PathVariable("constancia_id") UUID constanciaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (asimetría): mismo hallazgo que listarBajas() — sin scoping por plantel.
        verificarAccesoAlumno(user, estudianteIdDeConstancia(constanciaId));

        writeService.marcarConstanciaEntregada(constanciaId);
        List<Map<String, Object>> updated = queryService.fetchConstanciaById(constanciaId);
        if (updated.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no encontrada");
        return ResponseEntity.ok(updated.get(0));
    }

    private void verificarAccesoAlumno(AdesUser user, UUID estudianteId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            // BOLA fix: "alcance institucional" no significa cross-plantel — personal
            // escolar sigue acotado a su propio plantel (mismo criterio que
            // BadgeController#requireAccesoAlumno).
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            userService.verificarPlantel(user, plantelRows.get(0), "El alumno no pertenece a su plantel");
            return;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes e WHERE e.id = ? AND (" +
                "  e.persona_id = ? OR EXISTS (" +
                "    SELECT 1 FROM ades_tutores_alumnos ta JOIN ades_personas p ON p.id = ta.persona_id " +
                "    WHERE ta.alumno_id = e.id AND ta.is_active = TRUE AND p.email_personal = ?" +
                "  )" +
                ")", Integer.class, estudianteId, user.getPersonaId(), user.getEmail());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para ver el expediente de este estudiante");
        }
    }

    private UUID estudianteIdDeExtraordinario(UUID extraId) {
        try {
            return jdbc.queryForObject(
                    "SELECT estudiante_id FROM ades_extraordinarias WHERE id = ? AND is_active = TRUE",
                    UUID.class, extraId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraordinario no encontrado");
        }
    }

    private UUID estudianteIdDeConstancia(UUID constanciaId) {
        try {
            return jdbc.queryForObject(
                    "SELECT estudiante_id FROM ades_constancias WHERE id = ? AND is_active = TRUE",
                    UUID.class, constanciaId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no encontrada");
        }
    }

}

package mx.ades.modules.comunicados;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.common.PushService;
import mx.ades.modules.comunicados.domain.model.Periodicidad;
import mx.ades.modules.comunicados.query.ComunicadoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/comunicados")
@RequiredArgsConstructor
public class ComunicadoController {

    private final ComunicadoRepository repository;
    private final AcuseComunicadoRepository acuseRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final PushService pushService;
    private final ComunicadoQueryService queryService;

    @Data
    public static class ComunicadoCreateRequest {
        private String titulo;
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

    // ── Reads ─────────────────────────────────────────────────────────────────

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
    public ResponseEntity<List<Map<String, Object>>> recurrentesPendientes() {
        return ResponseEntity.ok(queryService.recurrentesPendientes());
    }

    @GetMapping("/{id}/reporte-lectura")
    public ResponseEntity<Map<String, Object>> reporteLectura(@PathVariable("id") UUID id) {
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
        return ResponseEntity.ok(queryService.reporteLectura(id, c.getTitulo(),
                c.getTotalDestinatarios() != null ? c.getTotalDestinatarios() : 0));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Comunicado> crear(
            @RequestBody ComunicadoCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        Comunicado c = new Comunicado();
        c.setTitulo(body.getTitulo());
        c.setContenido(body.getContenido());
        c.setTipoComunicado(body.getTipoComunicado());
        c.setPlantelId(body.getPlantelId());
        c.setNivelEducativoId(body.getNivelEducativoId());
        c.setGrupoId(body.getGrupoId());
        c.setRequiereAcuse(body.getRequiereAcuse());
        c.setFechaVencimiento(body.getFechaVencimiento());
        c.setEsRecurrente(body.getEsRecurrente());
        c.setPeriodicidad(body.getEsRecurrente() ? body.getPeriodicidad() : null);
        c.setCreadoPorId(user.getId());

        if (c.getEsRecurrente() && c.getPeriodicidad() != null) {
            c.setProximoEnvio(LocalDateTime.now());
        }

        Comunicado saved = repository.save(c);
        triggerPushNotificationAsync(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/acusar")
    public ResponseEntity<Map<String, Object>> acusar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        Optional<AcuseComunicado> existing = acuseRepository.findByComunicadoIdAndUsuarioId(id, user.getId());
        if (existing.isEmpty()) {
            AcuseComunicado ac = new AcuseComunicado();
            ac.setComunicado(repository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado")));
            ac.setUsuarioId(user.getId());
            ac.setFechaAcuse(LocalDateTime.now());
            ac.setIpOrigen("127.0.0.1");
            acuseRepository.save(ac);
        }
        return ResponseEntity.ok(Map.of("ok", true, "comunicado_id", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id) {
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
        c.setIsActive(false);
        repository.save(c);
    }

    @PostMapping("/{id}/programar-siguiente")
    public ResponseEntity<Map<String, Object>> programarSiguiente(@PathVariable("id") UUID id) {
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));

        if (c.getPeriodicidad() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comunicado no tiene periodicidad configurada");
        }

        LocalDateTime base = c.getProximoEnvio() != null ? c.getProximoEnvio() : LocalDateTime.now();
        LocalDateTime siguiente = Periodicidad.of(c.getPeriodicidad()).calcularSiguiente(base);

        c.setProximoEnvio(siguiente);
        repository.save(c);
        return ResponseEntity.ok(Map.of("proximo_envio", siguiente.toString(), "periodicidad", c.getPeriodicidad()));
    }

    // ── Push notification async (mantener sin cambios) ────────────────────────

    private void triggerPushNotificationAsync(Comunicado c) {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.initialize();
        executor.execute(() -> {
            try {
                List<UUID> recipientIds;
                if (c.getGrupoId() != null) {
                    recipientIds = jdbc.queryForList(
                            "SELECT DISTINCT u.id FROM ades_usuarios u " +
                            "WHERE u.id IN (SELECT u2.id FROM ades_usuarios u2 JOIN ades_estudiantes est ON est.id = (" +
                            "SELECT i.estudiante_id FROM ades_inscripciones i WHERE i.grupo_id = ? AND i.is_active = TRUE LIMIT 1))",
                            UUID.class, c.getGrupoId());
                } else if (c.getNivelEducativoId() != null) {
                    recipientIds = jdbc.queryForList(
                            "SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.nivel_educativo_id = ? LIMIT 500",
                            UUID.class, c.getNivelEducativoId());
                } else if (c.getPlantelId() != null) {
                    recipientIds = jdbc.queryForList(
                            "SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.plantel_id = ? OR u.nivel_acceso <= 2 LIMIT 500",
                            UUID.class, c.getPlantelId());
                } else {
                    recipientIds = jdbc.queryForList("SELECT DISTINCT id FROM ades_usuarios LIMIT 500", UUID.class);
                }

                String prioridad = "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "high" : "default";
                pushService.sendBatchAsync(recipientIds,
                        "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "Nuevo comunicado urgente" : "Nuevo comunicado",
                        c.getTitulo(), prioridad,
                        List.of("comunicado", c.getTipoComunicado().toLowerCase()),
                        "https://ades.setag.mx/comunicados");
            } catch (Exception ignored) {}
        });
    }
}

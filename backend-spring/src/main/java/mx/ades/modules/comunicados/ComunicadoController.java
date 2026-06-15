package mx.ades.modules.comunicados;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.common.PushService;
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

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "solo_vigentes", defaultValue = "true") boolean soloVigentes,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID uid = user.getId();

        StringBuilder query = new StringBuilder("SELECT c.id, c.titulo, c.contenido, c.tipo_comunicado, " +
                "c.plantel_id, c.nivel_educativo_id, c.grupo_id, c.requiere_acuse, c.fecha_publicacion, c.fecha_vencimiento, " +
                "COUNT(a.id) FILTER (WHERE a.id IS NOT NULL) AS total_acuses, " +
                "BOOL_OR(a.usuario_id = ?) AS acusado_por_mi, " +
                "u.nombre_usuario AS creado_por_nombre " +
                "FROM ades_comunicados c " +
                "LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id " +
                "LEFT JOIN ades_usuarios u ON u.id = c.creado_por_id " +
                "WHERE c.is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        params.add(uid);

        if (plantelId != null) {
            query.append("AND (c.plantel_id IS NULL OR c.plantel_id = ?) ");
            params.add(plantelId);
        }
        if (nivelEducativoId != null) {
            query.append("AND (c.nivel_educativo_id IS NULL OR c.nivel_educativo_id = ?) ");
            params.add(nivelEducativoId);
        }
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND c.tipo_comunicado = ? ");
            params.add(tipo);
        }
        if (soloVigentes) {
            query.append("AND (c.fecha_vencimiento IS NULL OR c.fecha_vencimiento > NOW()) ");
        }

        query.append("GROUP BY c.id, u.nombre_usuario ORDER BY c.fecha_publicacion DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> rows = jdbc.queryForList(query.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

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

        // Async Push Notification Trigger
        triggerPushNotificationAsync(saved);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID uid = user.getId();

        String sql = "SELECT c.*, " +
                "COUNT(a.id) AS total_acuses, " +
                "BOOL_OR(a.usuario_id = ?) AS acusado_por_mi " +
                "FROM ades_comunicados c " +
                "LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id " +
                "WHERE c.id = ? AND c.is_active = TRUE " +
                "GROUP BY c.id";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, uid, id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PutMapping("/{id}/acusar")
    public ResponseEntity<Map<String, Object>> acusar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID uid = user.getId();

        Optional<AcuseComunicado> existing = acuseRepository.findByComunicadoIdAndUsuarioId(id, uid);
        if (existing.isEmpty()) {
            AcuseComunicado ac = new AcuseComunicado();
            ac.setComunicado(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado")));
            ac.setUsuarioId(uid);
            ac.setFechaAcuse(LocalDateTime.now());
            ac.setIpOrigen("127.0.0.1"); // Simple local fallback
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

    @GetMapping("/recurrentes/pendientes")
    public ResponseEntity<List<Map<String, Object>>> recurrentesPendientes() {
        String sql = "SELECT id, titulo, tipo_comunicado, periodicidad, proximo_envio, fecha_publicacion, plantel_id, nivel_educativo_id " +
                "FROM ades_comunicados WHERE es_recurrente = TRUE AND is_active = TRUE ORDER BY proximo_envio ASC NULLS LAST";
        return ResponseEntity.ok(jdbc.queryForList(sql));
    }

    @PostMapping("/{id}/programar-siguiente")
    public ResponseEntity<Map<String, Object>> programarSiguiente(@PathVariable("id") UUID id) {
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));

        if (c.getPeriodicidad() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comunicado no tiene periodicidad configurada");
        }

        LocalDateTime base = c.getProximoEnvio() != null ? c.getProximoEnvio() : LocalDateTime.now();
        LocalDateTime siguiente;

        switch (c.getPeriodicidad().toUpperCase()) {
            case "DIARIA":
                siguiente = base.plusDays(1);
                break;
            case "SEMANAL":
                siguiente = base.plusWeeks(1);
                break;
            case "QUINCENAL":
                siguiente = base.plusDays(15);
                break;
            case "MENSUAL":
                siguiente = base.plusMonths(1);
                break;
            case "TRIMESTRAL":
                siguiente = base.plusMonths(3);
                break;
            default:
                siguiente = base.plusMonths(1);
        }

        c.setProximoEnvio(siguiente);
        repository.save(c);

        return ResponseEntity.ok(Map.of("proximo_envio", siguiente.toString(), "periodicidad", c.getPeriodicidad()));
    }

    @GetMapping("/{id}/reporte-lectura")
    public ResponseEntity<Map<String, Object>> reporteLectura(@PathVariable("id") UUID id) {
        Comunicado c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));

        String acusesSql = "SELECT COUNT(*) AS leidos, COUNT(DISTINCT ac.usuario_id) AS usuarios_distintos " +
                "FROM ades_acuses_comunicado ac WHERE ac.comunicado_id = ? AND ac.is_active = TRUE";
        Map<String, Object> acuses = jdbc.queryForMap(acusesSql, id);

        long leidos = ((Number) acuses.get("leidos")).longValue();
        long total = c.getTotalDestinatarios() != null ? c.getTotalDestinatarios() : 0;
        double pct = total > 0 ? Math.round(((double) leidos / total * 100.0) * 10.0) / 10.0 : 0.0;

        String detalleSql = "SELECT u.nombre_usuario, ac.fecha_acuse, ac.ip_origen " +
                "FROM ades_acuses_comunicado ac JOIN ades_usuarios u ON u.id = ac.usuario_id " +
                "WHERE ac.comunicado_id = ? AND ac.is_active = TRUE ORDER BY ac.fecha_acuse DESC LIMIT 200";
        List<Map<String, Object>> detalle = jdbc.queryForList(detalleSql, id);

        Map<String, Object> response = new HashMap<>();
        response.put("comunicado_id", id);
        response.put("titulo", c.getTitulo());
        response.put("total_destinatarios", total);
        response.put("total_leidos", leidos);
        response.put("pct_lectura", pct);
        response.put("detalle", detalle);

        return ResponseEntity.ok(response);
    }

    private void triggerPushNotificationAsync(Comunicado c) {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.initialize();
        executor.execute(() -> {
            try {
                List<UUID> recipientIds = new ArrayList<>();
                if (c.getGrupoId() != null) {
                    recipientIds = jdbc.queryForList("SELECT DISTINCT u.id FROM ades_usuarios u " +
                            "WHERE u.id IN (SELECT u2.id FROM ades_usuarios u2 JOIN ades_estudiantes est ON est.id = (" +
                            "SELECT i.estudiante_id FROM ades_inscripciones i WHERE i.grupo_id = ? AND i.is_active = TRUE LIMIT 1))", UUID.class, c.getGrupoId());
                } else if (c.getNivelEducativoId() != null) {
                    recipientIds = jdbc.queryForList("SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.nivel_educativo_id = ? LIMIT 500", UUID.class, c.getNivelEducativoId());
                } else if (c.getPlantelId() != null) {
                    recipientIds = jdbc.queryForList("SELECT DISTINCT u.id FROM ades_usuarios u WHERE u.plantel_id = ? OR u.nivel_acceso <= 2 LIMIT 500", UUID.class, c.getPlantelId());
                } else {
                    recipientIds = jdbc.queryForList("SELECT DISTINCT id FROM ades_usuarios LIMIT 500", UUID.class);
                }

                String prioridad = "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "high" : "default";
                pushService.sendBatchAsync(recipientIds,
                        "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "🚨 Nuevo comunicado" : "Nuevo comunicado",
                        c.getTitulo(),
                        prioridad,
                        List.of("comunicado", c.getTipoComunicado().toLowerCase()),
                        "https://ades.setag.mx/comunicados");
            } catch (Exception ex) {
                // Silently absorb like python code
            }
        });
    }
}

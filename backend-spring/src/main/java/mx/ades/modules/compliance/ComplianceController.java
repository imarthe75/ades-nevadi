package mx.ades.modules.compliance;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.compliance.query.ComplianceQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
public class ComplianceController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final ComplianceQueryService queryService;

    private static final int NIVEL_ADMIN = 2;
    private static final int NIVEL_DIRECTOR = 3;

    @Data
    public static class NormatividadPayload {
        private String nombre;
        private String tipo;
        private String descripcion;
        private String fechaVigenciaInicio;
        private String fechaVigenciaFin;
        private String urlDocumento;
        private Boolean aplicaPrimaria = true;
        private Boolean aplicaSecundaria = true;
        private Boolean aplicaPreparatoria = true;
    }

    @Data
    public static class RetencionPayload {
        private UUID alumnoId;
        private String tipoRetencion;
        private String motivo;
        private String fechaInicio;
        private String fechaFin;
        private String accionesRequeridas;
    }

    @Data
    public static class AlertaCumplimientoPayload {
        private String tipoAlerta;
        private String descripcion;
        private UUID alumnoId;
        private UUID plantelId;
        private String severidad = "MEDIA";
        private Boolean requiereAccion = true;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-007: Historial de logins
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/login-auditoria")
    public ResponseEntity<List<Map<String, Object>>> historialLogins(
            @RequestParam(value = "usuario", required = false) String usuario,
            @RequestParam(value = "desde", required = false) String desde,
            @RequestParam(value = "hasta", required = false) String hasta,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin Plantel o superior");
        }
        return ResponseEntity.ok(queryService.historialLogins(usuario, desde, hasta, skip, limit));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-013: Estadísticas de sistema
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/estadisticas-sistema")
    public ResponseEntity<Map<String, Object>> estadisticasSistema(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(queryService.estadisticasSistema(plantelId, cicloId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-014: Normatividad
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/normatividad")
    public ResponseEntity<List<Map<String, Object>>> listarNormatividad(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "vigente", defaultValue = "true") boolean vigente,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.normatividad(tipo, vigente));
    }

    @PostMapping("/normatividad")
    public ResponseEntity<Map<String, Object>> registrarNormativa(
            @RequestBody NormatividadPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Admin o superior");
        }

        UUID id = UUID.randomUUID();
        LocalDate fi = data.getFechaVigenciaInicio() != null ? LocalDate.parse(data.getFechaVigenciaInicio()) : LocalDate.now();
        LocalDate ff = data.getFechaVigenciaFin() != null ? LocalDate.parse(data.getFechaVigenciaFin()) : null;

        jdbc.update(
                "INSERT INTO ades_normatividad " +
                        "(id, nombre, tipo, descripcion, fecha_vigencia_inicio, fecha_vigencia_fin, " +
                        "url_documento, aplica_primaria, aplica_secundaria, aplica_preparatoria, " +
                        "usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.getNombre(), data.getTipo(), data.getDescripcion(), fi, ff,
                data.getUrlDocumento(), data.getAplicaPrimaria(), data.getAplicaSecundaria(), data.getAplicaPreparatoria(),
                user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Normativa registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-017: Retenciones
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/retenciones")
    public ResponseEntity<List<Map<String, Object>>> listarRetenciones(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "activas", defaultValue = "true") boolean activas,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(queryService.retenciones(plantelId, tipo, activas, skip, limit));
    }

    @PostMapping("/retenciones")
    public ResponseEntity<Map<String, Object>> crearRetencion(
            @RequestBody RetencionPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        LocalDate fi = data.getFechaInicio() != null ? LocalDate.parse(data.getFechaInicio()) : LocalDate.now();
        LocalDate ff = data.getFechaFin() != null ? LocalDate.parse(data.getFechaFin()) : null;

        jdbc.update(
                "INSERT INTO ades_retenciones " +
                        "(id, alumno_id, tipo_retencion, motivo, fecha_inicio, fecha_fin, " +
                        "acciones_requeridas, autorizado_por, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.getAlumnoId(), data.getTipoRetencion(), data.getMotivo(), fi, ff,
                data.getAccionesRequeridas(), user.getPersonaId(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Retención registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AD-030: Alertas de cumplimiento
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/alerta-cumplimiento")
    public ResponseEntity<Map<String, Object>> crearAlerta(
            @RequestBody AlertaCumplimientoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_alertas_cumplimiento " +
                        "(id, tipo_alerta, descripcion, alumno_id, plantel_id, severidad, " +
                        "requiere_accion, estado, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, data.getTipoAlerta(), data.getDescripcion(), data.getAlumnoId(), data.getPlantelId(),
                data.getSeveridad(), data.getRequiereAccion(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Alerta de cumplimiento registrada"));
    }

    @GetMapping("/alerta-cumplimiento")
    public ResponseEntity<List<Map<String, Object>>> listarAlertas(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "severidad", required = false) String severidad,
            @RequestParam(value = "estado", defaultValue = "PENDIENTE") String estado,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(queryService.alertas(plantelId, severidad, estado, skip, limit));
    }
}

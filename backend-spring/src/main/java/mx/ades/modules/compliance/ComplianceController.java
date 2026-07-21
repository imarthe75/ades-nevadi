package mx.ades.modules.compliance;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.compliance.domain.model.SeveridadAlerta;
import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;
import mx.ades.modules.compliance.query.ComplianceQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el módulo de cumplimiento normativo institucional.
 * Expone endpoints bajo /api/v1/compliance para gestión de normatividad SEP/UAEMEX,
 * retenciones de alumnos, alertas de cumplimiento, historial de logins (auditoría
 * de acceso) y estadísticas de sistema. El historial de logins requiere
 * nivelAcceso {@literal <=} 2 (ADMIN). Las estadísticas y las alertas requieren
 * nivelAcceso {@literal <=} 3 (Director). Las alertas tienen severidades
 * LEVE/MEDIA/GRAVE. Cumple con LFPDPPP para protección de datos personales.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
public class ComplianceController {

    private final AdesUserService             userService;
    private final RegistrarNormativaUseCase   registrarNormativa;
    private final RegistrarRetencionUseCase   registrarRetencion;
    private final CrearAlertaUseCase          crearAlerta;
    private final ComplianceQueryService      queryService;
    private final CumplimientoDashboardService dashboardService;

    private static final int NIVEL_ADMIN    = 2;
    private static final int NIVEL_DIRECTOR = 3;

    @Data
    public static class NormatividadPayload {
        private String nombre;
        private String tipo;
        private String descripcion;
        private String fechaVigenciaInicio;
        private String fechaVigenciaFin;
        private String urlDocumento;
        private Boolean aplicaPrimaria    = true;
        private Boolean aplicaSecundaria  = true;
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

    @GetMapping("/login-auditoria")
    public ResponseEntity<List<Map<String, Object>>> historialLogins(
            @RequestParam(value = "usuario",  required = false) String usuario,
            @RequestParam(value = "desde",    required = false) String desde,
            @RequestParam(value = "hasta",    required = false) String hasta,
            @RequestParam(value = "skip",  defaultValue = "0")   int skip,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queryService.historialLogins(usuario, desde, hasta, skip, limit));
    }

    @GetMapping("/estadisticas-sistema")
    public ResponseEntity<Map<String, Object>> estadisticasSistema(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "ciclo_id",   required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queryService.estadisticasSistema(plantelId, cicloId));
    }

    @GetMapping("/normatividad")
    public ResponseEntity<List<Map<String, Object>>> listarNormatividad(
            @RequestParam(value = "tipo",    required = false)    String tipo,
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
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        LocalDate fi = data.getFechaVigenciaInicio() != null ? mx.ades.common.ValidationUtils.parseFechaFlexible(data.getFechaVigenciaInicio(), "fechaVigenciaInicio") : LocalDate.now();
        LocalDate ff = data.getFechaVigenciaFin() != null ? mx.ades.common.ValidationUtils.parseFechaFlexible(data.getFechaVigenciaFin(), "fechaVigenciaFin") : null;
        var cmd = new RegistrarNormativaUseCase.Command(
                data.getNombre(), data.getTipo(), data.getDescripcion(), fi, ff,
                data.getUrlDocumento(),
                Boolean.TRUE.equals(data.getAplicaPrimaria()),
                Boolean.TRUE.equals(data.getAplicaSecundaria()),
                Boolean.TRUE.equals(data.getAplicaPreparatoria()),
                user.getUsername(), nivel);
        UUID id = registrarNormativa.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Normativa registrada"));
    }

    @GetMapping("/retenciones")
    public ResponseEntity<List<Map<String, Object>>> listarRetenciones(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo",        required = false) String tipo,
            @RequestParam(value = "activas", defaultValue = "true") boolean activas,
            @RequestParam(value = "skip",  defaultValue = "0")  int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queryService.retenciones(plantelId, tipo, activas, skip, limit));
    }

    @PostMapping("/retenciones")
    public ResponseEntity<Map<String, Object>> crearRetencion(
            @RequestBody RetencionPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        LocalDate fi = data.getFechaInicio() != null ? mx.ades.common.ValidationUtils.parseFechaFlexible(data.getFechaInicio(), "fechaInicio") : LocalDate.now();
        LocalDate ff = data.getFechaFin() != null ? mx.ades.common.ValidationUtils.parseFechaFlexible(data.getFechaFin(), "fechaFin") : null;
        var cmd = new RegistrarRetencionUseCase.Command(
                data.getAlumnoId(), data.getTipoRetencion(), data.getMotivo(), fi, ff,
                data.getAccionesRequeridas(), user.getPersonaId(), user.getUsername(), nivel);
        UUID id = registrarRetencion.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Retención registrada"));
    }

    @PostMapping("/alerta-cumplimiento")
    public ResponseEntity<Map<String, Object>> crearAlerta(
            @RequestBody AlertaCumplimientoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        var cmd = new CrearAlertaUseCase.Command(
                data.getTipoAlerta(), data.getDescripcion(), data.getAlumnoId(), data.getPlantelId(),
                SeveridadAlerta.of(data.getSeveridad()),
                Boolean.TRUE.equals(data.getRequiereAccion()),
                user.getUsername(), nivel);
        UUID id = crearAlerta.crear(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Alerta de cumplimiento registrada"));
    }

    @GetMapping("/alerta-cumplimiento")
    public ResponseEntity<List<Map<String, Object>>> listarAlertas(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "severidad",  required = false) String severidad,
            @RequestParam(value = "estado",  defaultValue = "PENDIENTE") String estado,
            @RequestParam(value = "skip",  defaultValue = "0")  int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queryService.alertas(plantelId, severidad, estado, skip, limit));
    }

    /** AD-014: dashboard de cumplimiento SEP/UAEMEX — agrega piezas ya existentes. */
    @GetMapping("/dashboard-cumplimiento")
    public ResponseEntity<Map<String, Object>> dashboardCumplimiento(@AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_DIRECTOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(dashboardService.resumen());
    }
}

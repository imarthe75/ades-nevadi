package mx.ades.modules.stats;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.stats.query.StatsQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para métricas y KPIs del sistema ADES.
 * Expone endpoints bajo /api/v1/stats para resumen general de alumnos/grupos/materias,
 * distribución estadística por plantel, información del servidor (tamaño de BD),
 * telemetría completa (BD, conexiones, disco, JVM, colas Celery — AD-030) y
 * el dashboard de dirección con KPIs, avance por grado y avance por asignatura.
 * Los endpoints de telemetría y dashboard de dirección requieren nivelAcceso &le;2
 * (Director o superior). Resuelve plantel desde el claim JWT o el parámetro de query.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService queryService;
    private final AdesUserService userService;

    @GetMapping("/resumen")
    public Map<String, Object> resumen(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        AdesUser user = userService.resolveUser(jwt);
        return queryService.resumen(userService.getEffectivePlantelId(user, plantelId));
    }

    @GetMapping("/distribucion")
    public List<Map<String, Object>> distribucion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        AdesUser user = userService.resolveUser(jwt);
        return queryService.distribucion(userService.getEffectivePlantelId(user, plantelId));
    }

    @GetMapping("/servidor")
    public Map<String, Object> servidor(@AuthenticationPrincipal Jwt jwt) {
        requireNivel(userService.resolveUser(jwt), "Solo directores y administradores pueden ver la telemetría");
        long dbSize = queryService.databaseSizeBytes();
        return Map.of(
                "database_size_bytes", dbSize,
                "database_size_mb", dbSize / 1_048_576.0
        );
    }

    // AD-030 — Telemetría completa: BD, conexiones, disco, JVM, colas Celery
    @GetMapping("/telemetria")
    public Map<String, Object> telemetria(@AuthenticationPrincipal Jwt jwt) {
        requireNivel(userService.resolveUser(jwt), "Solo directores y administradores pueden ver la telemetría");
        return queryService.telemetria();
    }

    @GetMapping("/director/kpis")
    public Map<String, Object> directorKPIs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, "Solo directores y administradores pueden ver el dashboard de dirección");
        return queryService.directorKPIs(userService.getEffectivePlantelId(user, plantelId));
    }

    @GetMapping("/director/avance-grado")
    public List<Map<String, Object>> directorAvanceGrado(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, "Solo directores y administradores pueden ver el dashboard de dirección");
        return queryService.directorAvanceGrado(userService.getEffectivePlantelId(user, plantelId));
    }

    @GetMapping("/director/avance-asignatura")
    public List<Map<String, Object>> directorAvanceAsignatura(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, "Solo directores y administradores pueden ver el dashboard de dirección");
        return queryService.directorAvanceAsignatura(userService.getEffectivePlantelId(user, plantelId));
    }

    private void requireNivel(AdesUser user, String mensaje) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
        }
    }
}

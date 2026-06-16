package mx.ades.modules.stats;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.stats.query.StatsQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsQueryService queryService;

    @GetMapping("/resumen")
    public Map<String, Object> resumen(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {

        UUID pid = resolveUUID(jwt.getClaimAsString("plantel"), plantelId);
        return queryService.resumen(pid);
    }

    @GetMapping("/distribucion")
    public List<Map<String, Object>> distribucion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {

        UUID pid = resolveUUID(jwt.getClaimAsString("plantel"), plantelId);
        return queryService.distribucion(pid);
    }

    @GetMapping("/servidor")
    public Map<String, Object> servidor(@AuthenticationPrincipal Jwt jwt) {
        _requireNivelAcceso(jwt, 2, "Solo directores y administradores pueden ver la telemetría");
        long dbSize = queryService.databaseSizeBytes();
        return Map.of(
                "database_size_bytes", dbSize,
                "database_size_mb", dbSize / 1_048_576.0
        );
    }

    // AD-030 — Telemetría completa: BD, conexiones, disco, JVM, colas Celery
    @GetMapping("/telemetria")
    public Map<String, Object> telemetria(@AuthenticationPrincipal Jwt jwt) {
        _requireNivelAcceso(jwt, 2, "Solo directores y administradores pueden ver la telemetría");
        return queryService.telemetria();
    }

    private void _requireNivelAcceso(Jwt jwt, int maxNivel, String mensaje) {
        Object nivel = jwt.getClaim("nivel_acceso");
        int nivelAcceso = nivel instanceof Number n ? n.intValue() : 99;
        if (nivelAcceso > maxNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
        }
    }

    private UUID resolveUUID(String claim, UUID fallback) {
        if (claim != null && !claim.isBlank()) {
            try { return UUID.fromString(claim); } catch (IllegalArgumentException ignored) {}
        }
        return fallback;
    }
}

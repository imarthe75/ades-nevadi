package mx.ades.modules.grade_analytics;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.grade_analytics.query.GradeAnalyticsQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para analítica avanzada de calificaciones.
 * Expone endpoints bajo /api/v1/grade-analytics para tendencias por grupo,
 * distribución de calificaciones por período, alumnos en riesgo académico,
 * resumen por plantel, cobertura curricular y alertas por umbral configurable.
 * Requiere JWT válido ({@code resolveUser}) y nivelAcceso &le;3 (Coordinador o
 * superior — mismo umbral que {@code roleGuard(3)} en app.routes.ts) en todos
 * los endpoints, con scoping por plantel del usuario para no-admins.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/grade-analytics")
@RequiredArgsConstructor
public class GradeAnalyticsController {

    private final AdesUserService userService;
    private final GradeAnalyticsQueryService queryService;
    private final JdbcTemplate jdbc;

    /**
     * BOLA/BFLA fix (2026-07-16, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
     * #1 — GradeAnalyticsController): los 6 endpoints solo llamaban a
     * {@code resolveUser(jwt)} sin verificar nivelAcceso ni plantel — cualquier
     * cuenta autenticada, incluido un alumno/padre (nivelAcceso &ge;5), podía
     * consultar analítica de riesgo académico de cualquier plantel del sistema.
     * Umbral alineado con {@code roleGuard(3)} de la ruta Angular equivalente.
     */
    private AdesUser requireCoordinadorOSuperior(Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return user;
    }

    private void verificarAccesoGrupo(AdesUser user, UUID grupoId) {
        List<UUID> plantelRows = jdbc.queryForList(
                "SELECT gr.plantel_id FROM ades_grupos g " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "WHERE g.id = ?", UUID.class, grupoId);
        if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        userService.verificarPlantel(user, plantelRows.get(0), "El grupo no pertenece a su plantel");
    }

    @GetMapping("/tendencias/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> tendenciasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        verificarAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(queryService.tendenciasGrupo(grupoId));
    }

    @GetMapping("/distribucion/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> distribucionCalificaciones(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "numero_periodo", required = false) Integer numeroPeriodo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        verificarAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(queryService.distribucion(grupoId, numeroPeriodo));
    }

    @GetMapping("/riesgo")
    public ResponseEntity<List<Map<String, Object>>> alumnosEnRiesgo(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "nivel_riesgo", required = false) String nivelRiesgo,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        if (grupoId != null) verificarAccesoGrupo(user, grupoId);
        UUID plantelFiltro = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(queryService.alumnosEnRiesgo(plantelFiltro, grupoId, nivelRiesgo, limit));
    }

    @GetMapping("/resumen-plantel")
    public ResponseEntity<List<Map<String, Object>>> resumenPlantel(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        UUID plantelFiltro = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(queryService.resumenPlantel(plantelFiltro));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<List<Map<String, Object>>> coberturaCurricular(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        if (grupoId != null) verificarAccesoGrupo(user, grupoId);
        UUID plantelFiltro = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(queryService.coberturaCurricular(plantelFiltro, grupoId));
    }

    @GetMapping("/alertas-umbral")
    public ResponseEntity<List<Map<String, Object>>> alertasUmbral(
            @RequestParam(value = "umbral", defaultValue = "7.0") double umbral,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = requireCoordinadorOSuperior(jwt);
        if (grupoId != null) verificarAccesoGrupo(user, grupoId);
        UUID plantelFiltro = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(queryService.alertasUmbral(umbral, plantelFiltro, grupoId, limit));
    }
}

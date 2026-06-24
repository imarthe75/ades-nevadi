package mx.ades.modules.grade_analytics;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.grade_analytics.query.GradeAnalyticsQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para analítica avanzada de calificaciones.
 * Expone endpoints bajo /api/v1/grade-analytics para tendencias por grupo,
 * distribución de calificaciones por período, alumnos en riesgo académico,
 * resumen por plantel, cobertura curricular y alertas por umbral configurable.
 * Requiere JWT válido ({@code resolveUser}) en todos los endpoints.
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

    @GetMapping("/tendencias/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> tendenciasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.tendenciasGrupo(grupoId));
    }

    @GetMapping("/distribucion/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> distribucionCalificaciones(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "numero_periodo", required = false) Integer numeroPeriodo,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.distribucion(grupoId, numeroPeriodo));
    }

    @GetMapping("/riesgo")
    public ResponseEntity<List<Map<String, Object>>> alumnosEnRiesgo(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "nivel_riesgo", required = false) String nivelRiesgo,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.alumnosEnRiesgo(plantelId, grupoId, nivelRiesgo, limit));
    }

    @GetMapping("/resumen-plantel")
    public ResponseEntity<List<Map<String, Object>>> resumenPlantel(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.resumenPlantel(plantelId));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<List<Map<String, Object>>> coberturaCurricular(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.coberturaCurricular(plantelId, grupoId));
    }

    @GetMapping("/alertas-umbral")
    public ResponseEntity<List<Map<String, Object>>> alertasUmbral(
            @RequestParam(value = "umbral", defaultValue = "7.0") double umbral,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.alertasUmbral(umbral, plantelId, grupoId, limit));
    }
}

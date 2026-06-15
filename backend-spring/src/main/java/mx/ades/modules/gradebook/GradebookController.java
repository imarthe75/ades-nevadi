package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.port.in.AplicarAjusteUseCase;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import mx.ades.modules.gradebook.query.GradebookQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/gradebook")
@RequiredArgsConstructor
public class GradebookController {

    private final AdesUserService userService;
    private final AplicarAjusteUseCase aplicarAjuste;
    private final CerrarCalificacionUseCase cerrarCalificacion;
    private final GradebookQueryService query;

    @Data
    public static class AjusteIn {
        private Double ajusteManual;
        private String justificacionAjuste;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @GetMapping("/periodo/{periodoId}/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> tablaCalificacionesGrupo(
            @PathVariable UUID periodoId,
            @PathVariable UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.tablaCalificacionesGrupo(periodoId, grupoId, materiaId));
    }

    @GetMapping("/alumno/{alumnoId}/boleta")
    public ResponseEntity<List<Map<String, Object>>> boletaAlumno(
            @PathVariable UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "ciclo_id",   required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.boletaAlumno(alumnoId, periodoId, cicloId));
    }

    @GetMapping("/grupo/{grupoId}/concentrado")
    public ResponseEntity<Map<String, Object>> concentradoGrupo(
            @PathVariable UUID grupoId,
            @RequestParam("periodo_id") UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.concentradoGrupo(grupoId, periodoId));
    }

    @GetMapping("/grupo/{grupoId}/cobertura-curricular")
    public ResponseEntity<Map<String, Object>> coberturaCurricular(
            @PathVariable UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.coberturaCurricular(grupoId, materiaId));
    }

    @GetMapping("/inconsistencias/{grupoId}")
    public ResponseEntity<Map<String, Object>> detectarInconsistencias(
            @PathVariable UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.detectarInconsistencias(grupoId, periodoId));
    }

    @GetMapping("/candidatos-extraordinario/{grupoId}")
    public ResponseEntity<Map<String, Object>> candidatosExtraordinario(
            @PathVariable UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.candidatosExtraordinario(grupoId, periodoId));
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @PostMapping("/{calPeriodoId}/ajuste-manual")
    public ResponseEntity<Map<String, Object>> ajusteManual(
            @PathVariable UUID calPeriodoId,
            @RequestBody AjusteIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        boolean esAdmin = user.getRoles().contains("ADMIN_GLOBAL") || user.getRoles().contains("ADMIN_PLANTEL");

        // Validación de dominio — AjusteManual lanza IllegalArgumentException si justificacion < 20 chars
        AjusteManual ajuste = new AjusteManual(
                BigDecimal.valueOf(body.getAjusteManual()), body.getJustificacionAjuste());

        AplicarAjusteUseCase.Result result = aplicarAjuste.ejecutar(
                new AplicarAjusteUseCase.Command(calPeriodoId, ajuste, user.getUsername(), esAdmin));

        return ResponseEntity.ok(Map.of("message", "Ajuste aplicado", "calificacion_final", result.calificacionFinal()));
    }

    @PostMapping("/{calPeriodoId}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarCalificacion(
            @PathVariable UUID calPeriodoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        cerrarCalificacion.ejecutar(
                new CerrarCalificacionUseCase.Command(calPeriodoId, user.getUsername(),
                        new java.util.HashSet<>(user.getRoles())));
        return ResponseEntity.ok(Map.of("message", "Período cerrado"));
    }

    @PostMapping("/periodo/{periodoId}/recalcular-todo")
    public ResponseEntity<Map<String, Object>> recalcularPeriodo(
            @PathVariable UUID periodoId,
            @RequestParam(value = "grupo_id",   required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int recalculados = query.recalcularPeriodo(periodoId, grupoId, materiaId);
        return ResponseEntity.ok(Map.of("recalculados", recalculados));
    }
}

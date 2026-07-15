package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.port.in.AplicarAjusteUseCase;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import mx.ades.modules.gradebook.query.GradebookQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Adaptador REST para el libro de calificaciones (gradebook) por período.
 * Expone endpoints bajo /api/v1/gradebook para la tabla de calificaciones de grupo,
 * boleta individual de alumno, concentrado de grupo, cobertura curricular, detección
 * de inconsistencias, candidatos a extraordinario, ajustes manuales con justificación
 * (requiere rol ADMIN_PLANTEL/ADMIN_GLOBAL), cierre de período y recálculo masivo.
 * Requiere JWT válido; ajustes y cierres exigen roles específicos.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/gradebook")
@RequiredArgsConstructor
public class GradebookController {

    private final AdesUserService userService;
    private final AplicarAjusteUseCase aplicarAjuste;
    private final CerrarCalificacionUseCase cerrarCalificacion;
    private final GradebookQueryService query;
    private final JdbcTemplate jdbc;

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
        // BOLA fix: tabla completa de calificaciones de un grupo (nombres + notas de TODOS
        // los alumnos); antes solo llamaba resolveUser sin verificar nivelAcceso ni
        // asignación docente↔grupo — cualquier usuario autenticado (incl. un docente de
        // OTRO grupo, o un padre) podía leer las notas de cualquier grupo del sistema.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(query.tablaCalificacionesGrupo(periodoId, grupoId, materiaId));
    }

    @GetMapping("/alumno/{alumnoId}/boleta")
    public ResponseEntity<List<Map<String, Object>>> boletaAlumno(
            @PathVariable UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "ciclo_id",   required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (mismo criterio que PortalFamiliasController#verificarAccesoAlumno):
        // la boleta es dato académico de un alumno específico; antes cualquier usuario
        // autenticado podía leerla de cualquier alumno por path param, incluidos
        // padres sin relación de tutoría con ese alumno.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(query.boletaAlumno(alumnoId, periodoId, cicloId));
    }

    @GetMapping("/grupo/{grupoId}/concentrado")
    public ResponseEntity<Map<String, Object>> concentradoGrupo(
            @PathVariable UUID grupoId,
            @RequestParam("periodo_id") UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(query.concentradoGrupo(grupoId, periodoId));
    }

    @GetMapping("/grupo/{grupoId}/cobertura-curricular")
    public ResponseEntity<Map<String, Object>> coberturaCurricular(
            @PathVariable UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(query.coberturaCurricular(grupoId, materiaId));
    }

    @GetMapping("/inconsistencias/{grupoId}")
    public ResponseEntity<Map<String, Object>> detectarInconsistencias(
            @PathVariable UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(query.detectarInconsistencias(grupoId, periodoId));
    }

    @GetMapping("/candidatos-extraordinario/{grupoId}")
    public ResponseEntity<Map<String, Object>> candidatosExtraordinario(
            @PathVariable UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(query.candidatosExtraordinario(grupoId, periodoId));
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @PostMapping("/{calPeriodoId}/ajuste-manual")
    public ResponseEntity<Map<String, Object>> ajusteManual(
            @PathVariable UUID calPeriodoId,
            @RequestBody AjusteIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA/BFLA fix CRÍTICO: este endpoint no verificaba nivelAcceso NI asignación
        // docente↔grupo en absoluto (solo el propio caso de uso bloqueaba el ajuste si la
        // calificación ya estaba cerrada) — cualquier usuario autenticado, incluido un
        // alumno/padre, podía sobreescribir la calificación final de CUALQUIER alumno
        // mientras el periodo siguiera abierto. Mismo criterio que
        // EvaluacionController/TareaController/ActividadesController#requireAcceso*.
        UUID grupoId = query.grupoIdDeCalificacion(calPeriodoId);
        if (grupoId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de calificación no encontrado");
        }
        requireAccesoGrupo(user, grupoId);
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
        // BFLA fix: recálculo masivo sin ninguna verificación de rol — cualquier usuario
        // autenticado podía disparar el recálculo de calificaciones de un grupo o de todo
        // el periodo. Si se indica grupo_id se exige el mismo control docente↔grupo del
        // resto del módulo; sin grupo_id (alcance de todo el periodo) se exige personal
        // con alcance institucional (nivelAcceso &le;3).
        AdesUser user = userService.resolveUser(jwt);
        if (grupoId != null) {
            requireAccesoGrupo(user, grupoId);
        } else if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Recalcular todo el periodo requiere alcance institucional (Admin/Director/Coordinador)");
        }
        int recalculados = query.recalcularPeriodo(periodoId, grupoId, materiaId);
        return ResponseEntity.ok(Map.of("recalculados", recalculados));
    }

    /**
     * Crear/editar/consultar datos de gradebook por grupo es operación de personal
     * escolar (nivelAcceso &le;4: admin/director/coordinador/docente). Admin/Director/
     * Coordinador (nivelAcceso &le;3) tienen alcance institucional; un Docente
     * (nivelAcceso 4) solo puede operar sobre grupos donde esté realmente asignado
     * (tabla {@code ades_asignaciones_docentes}) — mismo criterio que
     * EvaluacionController/TareaController/ActividadesController#requireAcceso*.
     */
    private void requireAccesoGrupo(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) return; // admin/director/coordinador: alcance institucional
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_asignaciones_docentes ad " +
                "JOIN ades_profesores p ON p.id = ad.profesor_id " +
                "WHERE ad.grupo_id = ? AND p.persona_id = ? AND ad.is_active = TRUE",
                Long.class, grupoId, user.getPersonaId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No está asignado a este grupo");
        }
    }

    /**
     * Personal escolar (nivelAcceso &le;4) puede consultar la boleta de cualquier alumno
     * de su ámbito. Padres/alumnos (nivelAcceso &gt;=5) solo pueden consultar la boleta de
     * un alumno donde son tutor activo — mismo criterio que
     * PortalFamiliasController#verificarAccesoAlumno, previene IDOR sobre calificaciones.
     */
    private void requireAccesoAlumno(AdesUser user, UUID alumnoId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            return;
        }
        if (!query.esTutorDeAlumno(user.getEmail(), alumnoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este alumno");
        }
    }
}

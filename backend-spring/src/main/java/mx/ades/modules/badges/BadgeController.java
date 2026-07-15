package mx.ades.modules.badges;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.badges.application.service.BadgeApplicationService;
import mx.ades.modules.badges.domain.model.CriterioTipo;
import mx.ades.modules.badges.domain.model.TipoBadge;
import mx.ades.modules.badges.domain.port.in.CrearBadgeUseCase;
import mx.ades.modules.badges.domain.port.in.OtorgarBadgeUseCase;
import mx.ades.modules.badges.domain.port.in.RevocarBadgeUseCase;
import mx.ades.modules.badges.domain.port.in.AutoEvaluarBadgesUseCase;
import mx.ades.modules.badges.query.BadgeQueryService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el módulo de insignias (badges) de reconocimiento académico.
 * Expone endpoints bajo /api/v1/badges para crear, consultar, eliminar y gestionar
 * insignias por alumno y ciclo escolar. Soporta otorgamiento manual y automático
 * (auto-evaluar) mediante criterios configurables (MANUAL, por métrica). Los badges
 * pueden ser de distintos tipos (TipoBadge) y tienen criterios de otorgamiento
 * (CriterioTipo) que permiten automatizar el reconocimiento al ejecutar el endpoint
 * /auto-evaluar/{cicloId}. Toda operación de escritura requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final AdesUserService         userService;
    private final CrearBadgeUseCase       crearBadge;
    private final OtorgarBadgeUseCase     otorgarBadge;
    private final RevocarBadgeUseCase     revocarBadge;
    private final AutoEvaluarBadgesUseCase autoEvaluarBadges;
    private final BadgeApplicationService service;
    private final BadgeQueryService       query;
    private final JdbcTemplate            jdbc;

    @Data
    public static class BadgeCreateRequest {
        @NotBlank(message = "nombre es obligatorio")
        @Size(max = 100, message = "nombre máximo 100 caracteres")
        private String nombre;

        private String descripcion;
        private String icono = "pi-star";
        private String color = "#D02030";

        @NotBlank(message = "tipo es obligatorio")
        private String tipo;

        private String criterioTipo = "MANUAL";
        private String criterioMetrica;
        private String criterioValor;
        private UUID plantelId;
    }

    @Data
    public static class OtorgarRequest {
        @NotNull(message = "estudianteId es obligatorio")
        private UUID estudianteId;

        private UUID cicloId;
        private String motivo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.listar(tipo, plantelId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody @Valid BadgeCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        BigDecimal valor = body.getCriterioValor() != null ? new BigDecimal(body.getCriterioValor()) : null;
        var cmd = new CrearBadgeUseCase.Command(
                body.getNombre(), body.getDescripcion(), body.getIcono(), body.getColor(),
                TipoBadge.of(body.getTipo()),
                CriterioTipo.of(body.getCriterioTipo()),
                body.getCriterioMetrica(), valor, body.getPlantelId());
        UUID id = crearBadge.crear(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalle(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.detalle(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        service.eliminar(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/alumno/{estudianteId}")
    public ResponseEntity<List<Map<String, Object>>> badgesAlumno(
            @PathVariable("estudianteId") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix: badges de un alumno específico por path param sin ninguna verificación —
        // cualquier autenticado, incl. padres sin relación de tutoría con ese alumno, podía
        // consultarlos. Mismo criterio que EntregasController#requireAccesoAlumno.
        requireAccesoAlumno(user, estudianteId);
        return ResponseEntity.ok(query.badgesAlumno(estudianteId, cicloId));
    }

    private void requireAccesoAlumno(AdesUser user, UUID alumnoId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            if (user.getPlantelId() == null) return;
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, alumnoId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            if (!user.getPlantelId().equals(plantelRows.get(0))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El alumno no pertenece a su plantel");
            }
            return;
        }
        String email = user.getEmail();
        Integer count = email == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE p.email_personal = ? AND ta.alumno_id = ? AND ta.is_active = TRUE",
                Integer.class, email, alumnoId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este alumno");
        }
    }

    @PostMapping("/{badgeId}/otorgar")
    public ResponseEntity<Map<String, Object>> otorgar(
            @PathVariable("badgeId") UUID badgeId,
            @RequestBody @Valid OtorgarRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        var cmd = new OtorgarBadgeUseCase.Command(
                badgeId, body.getEstudianteId(), body.getCicloId(), body.getMotivo(), user.getId());
        boolean nuevo = otorgarBadge.otorgar(cmd);
        return ResponseEntity.ok(nuevo ? Map.of("ok", true) : Map.of("ok", true, "duplicado", true));
    }

    @DeleteMapping("/{badgeId}/otorgados/{estudianteId}")
    public ResponseEntity<Map<String, Object>> revocar(
            @PathVariable("badgeId") UUID badgeId,
            @PathVariable("estudianteId") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        revocarBadge.revocar(badgeId, estudianteId, cicloId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/auto-evaluar/{cicloId}")
    public ResponseEntity<Map<String, Object>> autoEvaluar(
            @PathVariable("cicloId") UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        AutoEvaluarBadgesUseCase.Result result = autoEvaluarBadges.autoEvaluar(cicloId);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "total_otorgados", result.totalOtorgados(),
                "badges_evaluados", result.badgesEvaluados()));
    }
}

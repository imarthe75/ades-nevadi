package mx.ades.modules.grupos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.grupos.query.GrupoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la consulta de grupos escolares.
 * Expone endpoints bajo /api/v1/grupos para listar grupos con filtros en cascada
 * (plantel → nivel → grado → ciclo escolar) y obtener el detalle de un grupo por ID.
 * Requiere JWT válido en todos los endpoints; el listado admite filtro por ciclo vigente
 * para mostrar solo el ciclo activo del sistema educativo correspondiente.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final AdesUserService userService;
    private final GrupoQueryService queryService;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @RequestParam(name = "grado_id", required = false) UUID gradoId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId,
            @RequestParam(name = "solo_activos", defaultValue = "true") boolean soloActivos,
            @RequestParam(name = "ciclo_vigente", defaultValue = "false") boolean cicloVigente) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix (2026-07-16, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
        // #1 — GrupoController): sin nivelAcceso ni plantel check — cualquier autenticado
        // enumeraba la estructura de grupos de cualquier plantel (severidad baja, sin PII,
        // pero mismo patrón que el resto de controllers corregidos).
        UUID plantelFiltro = userService.getEffectivePlantelId(user, plantelId);
        UUID effectiveCicloId = cicloId != null ? cicloId : cicloEscolarId;
        return ResponseEntity.ok(queryService.listar(plantelFiltro, effectiveCicloId, gradoId, nivelId, soloActivos, cicloVigente));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<UUID> plantelRows = jdbc.queryForList(
                "SELECT gr.plantel_id FROM ades_grupos g " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "WHERE g.id = ?", UUID.class, id);
        if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        userService.verificarPlantel(user, plantelRows.get(0), "El grupo no pertenece a su plantel");
        return ResponseEntity.ok(queryService.obtener(id));
    }
}

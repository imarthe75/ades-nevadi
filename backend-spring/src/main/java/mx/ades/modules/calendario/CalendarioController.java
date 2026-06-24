package mx.ades.modules.calendario;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/calendario")
@RequiredArgsConstructor
public class CalendarioController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;
    private final CalendarioWriteService writeService;

    // Nivel mínimo para mutaciones (COORDINADOR = 3; DOCENTE/ALUMNO/PADRE no pueden)
    private static final int NIVEL_MIN_ESCRITURA = 3;

    private static void requireRole(AdesUser user, int nivelMax) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > nivelMax) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Se requiere rol de Coordinador o superior para esta operación");
        }
    }

    // Extrae campo como String de forma segura (evita ClassCastException con Integer, Boolean, etc.)
    private static String safeStr(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "ciclo_escolar_id", required = false) UUID cicloId,
            @RequestParam(name = "tipo_evento", required = false) String tipo,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        // Forzar plantel del usuario para no-admins (evita lectura cross-plantel)
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId();
        }

        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.ciclo_escolar_id, ce.nombre_ciclo, " +
            "  c.fecha_evento, c.nombre_evento, c.tipo_evento, " +
            "  c.aplica_todos_planteles, c.plantel_id, p.nombre_plantel, c.is_active " +
            "FROM ades_calendario_escolar c " +
            "LEFT JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id " +
            "LEFT JOIN ades_planteles p ON p.id = c.plantel_id " +
            "WHERE c.is_active = true ");

        List<Object> params = new ArrayList<>();
        if (cicloId != null) { sql.append("AND c.ciclo_escolar_id = ? "); params.add(cicloId); }
        if (tipo != null && !tipo.isBlank()) { sql.append("AND c.tipo_evento = ? "); params.add(tipo); }
        if (plantelId != null) { sql.append("AND (c.plantel_id = ? OR c.aplica_todos_planteles = true) "); params.add(plantelId); }
        sql.append("ORDER BY c.fecha_evento ASC, c.nombre_evento ASC");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.*, ce.nombre_ciclo, p.nombre_plantel " +
            "FROM ades_calendario_escolar c " +
            "LEFT JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id " +
            "LEFT JOIN ades_planteles p ON p.id = c.plantel_id " +
            "WHERE c.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        // Verificar acceso por plantel para no-admins
        Object eventPlantelId = rows.get(0).get("plantel_id");
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && eventPlantelId != null
                && !eventPlantelId.toString().equals(user.getPlantelId().toString())
                && !Boolean.TRUE.equals(rows.get(0).get("aplica_todos_planteles"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a este evento");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireRole(user, NIVEL_MIN_ESCRITURA);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 99;
        boolean aplicaTodos = body.get("aplica_todos_planteles") instanceof Boolean b ? b : (nivel == 0);
        return ResponseEntity.status(HttpStatus.CREATED).body(writeService.crearEvento(
            safeStr(body, "ciclo_escolar_id"), safeStr(body, "fecha_evento"),
            safeStr(body, "nombre_evento"), safeStr(body, "tipo_evento"),
            safeStr(body, "plantel_id"), aplicaTodos, nivel, user.getPlantelId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireRole(user, NIVEL_MIN_ESCRITURA);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 99;
        return ResponseEntity.ok(writeService.actualizarEvento(id, body, nivel, user.getPlantelId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireRole(user, NIVEL_MIN_ESCRITURA);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 99;
        writeService.eliminarEvento(id, nivel, user.getPlantelId());
    }
}

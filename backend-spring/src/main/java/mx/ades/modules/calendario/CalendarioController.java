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

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/calendario")
@RequiredArgsConstructor
public class CalendarioController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

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

        UUID newId = UUID.randomUUID();
        String cicloId  = safeStr(body, "ciclo_escolar_id");
        String fecha    = safeStr(body, "fecha_evento");
        String nombre   = safeStr(body, "nombre_evento");
        String tipo     = safeStr(body, "tipo_evento");
        String plantelId = safeStr(body, "plantel_id");

        if (cicloId == null || fecha == null || nombre == null || tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ciclo_escolar_id, fecha_evento, nombre_evento y tipo_evento son obligatorios");
        }
        // Validate date format before hitting DB
        try { LocalDate.parse(fecha); } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "fecha_evento inválida. Formato esperado: YYYY-MM-DD");
        }
        // Validate nombre length
        if (nombre.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre_evento excede 200 caracteres");
        }

        // Para ADMIN_PLANTEL (1) y superiores: aplica_todos_planteles por defecto false (solo su plantel)
        boolean aplicaTodos = body.get("aplica_todos_planteles") instanceof Boolean b ? b :
            (user.getNivelAcceso() != null && user.getNivelAcceso() == 0); // solo ADMIN_GLOBAL puede hacer global por defecto
        // Forzar plantel del usuario para no-admins globales
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 0 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId().toString();
            aplicaTodos = false;
        }

        jdbc.update(
            "INSERT INTO ades_calendario_escolar " +
            "(id, ciclo_escolar_id, fecha_evento, nombre_evento, tipo_evento, aplica_todos_planteles, plantel_id) " +
            "VALUES (?, ?::uuid, ?::date, ?, ?, ?, ?::uuid)",
            newId, cicloId, fecha, nombre, tipo, aplicaTodos,
            plantelId != null && !plantelId.isBlank() ? plantelId : null);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", newId, "created", true));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireRole(user, NIVEL_MIN_ESCRITURA);

        // Verify exists and check plantel ownership
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT plantel_id FROM ades_calendario_escolar WHERE id = ? AND is_active = true", id);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        }
        Object eventPlantelId = existing.get(0).get("plantel_id");
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && eventPlantelId != null
                && !eventPlantelId.toString().equals(user.getPlantelId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para modificar este evento");
        }

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.containsKey("fecha_evento")) {
            String f = safeStr(body, "fecha_evento");
            try { if (f != null) LocalDate.parse(f); } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha_evento inválida. Formato: YYYY-MM-DD");
            }
            sets.add("fecha_evento = ?::date"); params.add(f);
        }
        if (body.containsKey("nombre_evento")) { sets.add("nombre_evento = ?"); params.add(safeStr(body, "nombre_evento")); }
        if (body.containsKey("tipo_evento"))   { sets.add("tipo_evento = ?");   params.add(safeStr(body, "tipo_evento")); }
        if (body.containsKey("ciclo_escolar_id")) { sets.add("ciclo_escolar_id = ?::uuid"); params.add(safeStr(body, "ciclo_escolar_id")); }
        if (body.containsKey("aplica_todos_planteles")) {
            // Solo ADMIN_GLOBAL puede marcar aplica_todos_planteles = true
            boolean val = body.get("aplica_todos_planteles") instanceof Boolean b ? b : false;
            if (val && (user.getNivelAcceso() == null || user.getNivelAcceso() > 0)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo ADMIN_GLOBAL puede marcar eventos como aplicables a todos los planteles");
            }
            sets.add("aplica_todos_planteles = ?"); params.add(val);
        }
        if (body.containsKey("plantel_id")) {
            sets.add("plantel_id = ?::uuid");
            params.add(safeStr(body, "plantel_id"));
        }

        if (!sets.isEmpty()) {
            params.add(id);
            jdbc.update("UPDATE ades_calendario_escolar SET " + String.join(", ", sets) + " WHERE id = ?",
                params.toArray());
        }
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireRole(user, NIVEL_MIN_ESCRITURA);

        // Verificar plantel ownership antes de eliminar
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT plantel_id FROM ades_calendario_escolar WHERE id = ? AND is_active = true", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        Object eventPlantelId = existing.get(0).get("plantel_id");
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && eventPlantelId != null
                && !eventPlantelId.toString().equals(user.getPlantelId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para eliminar este evento");
        }

        jdbc.update("UPDATE ades_calendario_escolar SET is_active = false WHERE id = ? AND is_active = true", id);
    }
}

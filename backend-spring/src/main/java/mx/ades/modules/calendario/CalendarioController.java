package mx.ades.modules.calendario;

import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "ciclo_escolar_id", required = false) UUID cicloId,
            @RequestParam(name = "tipo_evento", required = false) String tipo,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId) {

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
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.*, ce.nombre_ciclo, p.nombre_plantel " +
            "FROM ades_calendario_escolar c " +
            "LEFT JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id " +
            "LEFT JOIN ades_planteles p ON p.id = c.plantel_id " +
            "WHERE c.id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        UUID newId = UUID.randomUUID();
        String cicloId = (String) body.get("ciclo_escolar_id");
        String fecha = (String) body.get("fecha_evento");
        String nombre = (String) body.get("nombre_evento");
        String tipo = (String) body.get("tipo_evento");
        boolean aplicaTodos = body.get("aplica_todos_planteles") instanceof Boolean b ? b : true;
        String plantelId = (String) body.get("plantel_id");

        if (cicloId == null || fecha == null || nombre == null || tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ciclo_escolar_id, fecha_evento, nombre_evento y tipo_evento son obligatorios");
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
        userService.resolveUser(jwt);
        // verify exists
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ades_calendario_escolar WHERE id = ? AND is_active = true",
                Integer.class, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        }

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.containsKey("fecha_evento"))         { sets.add("fecha_evento = ?::date");      params.add(body.get("fecha_evento")); }
        if (body.containsKey("nombre_evento"))        { sets.add("nombre_evento = ?");           params.add(body.get("nombre_evento")); }
        if (body.containsKey("tipo_evento"))          { sets.add("tipo_evento = ?");             params.add(body.get("tipo_evento")); }
        if (body.containsKey("ciclo_escolar_id"))     { sets.add("ciclo_escolar_id = ?::uuid");  params.add(body.get("ciclo_escolar_id")); }
        if (body.containsKey("aplica_todos_planteles")) { sets.add("aplica_todos_planteles = ?"); params.add(body.get("aplica_todos_planteles")); }
        if (body.containsKey("plantel_id")) {
            Object pid = body.get("plantel_id");
            sets.add("plantel_id = ?::uuid");
            params.add(pid != null && !pid.toString().isBlank() ? pid.toString() : null);
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
        userService.resolveUser(jwt);
        int rows = jdbc.update(
            "UPDATE ades_calendario_escolar SET is_active = false WHERE id = ? AND is_active = true", id);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
    }
}

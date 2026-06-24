package mx.ades.modules.calendario;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Lógica de escritura del calendario escolar.
 * El CalendarioController delega aquí todas las mutaciones (POST / PATCH / DELETE).
 */
@Service
@RequiredArgsConstructor
public class CalendarioWriteService {

    private final JdbcTemplate jdbc;

    public Map<String, Object> crearEvento(
            String cicloId, String fecha, String nombre, String tipo,
            String plantelIdStr, boolean aplicaTodos, int nivelAcceso, UUID userPlantelId) {

        validarFecha(fecha, "fecha_evento");
        if (nombre != null && nombre.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre_evento excede 200 caracteres");
        if (cicloId == null || fecha == null || nombre == null || tipo == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ciclo_escolar_id, fecha_evento, nombre_evento y tipo_evento son obligatorios");

        // Forzar plantel del usuario para no-admins globales
        if (nivelAcceso > 0 && userPlantelId != null) {
            plantelIdStr = userPlantelId.toString();
            aplicaTodos = false;
        }

        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_calendario_escolar " +
            "(id, ciclo_escolar_id, fecha_evento, nombre_evento, tipo_evento, aplica_todos_planteles, plantel_id) " +
            "VALUES (?, ?::uuid, ?::date, ?, ?, ?, ?::uuid)",
            newId, cicloId, fecha, nombre, tipo, aplicaTodos,
            plantelIdStr != null && !plantelIdStr.isBlank() ? plantelIdStr : null);

        return Map.of("id", newId, "created", true);
    }

    public Map<String, Object> actualizarEvento(
            UUID id, Map<String, Object> body, int nivelAcceso, UUID userPlantelId) {

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT plantel_id FROM ades_calendario_escolar WHERE id = ? AND is_active = true", id);
        if (existing.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");

        Object eventPlantelId = existing.get(0).get("plantel_id");
        if (nivelAcceso > 1 && userPlantelId != null && eventPlantelId != null
                && !eventPlantelId.toString().equals(userPlantelId.toString()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para modificar este evento");

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (body.containsKey("fecha_evento")) {
            String f = safeStr(body, "fecha_evento");
            validarFecha(f, "fecha_evento");
            sets.add("fecha_evento = ?::date"); params.add(f);
        }
        if (body.containsKey("nombre_evento")) { sets.add("nombre_evento = ?"); params.add(safeStr(body, "nombre_evento")); }
        if (body.containsKey("tipo_evento"))   { sets.add("tipo_evento = ?");   params.add(safeStr(body, "tipo_evento")); }
        if (body.containsKey("ciclo_escolar_id")) { sets.add("ciclo_escolar_id = ?::uuid"); params.add(safeStr(body, "ciclo_escolar_id")); }
        if (body.containsKey("aplica_todos_planteles")) {
            boolean val = body.get("aplica_todos_planteles") instanceof Boolean b ? b : false;
            if (val && nivelAcceso > 0)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo ADMIN_GLOBAL puede marcar eventos como aplicables a todos los planteles");
            sets.add("aplica_todos_planteles = ?"); params.add(val);
        }
        if (body.containsKey("plantel_id")) {
            sets.add("plantel_id = ?::uuid"); params.add(safeStr(body, "plantel_id"));
        }

        if (!sets.isEmpty()) {
            params.add(id);
            jdbc.update("UPDATE ades_calendario_escolar SET " + String.join(", ", sets) + " WHERE id = ?",
                params.toArray());
        }
        return Map.of("updated", true);
    }

    public void eliminarEvento(UUID id, int nivelAcceso, UUID userPlantelId) {
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT plantel_id FROM ades_calendario_escolar WHERE id = ? AND is_active = true", id);
        if (existing.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");

        Object eventPlantelId = existing.get(0).get("plantel_id");
        if (nivelAcceso > 1 && userPlantelId != null && eventPlantelId != null
                && !eventPlantelId.toString().equals(userPlantelId.toString()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para eliminar este evento");

        jdbc.update("UPDATE ades_calendario_escolar SET is_active = false WHERE id = ?", id);
    }

    private static void validarFecha(String f, String campo) {
        if (f == null) return;
        try { LocalDate.parse(f); } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " inválida. Formato: YYYY-MM-DD");
        }
    }

    private static String safeStr(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }
}

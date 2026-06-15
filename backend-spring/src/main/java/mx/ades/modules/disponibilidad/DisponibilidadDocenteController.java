package mx.ades.modules.disponibilidad;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadDocenteController {

    private final DisponibilidadDocenteRepository repository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Map<Integer, String> DIAS = Map.of(
            0, "Lunes", 1, "Martes", 2, "Miércoles", 3, "Jueves", 4, "Viernes", 5, "Sábado", 6, "Domingo"
    );

    @Data
    public static class SlotIn {
        private Integer diaSemana;
        private LocalTime horaInicio;
        private LocalTime horaFin;
        private Boolean disponible = true;
        private String motivoNoDisponible;
    }

    @Data
    public static class BulkDisponibilidadIn {
        private List<SlotIn> slots;
        private UUID cicloEscolarId;
        private Double horasSemanaMax;
        private Double horasFrenteGrupo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "profesor_id", required = false) UUID profesorId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @RequestParam(value = "q", required = false) String q) {

        StringBuilder query = new StringBuilder(
            "SELECT dd.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_docente, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno, " +
            "  pr.numero_empleado " +
            "FROM ades_disponibilidad_docente dd " +
            "LEFT JOIN ades_profesores pr ON pr.id = dd.profesor_id " +
            "LEFT JOIN ades_personas pe ON pe.id = pr.persona_id " +
            "WHERE dd.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (profesorId != null) {
            query.append("AND dd.profesor_id = ? ");
            params.add(profesorId);
        }
        if (q != null && !q.isBlank()) {
            query.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (cicloEscolarId != null) {
            query.append("AND dd.ciclo_escolar_id = ? ");
            params.add(cicloEscolarId);
        }

        query.append("ORDER BY dd.dia_semana, dd.hora_inicio");

        List<Map<String, Object>> rows = jdbc.queryForList(query.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            Map<String, Object> d = new HashMap<>(r);
            int dia = ((Number) d.get("dia_semana")).intValue();
            d.put("dia_nombre", DIAS.getOrDefault(dia, "?"));
            if (d.get("hora_inicio") != null) d.put("hora_inicio", d.get("hora_inicio").toString());
            if (d.get("hora_fin") != null) d.put("hora_fin", d.get("hora_fin").toString());
            result.add(d);
        }

        return ResponseEntity.ok(result);
    }

    @PutMapping("/docente/{profesorId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> guardar(
            @PathVariable("profesorId") UUID profesorId,
            @RequestBody BulkDisponibilidadIn data,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        // Soft-delete current slots
        if (data.getCicloEscolarId() != null) {
            jdbc.update("UPDATE ades_disponibilidad_docente SET is_active = FALSE WHERE profesor_id = ? AND ciclo_escolar_id = ?",
                    profesorId, data.getCicloEscolarId());
        } else {
            jdbc.update("UPDATE ades_disponibilidad_docente SET is_active = FALSE WHERE profesor_id = ? AND ciclo_escolar_id IS NULL",
                    profesorId);
        }

        // Insert new slots
        for (SlotIn slot : data.getSlots()) {
            jdbc.update("INSERT INTO ades_disponibilidad_docente " +
                    "(id, profesor_id, dia_semana, hora_inicio, hora_fin, disponible, motivo_no_disponible, ciclo_escolar_id, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), profesorId, slot.getDiaSemana(), slot.getHoraInicio(), slot.getHoraFin(),
                    slot.getDisponible(), slot.getMotivoNoDisponible(), data.getCicloEscolarId(), user.getUsername(), user.getUsername());
        }

        // Update teacher hours if supplied
        if (data.getHorasSemanaMax() != null || data.getHorasFrenteGrupo() != null) {
            StringBuilder updateSql = new StringBuilder("UPDATE ades_profesores SET usuario_modificacion = ?, row_version = row_version + 1 ");
            List<Object> params = new ArrayList<>();
            params.add(user.getUsername());

            if (data.getHorasSemanaMax() != null) {
                updateSql.append(", horas_semana_max = ? ");
                params.add(data.getHorasSemanaMax());
            }
            if (data.getHorasFrenteGrupo() != null) {
                updateSql.append(", horas_frente_grupo = ? ");
                params.add(data.getHorasFrenteGrupo());
            }
            updateSql.append("WHERE id = ?");
            params.add(profesorId);

            jdbc.update(updateSql.toString(), params.toArray());
        }

        return ResponseEntity.ok(Map.of("detail", data.getSlots().size() + " slots guardados para docente " + profesorId));
    }

    @GetMapping("/docente/{profesorId}/resumen")
    public ResponseEntity<Map<String, Object>> resumen(
            @PathVariable("profesorId") UUID profesorId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId) {

        StringBuilder query = new StringBuilder("SELECT dia_semana, hora_inicio, hora_fin, disponible " +
                "FROM ades_disponibilidad_docente " +
                "WHERE profesor_id = ? AND is_active = TRUE ");

        List<Object> params = new ArrayList<>();
        params.add(profesorId);

        if (cicloEscolarId != null) {
            query.append("AND ciclo_escolar_id = ? ");
            params.add(cicloEscolarId);
        } else {
            query.append("AND ciclo_escolar_id IS NULL ");
        }

        query.append("ORDER BY dia_semana, hora_inicio");

        List<Map<String, Object>> slots = jdbc.queryForList(query.toString(), params.toArray());

        double horas = 0.0;
        Set<Integer> diasSet = new TreeSet<>();
        int totalSlots = slots.size();
        int slotsDisponibles = 0;

        for (Map<String, Object> s : slots) {
            if (Boolean.TRUE.equals(s.get("disponible"))) {
                slotsDisponibles++;
                LocalTime hi = (LocalTime) s.get("hora_inicio");
                LocalTime hf = (LocalTime) s.get("hora_fin");
                int mins = (hf.getHour() * 60 + hf.getMinute()) - (hi.getHour() * 60 + hi.getMinute());
                horas += Math.max(mins, 0) / 60.0;
                diasSet.add(((Number) s.get("dia_semana")).intValue());
            }
        }

        Map<String, Object> prof = jdbc.queryForMap(
                "SELECT horas_semana_max, horas_frente_grupo FROM ades_profesores WHERE id = ?",
                profesorId);

        List<String> diasDisponibles = new ArrayList<>();
        for (int d : diasSet) {
            diasDisponibles.add(DIAS.getOrDefault(d, "?"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("profesor_id", profesorId);
        response.put("dias_disponibles", diasDisponibles);
        response.put("total_slots", totalSlots);
        response.put("slots_disponibles", slotsDisponibles);
        response.put("horas_semana", Math.round(horas * 10.0) / 10.0);
        response.put("horas_semana_max", prof.get("horas_semana_max") != null ? prof.get("horas_semana_max") : 20.0);
        response.put("horas_frente_grupo", prof.get("horas_frente_grupo") != null ? prof.get("horas_frente_grupo") : 16.0);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cobertura/{cicloId}")
    public ResponseEntity<List<Map<String, Object>>> cobertura(@PathVariable("cicloId") UUID cicloId) {
        String sql = "SELECT p.id, per.nombre, per.apellido_paterno, COUNT(dd.id) AS slots_registrados " +
                "FROM ades_profesores p " +
                "JOIN ades_personas per ON per.id = p.persona_id " +
                "LEFT JOIN ades_disponibilidad_docente dd ON dd.profesor_id = p.id " +
                "AND dd.ciclo_escolar_id = ? AND dd.is_active = TRUE " +
                "WHERE p.is_active = TRUE " +
                "GROUP BY p.id, per.nombre, per.apellido_paterno " +
                "ORDER BY per.apellido_paterno, per.nombre";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, cicloId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            long slots = ((Number) r.get("slots_registrados")).longValue();
            Map<String, Object> item = new HashMap<>();
            item.put("profesor_id", r.get("id"));
            item.put("nombre_completo", r.get("apellido_paterno") + " " + r.get("nombre"));
            item.put("slots_registrados", slots);
            item.put("tiene_disponibilidad", slots > 0);
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id) {
        DisponibilidadDocente slot = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot no encontrado"));
        slot.setIsActive(false);
        repository.save(slot);
    }
}

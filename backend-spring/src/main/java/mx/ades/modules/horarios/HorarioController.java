package mx.ades.modules.horarios;

import lombok.Data;
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
@RequestMapping("/api/v1/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    private static final String HORARIO_SELECT =
            "SELECT h.id, h.grupo_id, h.materia_id, h.profesor_id, h.aula_id, " +
            "  h.ciclo_escolar_id, h.dia_semana, h.hora_inicio, h.hora_fin, h.origen, " +
            "  h.is_active, h.row_version, " +
            "  m.nombre_materia, " +
            "  p2.nombre || ' ' || p2.apellido_paterno AS nombre_profesor, " +
            "  a.nombre_aula, " +
            "  g.nombre_grupo, " +
            "  gr.nombre_grado, gr.plantel_id " +
            "FROM ades_horarios h " +
            "JOIN ades_grupos g ON g.id = h.grupo_id " +
            "JOIN ades_grados gr ON gr.id = g.grado_id " +
            "JOIN ades_materias m ON m.id = h.materia_id " +
            "JOIN ades_profesores pr ON pr.id = h.profesor_id " +
            "JOIN ades_personas p2 ON p2.id = pr.persona_id " +
            "LEFT JOIN ades_aulas a ON a.id = h.aula_id ";

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> porGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList(
                HORARIO_SELECT + "WHERE h.grupo_id = ? AND h.is_active = TRUE " +
                "ORDER BY h.dia_semana, h.hora_inicio", grupoId);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/profesor/{profesor_id}")
    public ResponseEntity<List<Map<String, Object>>> porProfesor(
            @PathVariable("profesor_id") UUID profesorId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList(
                HORARIO_SELECT + "WHERE h.profesor_id = ? AND h.is_active = TRUE " +
                "ORDER BY h.dia_semana, h.hora_inicio", profesorId);
        return ResponseEntity.ok(rows);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        StringBuilder where = new StringBuilder("WHERE h.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        if (grupoId != null) { where.append(" AND h.grupo_id = ?"); params.add(grupoId); }
        if (plantelId != null) { where.append(" AND gr.plantel_id = ?"); params.add(plantelId); }
        where.append(" ORDER BY h.dia_semana, h.hora_inicio");
        return ResponseEntity.ok(jdbc.queryForList(HORARIO_SELECT + where, params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_horarios " +
                "(id, grupo_id, materia_id, profesor_id, aula_id, ciclo_escolar_id, " +
                " dia_semana, hora_inicio, hora_fin, origen, usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, body.getGrupoId(), body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getCicloEscolarId(), body.getDiaSemana(),
                body.getHoraInicio(), body.getHoraFin(),
                body.getOrigen() != null ? body.getOrigen() : "MANUAL",
                user.getUsername(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(getById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        List<Map<String, Object>> ex = jdbc.queryForList(
                "SELECT id FROM ades_horarios WHERE id = ? AND is_active = TRUE", id);
        if (ex.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Horario no encontrado");

        StringBuilder sql = new StringBuilder("UPDATE ades_horarios SET usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(user.getUsername());

        if (body.getMateriaId()      != null) { sql.append(", materia_id = ?"); params.add(body.getMateriaId()); }
        if (body.getProfesorId()     != null) { sql.append(", profesor_id = ?"); params.add(body.getProfesorId()); }
        if (body.getAulaId()         != null) { sql.append(", aula_id = ?"); params.add(body.getAulaId()); }
        if (body.getDiaSemana()      != null) { sql.append(", dia_semana = ?"); params.add(body.getDiaSemana()); }
        if (body.getHoraInicio()     != null) { sql.append(", hora_inicio = ?"); params.add(body.getHoraInicio()); }
        if (body.getHoraFin()        != null) { sql.append(", hora_fin = ?"); params.add(body.getHoraFin()); }
        if (body.getOrigen()         != null) { sql.append(", origen = ?"); params.add(body.getOrigen()); }
        if (body.getMotivoCambio()   != null) { sql.append(", motivo_cambio = ?, fecha_cambio = NOW()"); params.add(body.getMotivoCambio()); }

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());
        return ResponseEntity.ok(getById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        jdbc.update("UPDATE ades_horarios SET is_active = FALSE WHERE id = ?", id);
    }

    private Map<String, Object> getById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(HORARIO_SELECT + "WHERE h.id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Data
    public static class HorarioPayload {
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID aulaId;
        private UUID cicloEscolarId;
        private Integer diaSemana;
        private String horaInicio;
        private String horaFin;
        private String origen;
        private String motivoCambio;
    }
}

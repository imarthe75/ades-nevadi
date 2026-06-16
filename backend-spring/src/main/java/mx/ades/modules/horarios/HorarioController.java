package mx.ades.modules.horarios;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.query.HorarioQueryService;
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
    private final HorarioQueryService queryService;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> porGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porGrupo(grupoId));
    }

    @GetMapping("/profesor/{profesor_id}")
    public ResponseEntity<List<Map<String, Object>>> porProfesor(
            @PathVariable("profesor_id") UUID profesorId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porProfesor(profesorId));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, plantelId));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

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
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.obtener(id));
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

        if (body.getMateriaId()    != null) { sql.append(", materia_id = ?");    params.add(body.getMateriaId()); }
        if (body.getProfesorId()   != null) { sql.append(", profesor_id = ?");   params.add(body.getProfesorId()); }
        if (body.getAulaId()       != null) { sql.append(", aula_id = ?");       params.add(body.getAulaId()); }
        if (body.getDiaSemana()    != null) { sql.append(", dia_semana = ?");    params.add(body.getDiaSemana()); }
        if (body.getHoraInicio()   != null) { sql.append(", hora_inicio = ?");   params.add(body.getHoraInicio()); }
        if (body.getHoraFin()      != null) { sql.append(", hora_fin = ?");      params.add(body.getHoraFin()); }
        if (body.getOrigen()       != null) { sql.append(", origen = ?");        params.add(body.getOrigen()); }
        if (body.getMotivoCambio() != null) {
            sql.append(", motivo_cambio = ?, fecha_cambio = NOW()");
            params.add(body.getMotivoCambio());
        }

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());
        return ResponseEntity.ok(queryService.obtener(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        jdbc.update("UPDATE ades_horarios SET is_active = FALSE WHERE id = ?", id);
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

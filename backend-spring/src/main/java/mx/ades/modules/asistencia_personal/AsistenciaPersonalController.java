package mx.ades.modules.asistencia_personal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/asistencia-personal")
@RequiredArgsConstructor
public class AsistenciaPersonalController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final List<String> JORNADAS = Arrays.asList(
            "COMPLETA", "MEDIA", "NINGUNA", "INCAPACIDAD", "VACACIONES", "PERMISO"
    );

    @Data
    public static class AsistenciaCreate {
        private UUID personaId;
        private LocalDate fecha;
        private LocalTime horaEntrada;
        private LocalTime horaSalida;
        private String tipoJornada = "COMPLETA";
        private Boolean esRetardo = false;
        private Integer minutosRetardo = 0;
        private String observaciones;
    }

    @Data
    public static class AsistenciaPatch {
        private LocalTime horaEntrada;
        private LocalTime horaSalida;
        private String tipoJornada;
        private Boolean esRetardo;
        private Integer minutosRetardo;
        private Boolean justificado;
        private String justificacion;
        private String observaciones;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarAsistencias(
            @RequestParam(value = "persona_id", required = false) UUID personaId,
            @RequestParam(value = "fecha_inicio", required = false) LocalDate fechaInicio,
            @RequestParam(value = "fecha_fin", required = false) LocalDate fechaFin,
            @RequestParam(value = "tipo_jornada", required = false) String tipoJornada,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
            "SELECT ap.*, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_completo, " +
            "  pe.nombre AS nombre_persona, pe.apellido_paterno, pe.apellido_materno " +
            "FROM public.ades_asistencia_personal ap " +
            "LEFT JOIN ades_personas pe ON pe.id = ap.persona_id " +
            "WHERE ap.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (personaId != null) {
            sql.append("AND ap.persona_id = ? ");
            params.add(personaId);
        }
        if (q != null && !q.isBlank()) {
            sql.append("AND (pe.nombre ILIKE ? OR pe.apellido_paterno ILIKE ? OR CONCAT(pe.nombre,' ',pe.apellido_paterno) ILIKE ?) ");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (fechaInicio != null) {
            sql.append("AND ap.fecha >= ? ");
            params.add(fechaInicio);
        }
        if (fechaFin != null) {
            sql.append("AND ap.fecha <= ? ");
            params.add(fechaFin);
        }
        if (tipoJornada != null && !tipoJornada.isBlank()) {
            sql.append("AND ap.tipo_jornada = ? ");
            params.add(tipoJornada);
        }

        sql.append("ORDER BY ap.fecha DESC, pe.apellido_paterno, pe.nombre");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrarAsistencia(
            @RequestBody AsistenciaCreate data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (!JORNADAS.contains(data.getTipoJornada())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_jornada inválido");
        }

        String checkSql = "SELECT COUNT(*) FROM public.ades_asistencia_personal WHERE persona_id = ? AND fecha = ?";
        Integer exists = jdbc.queryForObject(checkSql, Integer.class, data.getPersonaId(), data.getFecha());

        if (exists != null && exists > 0) {
            jdbc.update(
                "UPDATE public.ades_asistencia_personal " +
                "SET hora_entrada = ?, hora_salida = ?, tipo_jornada = ?, es_retardo = ?, " +
                "minutos_retardo = ?, observaciones = ?, usuario_modificacion = ?, row_version = row_version + 1 " +
                "WHERE persona_id = ? AND fecha = ?",
                data.getHoraEntrada(), data.getHoraSalida(), data.getTipoJornada(), data.getEsRetardo(),
                data.getMinutosRetardo(), data.getObservaciones(), user.getUsername(),
                data.getPersonaId(), data.getFecha()
            );
        } else {
            jdbc.update(
                "INSERT INTO public.ades_asistencia_personal " +
                "(id, persona_id, fecha, hora_entrada, hora_salida, tipo_jornada, es_retardo, minutos_retardo, observaciones, usuario_creacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), data.getPersonaId(), data.getFecha(), data.getHoraEntrada(), data.getHoraSalida(),
                data.getTipoJornada(), data.getEsRetardo(), data.getMinutosRetardo(), data.getObservaciones(), user.getUsername()
            );
        }

        Map<String, Object> updated = jdbc.queryForMap(
                "SELECT * FROM public.ades_asistencia_personal WHERE persona_id = ? AND fecha = ?",
                data.getPersonaId(), data.getFecha()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @GetMapping("/reporte")
    public ResponseEntity<Map<String, Object>> reporteMensual(
            @RequestParam("persona_id") UUID personaId,
            @RequestParam("mes") int mes,
            @RequestParam("anio") int anio,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT tipo_jornada, es_retardo " +
                "FROM public.ades_asistencia_personal " +
                "WHERE persona_id = ? " +
                "AND EXTRACT(MONTH FROM fecha) = ? " +
                "AND EXTRACT(YEAR FROM fecha) = ? " +
                "AND is_active = TRUE";

        List<Map<String, Object>> records = jdbc.queryForList(sql, personaId, mes, anio);
        int totalDias = records.size();
        int diasFalta = 0;
        int diasInc = 0;
        int diasVac = 0;
        int diasPerm = 0;
        int diasAsistio = 0;
        int retardos = 0;

        for (Map<String, Object> r : records) {
            String tj = (String) r.get("tipo_jornada");
            Boolean ret = (Boolean) r.get("es_retardo");

            if ("NINGUNA".equals(tj)) diasFalta++;
            else if ("INCAPACIDAD".equals(tj)) diasInc++;
            else if ("VACACIONES".equals(tj)) diasVac++;
            else if ("PERMISO".equals(tj)) diasPerm++;
            else if ("COMPLETA".equals(tj) || "MEDIA".equals(tj)) diasAsistio++;

            if (Boolean.TRUE.equals(ret)) retardos++;
        }

        double pct = totalDias > 0 ? Math.round(((double) diasAsistio / totalDias * 100.0) * 10.0) / 10.0 : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("persona_id", personaId.toString());
        response.put("mes", mes);
        response.put("anio", anio);
        response.put("total_dias", totalDias);
        response.put("dias_asistio", diasAsistio);
        response.put("dias_falta", diasFalta);
        response.put("dias_incapacidad", diasInc);
        response.put("dias_vacaciones", diasVac);
        response.put("dias_permiso", diasPerm);
        response.put("total_retardos", retardos);
        response.put("porcentaje_asistencia", pct);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalleAsistencia(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM public.ades_asistencia_personal WHERE id = ? AND is_active = TRUE",
                id
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarAsistencia(
            @PathVariable("id") UUID id,
            @RequestBody AsistenciaPatch data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM public.ades_asistencia_personal WHERE id = ? AND is_active = TRUE",
                id
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado");
        }

        if (data.getTipoJornada() != null && !JORNADAS.contains(data.getTipoJornada())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_jornada inválido");
        }

        StringBuilder updateSql = new StringBuilder("UPDATE public.ades_asistencia_personal SET usuario_modificacion = ?, row_version = row_version + 1");
        List<Object> params = new ArrayList<>();
        params.add(user.getUsername());

        if (data.getHoraEntrada() != null) {
            updateSql.append(", hora_entrada = ?");
            params.add(data.getHoraEntrada());
        }
        if (data.getHoraSalida() != null) {
            updateSql.append(", hora_salida = ?");
            params.add(data.getHoraSalida());
        }
        if (data.getTipoJornada() != null) {
            updateSql.append(", tipo_jornada = ?");
            params.add(data.getTipoJornada());
        }
        if (data.getEsRetardo() != null) {
            updateSql.append(", es_retardo = ?");
            params.add(data.getEsRetardo());
        }
        if (data.getMinutosRetardo() != null) {
            updateSql.append(", minutos_retardo = ?");
            params.add(data.getMinutosRetardo());
        }
        if (data.getObservaciones() != null) {
            updateSql.append(", observaciones = ?");
            params.add(data.getObservaciones());
        }
        if (data.getJustificado() != null) {
            if (user.getNivelAcceso() > 3) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Coordinador o superior puede justificar asistencias");
            }
            updateSql.append(", justificado = ?, justificado_por = ?");
            params.add(data.getJustificado());
            params.add(user.getId());
        }
        if (data.getJustificacion() != null) {
            updateSql.append(", justificacion = ?");
            params.add(data.getJustificacion());
        }

        updateSql.append(" WHERE id = ?");
        params.add(id);

        jdbc.update(updateSql.toString(), params.toArray());

        Map<String, Object> updated = jdbc.queryForMap(
                "SELECT * FROM public.ades_asistencia_personal WHERE id = ?",
                id
        );
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAsistencia(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM public.ades_asistencia_personal WHERE id = ? AND is_active = TRUE",
                id
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado");
        }

        jdbc.update(
                "UPDATE public.ades_asistencia_personal SET is_active = FALSE, usuario_modificacion = ?, row_version = row_version + 1 WHERE id = ?",
                user.getUsername(), id
        );
    }
}

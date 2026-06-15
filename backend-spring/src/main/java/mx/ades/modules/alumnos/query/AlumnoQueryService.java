package mx.ades.modules.alumnos.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** CQRS read-side del módulo de alumnos. */
@Service
@RequiredArgsConstructor
public class AlumnoQueryService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> listar(UUID plantelId) {
        StringBuilder sql = new StringBuilder("""
            SELECT e.id, e.matricula, e.nss, e.fecha_ingreso, e.is_active, e.tipo_alumno,
                   e.plantel_id, e.persona_id,
                   COALESCE(p.nombre_social, p.nombre) AS nombre,
                   p.apellido_paterno, p.apellido_materno, p.curp
            FROM ades_estudiantes e
            JOIN ades_personas p ON p.id = e.persona_id
            WHERE e.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        if (plantelId != null) { sql.append("AND e.plantel_id = ? "); params.add(plantelId); }
        sql.append("ORDER BY p.apellido_paterno, p.nombre");

        List<Map<String, Object>> data = jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> persona = new LinkedHashMap<>();
            persona.put("nombre",           rs.getString("nombre"));
            persona.put("apellido_paterno", rs.getString("apellido_paterno"));
            persona.put("apellido_materno", rs.getString("apellido_materno"));
            persona.put("curp",             rs.getString("curp"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",           rs.getObject("id",        UUID.class));
            row.put("matricula",    rs.getString("matricula"));
            row.put("nss",          rs.getString("nss"));
            row.put("fecha_ingreso",rs.getObject("fecha_ingreso"));
            row.put("is_active",    rs.getBoolean("is_active"));
            row.put("tipo_alumno",  rs.getString("tipo_alumno"));
            row.put("plantel_id",   rs.getObject("plantel_id", UUID.class));
            row.put("persona_id",   rs.getObject("persona_id", UUID.class));
            row.put("persona",      persona);
            return row;
        }, params.toArray());

        return Map.of("data", data, "total", data.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT e.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, p.rfc,
                   p.genero, p.nombre_social, p.genero_autopercibido, p.pronombres,
                   p.datos_sensibles_restringidos, p.fecha_nacimiento,
                   p.telefono, p.email_personal, p.estado_civil,
                   p.pais_nacimiento, p.municipio_nacimiento, p.estado_nacimiento,
                   p.nacionalidad, p.foto_url
            FROM ades_estudiantes e
            JOIN ades_personas p ON p.id = e.persona_id
            WHERE e.id = ?
            """, id);

        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public UUID resolverPersonaId(UUID alumnoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT persona_id FROM ades_estudiantes WHERE id = ?", alumnoId);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        return (UUID) rows.get(0).get("persona_id");
    }
}

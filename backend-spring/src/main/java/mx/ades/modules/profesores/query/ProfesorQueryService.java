package mx.ades.modules.profesores.query;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** CQRS read-side del módulo de profesores. */
@Service
@RequiredArgsConstructor
public class ProfesorQueryService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> listar(UUID plantelId, String buscar) {
        return listar(plantelId, null, null, null, buscar);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(UUID plantelId, UUID nivelId, UUID gradoId, UUID grupoId, String buscar) {
        StringBuilder sql = new StringBuilder("""
            SELECT pr.id, pr.numero_empleado, pr.rfc, pr.nss, pr.cedula_profesional,
                   pr.especialidad, pr.turno, pr.tipo_contrato, pr.nivel_estudios,
                   pr.fecha_ingreso_inst, pr.is_active, pr.plantel_id, pr.persona_id,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal, p.nacionalidad,
                   pl.nombre_plantel
            FROM ades_profesores pr
            JOIN ades_personas p ON p.id = pr.persona_id
            LEFT JOIN ades_planteles pl ON pl.id = pr.plantel_id
            WHERE pr.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        if (plantelId != null) { sql.append("AND pr.plantel_id = ? "); params.add(plantelId); }

        if (grupoId != null) {
            sql.append("""
                AND pr.id IN (
                    SELECT id FROM ades_profesores WHERE id IN (
                        SELECT profesor_titular_id FROM ades_grupos WHERE id = ?
                        UNION
                        SELECT profesor_id FROM ades_asignaciones_docentes WHERE grupo_id = ?
                    )
                )
                """);
            params.add(grupoId);
            params.add(grupoId);
        } else if (gradoId != null) {
            sql.append("""
                AND pr.id IN (
                    SELECT id FROM ades_profesores WHERE id IN (
                        SELECT profesor_titular_id FROM ades_grupos WHERE grado_id IN (SELECT id FROM ades_grados WHERE (numero_grado, nivel_educativo_id) = (SELECT numero_grado, nivel_educativo_id FROM ades_grados WHERE id = ?))
                        UNION
                        SELECT ad.profesor_id FROM ades_asignaciones_docentes ad 
                        JOIN ades_grupos g ON g.id = ad.grupo_id 
                        WHERE g.grado_id IN (SELECT id FROM ades_grados WHERE (numero_grado, nivel_educativo_id) = (SELECT numero_grado, nivel_educativo_id FROM ades_grados WHERE id = ?))
                    )
                )
                """);
            params.add(gradoId);
            params.add(gradoId);
        } else if (nivelId != null) {
            sql.append("""
                AND pr.id IN (
                    SELECT id FROM ades_profesores WHERE id IN (
                        SELECT profesor_titular_id FROM ades_grupos g 
                        JOIN ades_grados gr ON gr.id = g.grado_id 
                        WHERE gr.nivel_educativo_id = ?
                        UNION
                        SELECT ad.profesor_id FROM ades_asignaciones_docentes ad 
                        JOIN ades_grupos g ON g.id = ad.grupo_id 
                        JOIN ades_grados gr ON gr.id = g.grado_id 
                        WHERE gr.nivel_educativo_id = ?
                    )
                )
                """);
            params.add(nivelId);
            params.add(nivelId);
        }

        if (buscar != null && !buscar.isBlank()) {
            sql.append("AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? " +
                       "OR p.apellido_materno ILIKE ? OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?) ");
            String like = "%" + buscar.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        sql.append("ORDER BY p.apellido_paterno, p.nombre");

        List<Map<String, Object>> data = jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> persona = new LinkedHashMap<>();
            persona.put("nombre",           rs.getString("nombre"));
            persona.put("apellido_paterno", rs.getString("apellido_paterno"));
            persona.put("apellido_materno", rs.getString("apellido_materno"));
            persona.put("curp",             rs.getString("curp"));
            persona.put("genero",           rs.getString("genero"));
            persona.put("fecha_nacimiento", rs.getObject("fecha_nacimiento"));
            persona.put("telefono",         rs.getString("telefono"));
            persona.put("email_personal",   rs.getString("email_personal"));
            persona.put("nacionalidad",     rs.getString("nacionalidad"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",                rs.getObject("id",         UUID.class));
            row.put("numero_empleado",   rs.getString("numero_empleado"));
            row.put("rfc",               rs.getString("rfc"));
            row.put("nss",               rs.getString("nss"));
            row.put("cedula_profesional",rs.getString("cedula_profesional"));
            row.put("especialidad",      rs.getString("especialidad"));
            row.put("turno",             rs.getString("turno"));
            row.put("tipo_contrato",     rs.getString("tipo_contrato"));
            row.put("nivel_estudios",    rs.getString("nivel_estudios"));
            row.put("fecha_ingreso_inst",rs.getObject("fecha_ingreso_inst"));
            row.put("is_active",         rs.getBoolean("is_active"));
            row.put("plantel_id",        rs.getObject("plantel_id", UUID.class));
            row.put("plantel_nombre",    rs.getString("nombre_plantel"));
            row.put("persona_id",        rs.getObject("persona_id", UUID.class));
            row.put("persona",           persona);
            return row;
        }, params.toArray());

        return Map.of("data", data, "total", data.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT pr.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.rfc AS rfc_persona, p.genero, p.fecha_nacimiento,
                   p.telefono, p.email_personal, p.estado_civil,
                   p.municipio_nacimiento, p.estado_nacimiento, p.pais_nacimiento,
                   p.nacionalidad, p.foto_url
            FROM ades_profesores pr
            JOIN ades_personas p ON p.id = pr.persona_id
            WHERE pr.id = ?
            """, id);

        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public UUID resolverPersonaId(UUID profesorId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT persona_id FROM ades_profesores WHERE id = ?", profesorId);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        return (UUID) rows.get(0).get("persona_id");
    }
}

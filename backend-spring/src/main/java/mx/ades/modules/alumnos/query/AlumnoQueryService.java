package mx.ades.modules.alumnos.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
        return listar(plantelId, null, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(UUID plantelId, UUID grupoId) {
        return listar(plantelId, null, null, grupoId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(UUID plantelId, UUID nivelId, UUID gradoId, UUID grupoId) {
        StringBuilder sql = new StringBuilder("""
            SELECT e.id, e.matricula, e.nss, e.fecha_ingreso, e.is_active, e.tipo_alumno,
                   e.escuela_procedencia, e.promedio_procedencia, e.beca_tipo, e.folio_sep,
                   e.plantel_id, e.persona_id,
                   COALESCE(p.nombre_social, p.nombre) AS nombre,
                   p.apellido_paterno, p.apellido_materno, p.curp, p.rfc, p.genero,
                   p.fecha_nacimiento, p.telefono, p.email_personal, p.nacionalidad,
                   pl.nombre_plantel,
                   g.id AS grupo_id, g.nombre_grupo,
                   gr.id AS grado_id, gr.nombre_grado,
                   ne.id AS nivel_id, ne.nombre_nivel
            FROM ades_estudiantes e
            JOIN ades_personas p ON p.id = e.persona_id
            LEFT JOIN ades_planteles pl ON pl.id = e.plantel_id
            LEFT JOIN ades_inscripciones ins ON ins.estudiante_id = e.id AND ins.is_active = TRUE
            LEFT JOIN ades_grupos g ON g.id = ins.grupo_id AND g.is_active = TRUE
            LEFT JOIN ades_grados gr ON gr.id = g.grado_id AND gr.is_active = TRUE
            LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();

        sql.append("WHERE e.is_active = TRUE\n");

        if (plantelId != null) {
            sql.append("AND e.plantel_id = ?\n");
            params.add(plantelId);
        }

        if (grupoId != null) {
            sql.append("AND g.id = ?\n");
            params.add(grupoId);
        } else if (gradoId != null) {
            sql.append("AND gr.id = ?\n");
            params.add(gradoId);
        } else if (nivelId != null) {
            sql.append("AND ne.id = ?\n");
            params.add(nivelId);
        }

        sql.append("ORDER BY p.apellido_paterno, p.nombre");

        org.springframework.jdbc.core.RowMapper<Map<String, Object>> mapper = (rs, i) -> {
            Map<String, Object> persona = new LinkedHashMap<>();
            persona.put("nombre",           rs.getString("nombre"));
            persona.put("apellido_paterno", rs.getString("apellido_paterno"));
            persona.put("apellido_materno", rs.getString("apellido_materno"));
            persona.put("curp",             rs.getString("curp"));
            persona.put("rfc",              rs.getString("rfc"));
            persona.put("genero",           rs.getString("genero"));
            persona.put("fecha_nacimiento", rs.getObject("fecha_nacimiento"));
            persona.put("telefono",         rs.getString("telefono"));
            persona.put("email_personal",   rs.getString("email_personal"));
            persona.put("nacionalidad",     rs.getString("nacionalidad"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",                  rs.getObject("id", UUID.class));
            row.put("matricula",           rs.getString("matricula"));
            row.put("nss",                 rs.getString("nss"));
            row.put("fecha_ingreso",       rs.getObject("fecha_ingreso"));
            row.put("is_active",           rs.getBoolean("is_active"));
            row.put("tipo_alumno",         rs.getString("tipo_alumno"));
            row.put("escuela_procedencia", rs.getString("escuela_procedencia"));
            row.put("promedio_procedencia",rs.getObject("promedio_procedencia"));
            row.put("beca_tipo",           rs.getString("beca_tipo"));
            row.put("folio_sep",           rs.getString("folio_sep"));
            row.put("plantel_id",          rs.getObject("plantel_id", UUID.class));
            row.put("plantel_nombre",      rs.getString("nombre_plantel"));
            row.put("persona_id",          rs.getObject("persona_id", UUID.class));
            row.put("persona",             persona);

            Map<String, Object> nivelEducativo = new LinkedHashMap<>();
            nivelEducativo.put("id", rs.getObject("nivel_id", UUID.class));
            nivelEducativo.put("nombre_nivel", rs.getString("nombre_nivel"));

            Map<String, Object> grado = new LinkedHashMap<>();
            grado.put("id", rs.getObject("grado_id", UUID.class));
            grado.put("nombre_grado", rs.getString("nombre_grado"));

            Map<String, Object> grupo = new LinkedHashMap<>();
            grupo.put("id", rs.getObject("grupo_id", UUID.class));
            grupo.put("nombre_grupo", rs.getString("nombre_grupo"));

            row.put("nivel_educativo", nivelEducativo.get("id") != null ? nivelEducativo : null);
            row.put("grado",           grado.get("id") != null ? grado : null);
            row.put("grupo",           grupo.get("id") != null ? grupo : null);

            return row;
        };

        List<Map<String, Object>> data = jdbc.query(sql.toString(), mapper, params.toArray());

        return Map.of("data", data, "total", data.size());
    }

    @Cacheable(value = "alumnos", key = "#id")
    @Transactional(readOnly = true)
    public Map<String, Object> obtener(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT e.id, e.matricula, e.nss, e.fecha_ingreso, e.is_active, e.tipo_alumno,
                   e.plantel_id, e.persona_id, e.row_version,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, p.rfc,
                   p.genero, p.nombre_social, p.fecha_nacimiento,
                   p.telefono, p.email_personal, p.estado_civil, p.nacionalidad, p.foto_url
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

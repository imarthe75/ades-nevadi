package mx.ades.modules.medico.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalSaludQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listar(UUID plantelId, String buscar) {
        StringBuilder sql = new StringBuilder("""
            SELECT ps.id, ps.numero_empleado, ps.cedula_profesional, ps.especialidad,
                   ps.tipo_contrato, ps.nivel_estudios, ps.turno,
                   ps.fecha_ingreso_inst, ps.rfc, ps.nss, ps.is_active,
                   ps.plantel_id, ps.persona_id,
                   p.nombre, p.apellido_paterno, p.apellido_materno,
                   p.curp, p.telefono, p.email_personal,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p   ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append(" AND ps.plantel_id = ?");
            params.add(plantelId);
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?)");
            String like = "%" + buscar.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append(" ORDER BY p.apellido_paterno, p.nombre");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> detalle(UUID saludId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ps.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p   ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.id = ?
            """, saludId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}

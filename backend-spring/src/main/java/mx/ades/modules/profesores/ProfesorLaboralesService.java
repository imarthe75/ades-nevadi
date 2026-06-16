package mx.ades.modules.profesores;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class ProfesorLaboralesService {

    private final JdbcTemplate jdbc;

    public ProfesorLaboralesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void actualizar(UUID profesorId, Map<String, Object> lab) {
        if (lab == null || lab.isEmpty()) return;
        Date fechaIngreso = lab.get("fecha_ingreso_inst") != null
                ? Date.valueOf(lab.get("fecha_ingreso_inst").toString().substring(0, 10))
                : null;
        jdbc.update("""
            UPDATE ades_profesores
               SET tipo_contrato       = ?,
                   rfc                 = ?,
                   nss                 = ?,
                   cedula_profesional  = ?,
                   especialidad        = ?,
                   nivel_estudios      = ?,
                   fecha_ingreso_inst  = ?,
                   clabe               = ?,
                   banco               = ?,
                   turno               = ?
             WHERE id = ?
            """,
                lab.get("tipo_contrato"), lab.get("rfc"), lab.get("nss"),
                lab.get("cedula_profesional"), lab.get("especialidad"), lab.get("nivel_estudios"),
                fechaIngreso, lab.get("clabe"), lab.get("banco"), lab.get("turno"),
                profesorId);
    }
}

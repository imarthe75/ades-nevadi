package mx.ades.modules.alumnos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AlumnoComplementariosService {

    private final JdbcTemplate jdbc;

    public AlumnoComplementariosService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void actualizar(UUID estudianteId, Map<String, Object> comp) {
        if (comp == null || comp.isEmpty()) return;
        Object lengInd = comp.get("lengua_indigena_id");
        Object nivIng  = comp.get("nivel_ingles_id");
        jdbc.update("""
            UPDATE ades_estudiantes
               SET nss                   = ?,
                   discapacidad          = ?,
                   escuela_procedencia   = ?,
                   clave_ct_procedencia  = ?,
                   promedio_procedencia  = ?,
                   beca_tipo             = ?,
                   beca_monto            = ?,
                   nivel_socioeconomico  = ?,
                   etnia                 = ?,
                   lengua_indigena_id    = ?::uuid,
                   nivel_ingles_id       = ?::uuid
             WHERE id = ?
            """,
                comp.get("nss"), comp.get("discapacidad"), comp.get("escuela_procedencia"),
                comp.get("clave_ct_procedencia"),
                comp.get("promedio_procedencia") != null ? Double.parseDouble(comp.get("promedio_procedencia").toString()) : null,
                comp.get("beca_tipo"),
                comp.get("beca_monto") != null ? Double.parseDouble(comp.get("beca_monto").toString()) : null,
                comp.get("nivel_socioeconomico"), comp.get("etnia"),
                lengInd != null ? lengInd.toString() : null,
                nivIng  != null ? nivIng.toString()  : null,
                estudianteId);
    }
}

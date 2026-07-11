package mx.ades.shared.persona;

import mx.ades.common.PiiEncryptionService;
import mx.ades.common.ValidationUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsula el UPDATE de ades_personas reutilizado por alumnos, profesores y otros módulos.
 * Extraído de AlumnoController y ProfesorController (Strangler Fig — FASE 4).
 */
@Component
public class PersonaUpdateHelper {

    private final JdbcTemplate jdbc;
    private final PiiEncryptionService pii;

    public PersonaUpdateHelper(JdbcTemplate jdbc, PiiEncryptionService pii) {
        this.jdbc = jdbc;
        this.pii = pii;
    }

    /**
     * Aplica un PATCH parcial a ades_personas. Todos los campos son opcionales;
     * null respeta el valor existente salvo cuando la semántica requiere borrado explícito.
     */
    public void actualizar(UUID personaId, Map<String, Object> per) {
        if (per == null || per.isEmpty()) return;
        ValidationUtils.validarPersonaMap(per);

        Date fechaNac = per.get("fecha_nacimiento") != null
                ? Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10))
                : null;
        String curp = str(per.get("curp"));
        String telefono = str(per.get("telefono"));
        String email = str(per.get("email_personal"));

        jdbc.update("""
            UPDATE ades_personas
               SET nombre                    = COALESCE(?, nombre),
                   apellido_paterno          = COALESCE(?, apellido_paterno),
                   apellido_materno          = ?,
                   curp                      = COALESCE(?, curp),
                   curp_encrypted            = COALESCE(?, curp_encrypted),
                   curp_hash                 = COALESCE(?, curp_hash),
                   genero                    = ?,
                   fecha_nacimiento          = ?,
                   telefono                  = ?,
                   telefono_encrypted        = ?,
                   telefono_hash             = ?,
                   email_personal            = ?,
                   email_personal_encrypted  = ?,
                   email_personal_hash       = ?,
                   estado_civil              = ?,
                   pais_nacimiento           = ?,
                   municipio_nacimiento      = ?,
                   estado_nacimiento         = ?,
                   nacionalidad              = COALESCE(?, nacionalidad),
                   nombre_social             = ?,
                   genero_autopercibido      = ?,
                   pronombres                = ?
             WHERE id = ?
            """,
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                curp, pii.encrypt(curp), pii.hash(curp),
                per.get("genero"),
                fechaNac,
                telefono, pii.encrypt(telefono), pii.hash(telefono),
                email, pii.encrypt(email), pii.hash(email),
                per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"),
                per.get("nombre_social"), per.get("genero_autopercibido"), per.get("pronombres"),
                personaId);
    }

    /** Variante sin campos de identidad de género (para profesores). */
    public void actualizarBasico(UUID personaId, Map<String, Object> per) {
        if (per == null || per.isEmpty()) return;
        ValidationUtils.validarPersonaMap(per);

        Date fechaNac = per.get("fecha_nacimiento") != null
                ? Date.valueOf(per.get("fecha_nacimiento").toString().substring(0, 10))
                : null;
        String curp = str(per.get("curp"));
        String telefono = str(per.get("telefono"));
        String email = str(per.get("email_personal"));

        jdbc.update("""
            UPDATE ades_personas
               SET nombre               = COALESCE(?, nombre),
                   apellido_paterno     = COALESCE(?, apellido_paterno),
                   apellido_materno     = ?,
                   curp                 = COALESCE(?, curp),
                   curp_encrypted       = COALESCE(?, curp_encrypted),
                   curp_hash            = COALESCE(?, curp_hash),
                   genero               = ?,
                   fecha_nacimiento     = ?,
                   telefono             = ?,
                   telefono_encrypted   = ?,
                   telefono_hash        = ?,
                   email_personal       = ?,
                   email_personal_encrypted = ?,
                   email_personal_hash  = ?,
                   estado_civil         = ?,
                   pais_nacimiento      = ?,
                   municipio_nacimiento = ?,
                   estado_nacimiento    = ?,
                   nacionalidad         = COALESCE(?, nacionalidad)
             WHERE id = ?
            """,
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                curp, pii.encrypt(curp), pii.hash(curp),
                per.get("genero"),
                fechaNac,
                telefono, pii.encrypt(telefono), pii.hash(telefono),
                email, pii.encrypt(email), pii.hash(email),
                per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"),
                personaId);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

package mx.ades.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

public class ValidationUtils {

    // CURP: posición 9 acepta H (hombre), M (mujer) o X (no binario — reforma RENAPO 2021)
    private static final Pattern CURP_PATTERN = Pattern.compile("^[A-Z]{4}\\d{6}[HMX][A-Z]{5}[A-Z\\d]\\d$");

    // Standard official RFC pattern (12 or 13 characters)
    private static final Pattern RFC_PATTERN = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$");

    // Email pattern alineado al CHECK de BD (chk_cont_fam_email / chk_personas_email_personal:
    // email ~~ '%@%.%') — exige un '.' después del '@', no solo la presencia de '@'.
    // Sin este alineamiento, un correo como "user@localhost" pasaba la validación 422
    // de la app pero violaba el CHECK constraint al insertar (500/409 crudo).
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@.+\\..+$");

    // Phone number pattern (exactly 10 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

    // NSS (Número de Seguridad Social IMSS): exactamente 11 dígitos
    private static final Pattern NSS_PATTERN = Pattern.compile("^\\d{11}$");

    // Nombre/apellido: letras (incl. acentos/Ñ), espacios, guiones, apóstrofes.
    // Mismo juego de caracteres que InputFormattersService.formatNombre en el frontend.
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("^[\\p{L}\\s'\\-]+$");

    public static void validarCURP(String curp) {
        if (curp == null || curp.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La CURP es obligatoria.");
        }
        String cleanCurp = curp.trim().toUpperCase();
        // Allow mock/synthetic/system CURPs starting with TGEN, XE, or XP for local/test data
        if (cleanCurp.startsWith("TGEN") || cleanCurp.startsWith("XE") || cleanCurp.startsWith("XP")) {
            if (cleanCurp.length() != 18) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La CURP sintética debe tener exactamente 18 caracteres.");
            }
            return;
        }
        if (!CURP_PATTERN.matcher(cleanCurp).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El formato de la CURP es inválido.");
        }
    }

    public static void validarRFC(String rfc) {
        if (rfc == null || rfc.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El RFC es obligatorio.");
        }
        String cleanRfc = rfc.trim().toUpperCase();
        // Allow mock/synthetic RFCs starting with XAXX, XEXX, etc.
        if (cleanRfc.startsWith("XAXX") || cleanRfc.startsWith("XEXX")) {
            return;
        }
        if (!RFC_PATTERN.matcher(cleanRfc).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El formato del RFC es inválido.");
        }
    }

    public static void validarEmail(String email) {
        if (email != null && !email.trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El formato del correo electrónico es inválido.");
            }
        }
    }

    public static void validarTelefono(String telefono) {
        if (telefono != null && !telefono.trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(telefono.trim()).matches()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El formato del teléfono es inválido (deben ser 10 dígitos).");
            }
        }
    }

    /** Como {@link #validarCURP}, pero no exige el campo (para PATCH parciales donde curp es opcional). */
    public static void validarCURPSiPresente(String curp) {
        if (curp != null && !curp.trim().isEmpty()) {
            validarCURP(curp);
        }
    }

    /** Como {@link #validarRFC}, pero no exige el campo (para PATCH parciales donde rfc es opcional). */
    public static void validarRFCSiPresente(String rfc) {
        if (rfc != null && !rfc.trim().isEmpty()) {
            validarRFC(rfc);
        }
    }

    public static void validarNSS(String nss) {
        if (nss != null && !nss.trim().isEmpty()) {
            if (!NSS_PATTERN.matcher(nss.trim()).matches()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El NSS debe tener 11 dígitos.");
            }
        }
    }

    /**
     * Guarda contra cadenas excesivamente largas. Los VARCHAR(n) de PostgreSQL ya
     * truncan/rechazan a nivel de columna, pero eso produce un 500 crudo; esto da
     * un 422 con mensaje claro antes de llegar al SQL.
     */
    public static void validarLongitud(String value, int max, String etiqueta) {
        if (value != null && value.length() > max) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                etiqueta + " excede la longitud máxima de " + max + " caracteres.");
        }
    }

    /** Nombre/apellido: solo letras, espacios, guiones y apóstrofes; máx. 100 (VARCHAR(100) en ades_personas). */
    public static void validarNombrePersona(String value, String etiqueta) {
        if (value == null || value.trim().isEmpty()) return;
        validarLongitud(value, 100, etiqueta);
        if (!NOMBRE_PATTERN.matcher(value.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                etiqueta + " solo puede contener letras, espacios, guiones y apóstrofes.");
        }
    }

    /**
     * Valida el mapa de campos de {@code ades_personas} usado por los PATCH parciales
     * de alumnos, profesores y personal administrativo (AlumnoController, ProfesorController,
     * PersonalAdminApplicationService — todos comparten esta forma vía PersonaUpdateHelper /
     * PersonalAdminPersistenceAdapter). Todos los campos son opcionales (PATCH parcial); solo
     * se valida formato/longitud cuando el valor está presente. Límites alineados a las
     * columnas VARCHAR reales de db/migrations (001_initial_schema.sql, 011_datos_complementarios.sql,
     * 057_inclusion_genero.sql).
     */
    public static void validarPersonaMap(Map<String, Object> per) {
        if (per == null) return;
        validarNombrePersona(str(per.get("nombre")), "El nombre");
        validarNombrePersona(str(per.get("apellido_paterno")), "El apellido paterno");
        validarNombrePersona(str(per.get("apellido_materno")), "El apellido materno");
        validarCURPSiPresente(str(per.get("curp")));
        validarTelefono(str(per.get("telefono")));
        validarEmail(str(per.get("email_personal")));
        validarLongitud(str(per.get("estado_civil")), 20, "El estado civil");
        validarLongitud(str(per.get("nacionalidad")), 50, "La nacionalidad");
        validarLongitud(str(per.get("nombre_social")), 150, "El nombre social");
        validarLongitud(str(per.get("genero_autopercibido")), 40, "El género autopercibido");
        validarLongitud(str(per.get("pronombres")), 40, "Los pronombres");
    }

    /**
     * Valida el mapa de campos laborales (rfc, nss) usado por el alta/PATCH de
     * personal administrativo (PersonalAdminApplicationService). Igual que
     * {@link #validarPersonaMap}, todos los campos son opcionales.
     */
    public static void validarLaboralesMap(Map<String, Object> lab) {
        if (lab == null) return;
        validarRFCSiPresente(str(lab.get("rfc")));
        validarNSS(str(lab.get("nss")));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static final DateTimeFormatter FORMATO_FECHA_MX = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Acepta ISO (yyyy-MM-dd) y formato México (dd/MM/yyyy); 400 con mensaje claro si no calza con ninguno. */
    public static LocalDate parseFechaFlexible(String valor, String campo) {
        try {
            return valor.contains("/") ? LocalDate.parse(valor, FORMATO_FECHA_MX) : LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    campo + " inválida. Use DD/MM/YYYY o YYYY-MM-DD. Recibido: " + valor);
        }
    }

    // Año mínimo para datos históricos en México (reglamentación educativa SEP comienza ~1921)
    private static final int YEAR_MIN = 1900;

    /**
     * Valida que una fecha de nacimiento sea coherente: no futura y no anterior a 1900.
     * Rechaza valores como año 1026 (pasado lejano) o 2099 (futuro).
     */
    public static void validarFechaNacimiento(LocalDate fecha) {
        if (fecha == null) return;
        int year = fecha.getYear();
        int currentYear = LocalDate.now().getYear();
        if (year < YEAR_MIN || year > currentYear) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "La fecha de nacimiento es inválida: el año debe estar entre " + YEAR_MIN + " y " + currentYear + ".");
        }
    }
}

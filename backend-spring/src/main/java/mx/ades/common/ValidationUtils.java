package mx.ades.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.regex.Pattern;

public class ValidationUtils {

    // CURP: posición 9 acepta H (hombre), M (mujer) o X (no binario — reforma RENAPO 2021)
    private static final Pattern CURP_PATTERN = Pattern.compile("^[A-Z]{4}\\d{6}[HMX][A-Z]{5}[A-Z\\d]\\d$");

    // Standard official RFC pattern (12 or 13 characters)
    private static final Pattern RFC_PATTERN = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$");

    // Standard simple email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    // Phone number pattern (exactly 10 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

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
}

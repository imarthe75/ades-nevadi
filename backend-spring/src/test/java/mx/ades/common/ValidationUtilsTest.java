package mx.ades.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre validarPersonaMap/validarLaboralesMap: los guardas de caracteres/longitud
 * aplicados a los PATCH de alumnos, profesores y personal administrativo
 * (PersonaUpdateHelper, PersonalAdminApplicationService).
 */
class ValidationUtilsTest {

    @Test
    void rechazaNombreConCaracteresPeligrosos() {
        Map<String, Object> per = Map.of("nombre", "Juan<script>alert(1)</script>");
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaNombreConNumeros() {
        Map<String, Object> per = Map.of("nombre", "Juan123");
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void aceptaNombreConAcentosYGuiones() {
        Map<String, Object> per = Map.of("nombre", "María José", "apellido_paterno", "Núñez-López");
        assertDoesNotThrow(() -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaNombreExcesivamenteLargo() {
        String largo = "a".repeat(101);
        Map<String, Object> per = Map.of("nombre", largo);
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaCurpConLongitudInvalida() {
        Map<String, Object> per = Map.of("curp", "AAAA010101HDFRRL0"); // 17 chars, falta uno
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void aceptaCurpValida() {
        Map<String, Object> per = Map.of("curp", "AAAA010101HDFRRL09");
        assertDoesNotThrow(() -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void curpAusenteNoFallaEnPatchParcial() {
        // PATCH parcial: curp es opcional, ausente no debe lanzar "obligatoria".
        Map<String, Object> per = Map.of("nombre", "Ana");
        assertDoesNotThrow(() -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaTelefonoConLetras() {
        Map<String, Object> per = Map.of("telefono", "555-ABCD-12");
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaPronombresExcesivamenteLargos() {
        Map<String, Object> per = Map.of("pronombres", "x".repeat(41));
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarPersonaMap(per));
    }

    @Test
    void rechazaNssConLongitudInvalida() {
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarNSS("12345"));
    }

    @Test
    void aceptaNssValido() {
        assertDoesNotThrow(() -> ValidationUtils.validarNSS("12345678901"));
    }

    @Test
    void laboralesMapRechazaRfcInvalido() {
        Map<String, Object> lab = Map.of("rfc", "123");
        assertThrows(ResponseStatusException.class, () -> ValidationUtils.validarLaboralesMap(lab));
    }

    @Test
    void mapaVacioNoFalla() {
        assertDoesNotThrow(() -> ValidationUtils.validarPersonaMap(Map.of()));
        assertDoesNotThrow(() -> ValidationUtils.validarPersonaMap(null));
    }
}

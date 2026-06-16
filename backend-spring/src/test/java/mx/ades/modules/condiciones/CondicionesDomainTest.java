package mx.ades.modules.condiciones;

import mx.ades.modules.condiciones.domain.model.TipoCondicion;
import mx.ades.modules.condiciones.domain.port.in.ActualizarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.RegistrarCondicionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class CondicionesDomainTest {

    // ── TipoCondicion ─────────────────────────────────────────────────────────

    @Test
    void tipoCondicion_of_caseInsensitive() {
        assertEquals(TipoCondicion.EPILEPSIA, TipoCondicion.of("epilepsia"));
        assertEquals(TipoCondicion.ASMA, TipoCondicion.of("ASMA"));
        assertEquals(TipoCondicion.DISCAPACIDAD_VISUAL, TipoCondicion.of("Discapacidad_Visual"));
    }

    @Test
    void tipoCondicion_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoCondicion.of("GRIPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_condicion inválido");
    }

    @Test
    void tipoCondicion_of_null_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoCondicion.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_condicion es requerido");
    }

    @ParameterizedTest(name = "{0}.requiereMedicacion={1}")
    @CsvSource({
            "EPILEPSIA, true",
            "DIABETES, true",
            "CARDIACA, true",
            "HIPERTENSION, true",
            "ASMA, false",
            "ALERGIA, false",
            "DISCAPACIDAD_VISUAL, false",
            "OTRA, false",
    })
    void tipoCondicion_requiereMedicacion(TipoCondicion tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereMedicacion());
    }

    @ParameterizedTest(name = "{0}.esDiscapacidad={1}")
    @CsvSource({
            "DISCAPACIDAD_VISUAL, true",
            "DISCAPACIDAD_AUDITIVA, true",
            "EPILEPSIA, false",
            "ASMA, false",
    })
    void tipoCondicion_esDiscapacidad(TipoCondicion tipo, boolean esperado) {
        assertEquals(esperado, tipo.esDiscapacidad());
    }

    // ── RegistrarCondicionUseCase.Command ─────────────────────────────────────

    @Test
    void command_registrar_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarCondicionUseCase.Command(
                null, TipoCondicion.ASMA, "Asma leve", null, null, null, null, null, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alumno_id");
    }

    @Test
    void command_registrar_sinDescripcion_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarCondicionUseCase.Command(
                UUID.randomUUID(), TipoCondicion.ASMA, "  ", null, null, null, null, null, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descripcion");
    }

    @Test
    void command_registrar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarCondicionUseCase.Command(
                UUID.randomUUID(), TipoCondicion.DIABETES, "Diabetes tipo 2",
                "Metformina", "500mg", "Diaria", null, "Dr. García", "5551234567", 3))
                .doesNotThrowAnyException();
    }

    // ── ActualizarCondicionUseCase.Command ────────────────────────────────────

    @Test
    void command_actualizar_sinId_lanzaExcepcion() {
        assertThatThrownBy(() -> new ActualizarCondicionUseCase.Command(
                null, "nueva descripcion", null, null, null, null, null, null, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condicion_id");
    }

    @Test
    void command_actualizar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new ActualizarCondicionUseCase.Command(
                UUID.randomUUID(), null, "Paracetamol", null, null, null, null, null, false, 2))
                .doesNotThrowAnyException();
    }
}

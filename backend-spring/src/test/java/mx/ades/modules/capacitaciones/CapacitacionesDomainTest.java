package mx.ades.modules.capacitaciones;

import mx.ades.modules.capacitaciones.domain.model.AreaFormacion;
import mx.ades.modules.capacitaciones.domain.model.ModalidadCapacitacion;
import mx.ades.modules.capacitaciones.domain.model.TipoCertificacion;
import mx.ades.modules.capacitaciones.domain.port.in.RegistrarCapacitacionUseCase;
import mx.ades.modules.capacitaciones.domain.port.in.ValidarCapacitacionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class CapacitacionesDomainTest {

    // ── TipoCertificacion ─────────────────────────────────────────────────────

    @Test
    void tipo_of_caseInsensitive() {
        assertEquals(TipoCertificacion.DIPLOMADO, TipoCertificacion.of("diplomado"));
        assertEquals(TipoCertificacion.CERTIFICACION, TipoCertificacion.of("CERTIFICACION"));
    }

    @Test
    void tipo_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoCertificacion.of("MASTERCLASS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_certificacion inválido");
    }

    @ParameterizedTest(name = "{0}.esFormacionFormal={1}")
    @CsvSource({
            "DIPLOMADO, true",
            "POSGRADO, true",
            "CERTIFICACION, true",
            "CURSO, false",
            "TALLER, false",
            "CONGRESO, false",
    })
    void tipo_esFormacionFormal(TipoCertificacion tipo, boolean esperado) {
        assertEquals(esperado, tipo.esFormacionFormal());
    }

    // ── ModalidadCapacitacion ─────────────────────────────────────────────────

    @Test
    void modalidad_of_caseInsensitive() {
        assertEquals(ModalidadCapacitacion.EN_LINEA, ModalidadCapacitacion.of("en_linea"));
        assertEquals(ModalidadCapacitacion.HIBRIDA, ModalidadCapacitacion.of("HIBRIDA"));
    }

    @Test
    void modalidad_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> ModalidadCapacitacion.of("VIRTUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modalidad inválida");
    }

    @ParameterizedTest(name = "{0}.tienePresenciaFisica={1}")
    @CsvSource({"PRESENCIAL, true", "HIBRIDA, true", "EN_LINEA, false"})
    void modalidad_presenciaFisica(ModalidadCapacitacion modalidad, boolean esperado) {
        assertEquals(esperado, modalidad.tienePresenciaFisica());
    }

    // ── AreaFormacion ─────────────────────────────────────────────────────────

    @Test
    void area_ofNullable_null_retornaNull() {
        assertNull(AreaFormacion.ofNullable(null));
        assertNull(AreaFormacion.ofNullable(""));
    }

    @Test
    void area_ofNullable_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> AreaFormacion.ofNullable("DEPORTES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("area_formacion inválida");
    }

    // ── RegistrarCapacitacionUseCase.Command ──────────────────────────────────

    @Test
    void command_registrar_sinDocenteId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarCapacitacionUseCase.Command(
                null, "Taller Python", TipoCertificacion.TALLER,
                ModalidadCapacitacion.EN_LINEA, null,
                LocalDate.now(), LocalDate.now().plusDays(2),
                BigDecimal.valueOf(8), "UAEMEX", null, null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docente_id");
    }

    @Test
    void command_registrar_duracionNegativa_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarCapacitacionUseCase.Command(
                UUID.randomUUID(), "Taller Python", TipoCertificacion.TALLER,
                ModalidadCapacitacion.EN_LINEA, null,
                LocalDate.now(), LocalDate.now().plusDays(2),
                BigDecimal.valueOf(-1), "UAEMEX", null, null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duracion_hrs");
    }

    @Test
    void command_registrar_fechaFinAntes_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarCapacitacionUseCase.Command(
                UUID.randomUUID(), "Taller Python", TipoCertificacion.TALLER,
                ModalidadCapacitacion.EN_LINEA, null,
                LocalDate.now().plusDays(5), LocalDate.now(),
                BigDecimal.valueOf(8), "UAEMEX", null, null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha_fin");
    }

    @Test
    void command_registrar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarCapacitacionUseCase.Command(
                UUID.randomUUID(), "Diplomado Evaluación", TipoCertificacion.DIPLOMADO,
                ModalidadCapacitacion.HIBRIDA, AreaFormacion.PEDAGOGIA,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 30),
                BigDecimal.valueOf(120), "UAEMEX Toluca", "UAEMEX-2026-001", null, "dir"))
                .doesNotThrowAnyException();
    }

    // ── ValidarCapacitacionUseCase.Command ────────────────────────────────────

    @Test
    void command_validar_sinCapacitacionId_lanzaExcepcion() {
        assertThatThrownBy(() -> new ValidarCapacitacionUseCase.Command(
                null, UUID.randomUUID(), "rh", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacitacion_id");
    }
}

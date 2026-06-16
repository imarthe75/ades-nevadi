package mx.ades.modules.asistencia_personal;

import mx.ades.modules.asistencia_personal.domain.model.TipoJornada;
import mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AsistenciaPersonalDomainTest {

    // ── TipoJornada ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.esAsistencia={1}")
    @CsvSource({"COMPLETA, true", "MEDIA, true", "NINGUNA, false", "INCAPACIDAD, false", "VACACIONES, false", "PERMISO, false"})
    void tipoJornada_esAsistencia(TipoJornada tj, boolean esperado) {
        assertEquals(esperado, tj.esAsistencia());
    }

    @ParameterizedTest(name = "{0}.esFalta={1}")
    @CsvSource({"NINGUNA, true", "COMPLETA, false", "MEDIA, false", "INCAPACIDAD, false"})
    void tipoJornada_esFalta(TipoJornada tj, boolean esperado) {
        assertEquals(esperado, tj.esFalta());
    }

    @ParameterizedTest(name = "{0}.esAusenciaJustificada={1}")
    @CsvSource({"INCAPACIDAD, true", "VACACIONES, true", "PERMISO, true", "COMPLETA, false", "NINGUNA, false"})
    void tipoJornada_esAusenciaJustificada(TipoJornada tj, boolean esperado) {
        assertEquals(esperado, tj.esAusenciaJustificada());
    }

    @Test
    void tipoJornada_of_caseInsensitive() {
        assertEquals(TipoJornada.COMPLETA, TipoJornada.of("completa"));
        assertEquals(TipoJornada.MEDIA, TipoJornada.of("MEDIA"));
        assertEquals(TipoJornada.NINGUNA, TipoJornada.of("ninguna"));
    }

    @Test
    void tipoJornada_of_nulo_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoJornada.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_jornada");
    }

    @Test
    void tipoJornada_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoJornada.of("AUSENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUSENTE");
    }

    @Test
    void tipoJornada_ofDefault_nulo_retornaCompleta() {
        assertEquals(TipoJornada.COMPLETA, TipoJornada.ofDefault(null));
        assertEquals(TipoJornada.COMPLETA, TipoJornada.ofDefault(""));
        assertEquals(TipoJornada.COMPLETA, TipoJornada.ofDefault("INVALIDO"));
    }

    // ── RegistrarAsistenciaUseCase.Command ────────────────────────────────────

    @Test
    void command_registrar_sinPersonaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarAsistenciaUseCase.Command(
                null, LocalDate.now(), null, null,
                TipoJornada.COMPLETA, false, 0, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persona_id");
    }

    @Test
    void command_registrar_sinFecha_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarAsistenciaUseCase.Command(
                UUID.randomUUID(), null, null, null,
                TipoJornada.COMPLETA, false, 0, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void command_registrar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarAsistenciaUseCase.Command(
                UUID.randomUUID(), LocalDate.of(2026, 6, 15),
                LocalTime.of(8, 0), LocalTime.of(16, 0),
                TipoJornada.COMPLETA, false, 0, null, "admin"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_registrar_retardo_valido() {
        assertThatCode(() -> new RegistrarAsistenciaUseCase.Command(
                UUID.randomUUID(), LocalDate.now(),
                LocalTime.of(8, 15), null,
                TipoJornada.COMPLETA, true, 15, "Tráfico", "admin"))
                .doesNotThrowAnyException();
    }

    // ── ActualizarAsistenciaUseCase.Command ───────────────────────────────────

    @Test
    void command_actualizar_sinId_lanzaExcepcion() {
        ActualizarAsistenciaUseCase.Patch patch = new ActualizarAsistenciaUseCase.Patch(
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> new ActualizarAsistenciaUseCase.Command(
                null, patch, null, "admin", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void command_actualizar_justificarSinPermiso_lanzaExcepcion() {
        ActualizarAsistenciaUseCase.Patch patch = new ActualizarAsistenciaUseCase.Patch(
                null, null, null, null, null, null, true, "Motivo");
        assertThatThrownBy(() -> new ActualizarAsistenciaUseCase.Command(
                UUID.randomUUID(), patch, UUID.randomUUID(), "docente", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Coordinador");
    }

    @Test
    void command_actualizar_justificarConPermiso_valido() {
        ActualizarAsistenciaUseCase.Patch patch = new ActualizarAsistenciaUseCase.Patch(
                null, null, null, null, null, null, true, "Justificación válida");
        assertThatCode(() -> new ActualizarAsistenciaUseCase.Command(
                UUID.randomUUID(), patch, UUID.randomUUID(), "coordinador", 3))
                .doesNotThrowAnyException();
    }

    @Test
    void command_actualizar_sinJustificado_cualquierNivel_valido() {
        ActualizarAsistenciaUseCase.Patch patch = new ActualizarAsistenciaUseCase.Patch(
                LocalTime.of(8, 0), LocalTime.of(16, 0), TipoJornada.COMPLETA,
                false, 0, "Observación", null, null);
        assertThatCode(() -> new ActualizarAsistenciaUseCase.Command(
                UUID.randomUUID(), patch, null, "docente", 5))
                .doesNotThrowAnyException();
    }
}

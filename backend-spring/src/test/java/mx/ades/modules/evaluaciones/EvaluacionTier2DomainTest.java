package mx.ades.modules.evaluaciones;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EvaluacionTier2DomainTest {

    // ── SlotHorario ───────────────────────────────────────────────────────────

    @Test
    void slot_valido_no_lanza_excepcion() {
        assertThatCode(() -> SlotHorario.of("08:00", "09:00")).doesNotThrowAnyException();
    }

    @Test
    void slot_fin_antes_que_inicio_lanza_excepcion() {
        assertThatThrownBy(() -> new SlotHorario(
                LocalTime.of(10, 0), LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hora_fin");
    }

    @Test
    void slots_iguales_lanza_excepcion() {
        assertThatThrownBy(() -> new SlotHorario(
                LocalTime.of(8, 0), LocalTime.of(8, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slots_solapados_conflictan() {
        SlotHorario a = SlotHorario.of("08:00", "10:00");
        SlotHorario b = SlotHorario.of("09:00", "11:00");
        assertThat(a.conflictaCon(b)).isTrue();
        assertThat(b.conflictaCon(a)).isTrue();
    }

    @Test
    void slot_contenido_conflicta() {
        SlotHorario ext = SlotHorario.of("08:00", "12:00");
        SlotHorario inner = SlotHorario.of("09:00", "10:00");
        assertThat(ext.conflictaCon(inner)).isTrue();
    }

    @Test
    void slots_adyacentes_no_conflictan() {
        SlotHorario a = SlotHorario.of("08:00", "09:00");
        SlotHorario b = SlotHorario.of("09:00", "10:00");
        assertThat(a.conflictaCon(b)).isFalse();
    }

    @Test
    void slots_separados_no_conflictan() {
        SlotHorario mañana = SlotHorario.of("08:00", "10:00");
        SlotHorario tarde = SlotHorario.of("14:00", "16:00");
        assertThat(mañana.conflictaCon(tarde)).isFalse();
    }

    @Test
    void slot_of_formato_invalido_lanza_excepcion() {
        assertThatThrownBy(() -> SlotHorario.of("8am", "10am"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato de hora");
    }

    // ── CalificarEvaluacionMasivoUseCase.EntradaCalificacion ──────────────────

    @Test
    void entrada_calificacion_valida_rango() {
        assertThatCode(() ->
            new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(UUID.randomUUID(), 7.5, "ok")
        ).doesNotThrowAnyException();
    }

    @Test
    void entrada_calificacion_negativa_lanza_excepcion() {
        assertThatThrownBy(() ->
            new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(UUID.randomUUID(), -0.1, null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("rango");
    }

    @Test
    void entrada_calificacion_mayor_10_lanza_excepcion() {
        assertThatThrownBy(() ->
            new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(UUID.randomUUID(), 10.01, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void command_lista_vacia_lanza_excepcion() {
        assertThatThrownBy(() ->
            new CalificarEvaluacionMasivoUseCase.Command(UUID.randomUUID(), List.of(), "prof")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("vacía");
    }

    @Test
    void command_nulo_lanza_excepcion() {
        assertThatThrownBy(() ->
            new CalificarEvaluacionMasivoUseCase.Command(UUID.randomUUID(), null, "prof")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

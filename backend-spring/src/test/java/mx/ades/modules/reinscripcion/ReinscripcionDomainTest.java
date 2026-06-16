package mx.ades.modules.reinscripcion;

import mx.ades.modules.reinscripcion.domain.model.AccionReinscripcion;
import mx.ades.modules.reinscripcion.domain.port.in.ProcesarAccionReinscripcionUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ReinscripcionDomainTest {

    // ── AccionReinscripcion ───────────────────────────────────────────────────

    @Test
    void rechazar_requiere_razon() {
        assertThat(AccionReinscripcion.RECHAZAR.requiereRazon()).isTrue();
    }

    @Test
    void aprobar_no_requiere_razon() {
        assertThat(AccionReinscripcion.APROBAR.requiereRazon()).isFalse();
    }

    @Test
    void aprobar_to_estado_es_aprobado() {
        assertThat(AccionReinscripcion.APROBAR.toEstado()).isEqualTo("APROBADO");
    }

    @Test
    void rechazar_to_estado_es_rechazado() {
        assertThat(AccionReinscripcion.RECHAZAR.toEstado()).isEqualTo("RECHAZADO");
    }

    @Test
    void of_case_insensitive() {
        assertThat(AccionReinscripcion.of("aprobar")).isEqualTo(AccionReinscripcion.APROBAR);
        assertThat(AccionReinscripcion.of("RECHAZAR")).isEqualTo(AccionReinscripcion.RECHAZAR);
    }

    @Test
    void of_invalido_lanza_excepcion() {
        assertThatThrownBy(() -> AccionReinscripcion.of("CANCELAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accion inválida");
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Test
    void command_rechazar_sin_razon_lanza_excepcion() {
        assertThatThrownBy(() ->
            new ProcesarAccionReinscripcionUseCase.Command(
                    UUID.randomUUID(), AccionReinscripcion.RECHAZAR, null, UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("razon_rechazo");
    }

    @Test
    void command_rechazar_razon_vacia_lanza_excepcion() {
        assertThatThrownBy(() ->
            new ProcesarAccionReinscripcionUseCase.Command(
                    UUID.randomUUID(), AccionReinscripcion.RECHAZAR, "  ", UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void command_aprobar_sin_razon_es_valido() {
        assertThatCode(() ->
            new ProcesarAccionReinscripcionUseCase.Command(
                    UUID.randomUUID(), AccionReinscripcion.APROBAR, null, UUID.randomUUID())
        ).doesNotThrowAnyException();
    }

    @Test
    void command_rechazar_con_razon_es_valido() {
        assertThatCode(() ->
            new ProcesarAccionReinscripcionUseCase.Command(
                    UUID.randomUUID(), AccionReinscripcion.RECHAZAR,
                    "Adeudo pendiente de pago", UUID.randomUUID())
        ).doesNotThrowAnyException();
    }
}

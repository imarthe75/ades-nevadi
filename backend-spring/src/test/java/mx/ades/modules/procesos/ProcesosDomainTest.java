package mx.ades.modules.procesos;

import mx.ades.modules.procesos.domain.model.EstadoAdmision;
import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProcesosDomainTest {

    // ── EstadoAdmision ────────────────────────────────────────────────────────

    @Test
    void solo_aceptado_permite_preinscripcion() {
        assertThat(EstadoAdmision.ACEPTADO.permitePreinscripcion()).isTrue();
        assertThat(EstadoAdmision.PENDIENTE.permitePreinscripcion()).isFalse();
        assertThat(EstadoAdmision.RECHAZADO.permitePreinscripcion()).isFalse();
        assertThat(EstadoAdmision.INSCRITO.permitePreinscripcion()).isFalse();
    }

    @Test
    void es_resuelto_solo_para_estados_terminales() {
        assertThat(EstadoAdmision.ACEPTADO.esResuelto()).isTrue();
        assertThat(EstadoAdmision.RECHAZADO.esResuelto()).isTrue();
        assertThat(EstadoAdmision.INSCRITO.esResuelto()).isTrue();
        assertThat(EstadoAdmision.PENDIENTE.esResuelto()).isFalse();
        assertThat(EstadoAdmision.LISTA_ESPERA.esResuelto()).isFalse();
    }

    @Test
    void of_parsea_case_insensitive() {
        assertThat(EstadoAdmision.of("aceptado")).isEqualTo(EstadoAdmision.ACEPTADO);
        assertThat(EstadoAdmision.of("RECHAZADO")).isEqualTo(EstadoAdmision.RECHAZADO);
        assertThat(EstadoAdmision.of("  inscrito  ")).isEqualTo(EstadoAdmision.INSCRITO);
    }

    @Test
    void of_retorna_pendiente_para_valor_invalido_o_nulo() {
        assertThat(EstadoAdmision.of(null)).isEqualTo(EstadoAdmision.PENDIENTE);
        assertThat(EstadoAdmision.of("")).isEqualTo(EstadoAdmision.PENDIENTE);
        assertThat(EstadoAdmision.of("DESCONOCIDO")).isEqualTo(EstadoAdmision.PENDIENTE);
    }

    // ── ProcesarPreinscripcionUseCase.Command ─────────────────────────────────

    @Test
    void command_valido_se_construye_sin_excepcion() {
        assertThatNoException().isThrownBy(() ->
                new ProcesarPreinscripcionUseCase.Command(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "admin"));
    }

    @Test
    void command_sin_admision_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcesarPreinscripcionUseCase.Command(
                        null, UUID.randomUUID(), UUID.randomUUID(), "admin"));
    }

    @Test
    void command_sin_grupo_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcesarPreinscripcionUseCase.Command(
                        UUID.randomUUID(), UUID.randomUUID(), null, "admin"));
    }

    @Test
    void command_sin_ciclo_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcesarPreinscripcionUseCase.Command(
                        UUID.randomUUID(), null, UUID.randomUUID(), "admin"));
    }
}

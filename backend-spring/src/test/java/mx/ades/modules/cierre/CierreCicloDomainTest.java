package mx.ades.modules.cierre;

import mx.ades.modules.cierre.domain.port.in.CerrarCicloUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CierreCicloDomainTest {

    @Test
    void command_sinCicloId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CerrarCicloUseCase.Command(null, UUID.randomUUID(), 1, "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ciclo_id");
    }

    @Test
    void command_nivelSuperior2_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CerrarCicloUseCase.Command(UUID.randomUUID(), null, 3, "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Solo administradores");
    }

    @Test
    void command_nivelNulo_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CerrarCicloUseCase.Command(UUID.randomUUID(), null, null, "admin"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void command_nivel1_valido() {
        assertThatCode(() ->
            new CerrarCicloUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), 1, "admin"))
            .doesNotThrowAnyException();
    }

    @Test
    void command_nivel2_valido() {
        assertThatCode(() ->
            new CerrarCicloUseCase.Command(UUID.randomUUID(), null, 2, "director"))
            .doesNotThrowAnyException();
    }

    @Test
    void command_sinCicloDestino_valido() {
        assertThatCode(() ->
            new CerrarCicloUseCase.Command(UUID.randomUUID(), null, 1, "admin"))
            .doesNotThrowAnyException();
    }
}

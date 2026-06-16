package mx.ades.modules.notificaciones;

import mx.ades.modules.notificaciones.domain.port.in.MarcarLeidaUseCase;
import mx.ades.modules.notificaciones.domain.port.in.MarcarTodasLeidasUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class NotificacionDomainTest {

    // ── MarcarLeidaUseCase.Command ────────────────────────────────────────────

    @Test
    void command_marcarLeida_sinNotifId_lanzaExcepcion() {
        assertThatThrownBy(() -> new MarcarLeidaUseCase.Command(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notif_id");
    }

    @Test
    void command_marcarLeida_sinUsuarioId_lanzaExcepcion() {
        assertThatThrownBy(() -> new MarcarLeidaUseCase.Command(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario_id");
    }

    @Test
    void command_marcarLeida_valido_noLanzaExcepcion() {
        assertThatCode(() -> new MarcarLeidaUseCase.Command(UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    // ── MarcarTodasLeidasUseCase.Command ──────────────────────────────────────

    @Test
    void command_marcarTodas_sinUsuarioId_lanzaExcepcion() {
        assertThatThrownBy(() -> new MarcarTodasLeidasUseCase.Command(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario_id");
    }

    @Test
    void command_marcarTodas_valido_noLanzaExcepcion() {
        assertThatCode(() -> new MarcarTodasLeidasUseCase.Command(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}

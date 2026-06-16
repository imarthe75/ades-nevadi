package mx.ades.modules.medico;

import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MedicoDomainTest {

    // ── RegistrarPersonalSaludUseCase.Command ─────────────────────────────────

    @Test
    void registrar_sinPlantelId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarPersonalSaludUseCase.Command(null, Map.of(), Map.of(), "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("plantel_id");
    }

    @Test
    void registrar_sinPersona_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarPersonalSaludUseCase.Command(UUID.randomUUID(), null, Map.of(), "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("persona");
    }

    @Test
    void registrar_sinLaborales_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new RegistrarPersonalSaludUseCase.Command(UUID.randomUUID(), Map.of(), null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("laborales");
    }

    @Test
    void registrar_valido_noLanzaExcepcion() {
        assertThatCode(() ->
            new RegistrarPersonalSaludUseCase.Command(UUID.randomUUID(), Map.of(), Map.of(), "user"))
            .doesNotThrowAnyException();
    }

    // ── ActualizarPersonalSaludUseCase.Command ────────────────────────────────

    @Test
    void actualizar_sinSaludId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new ActualizarPersonalSaludUseCase.Command(null, null, null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("salud_id");
    }

    @Test
    void actualizar_valido_conPersonaNula_noLanzaExcepcion() {
        assertThatCode(() ->
            new ActualizarPersonalSaludUseCase.Command(UUID.randomUUID(), null, null, "user"))
            .doesNotThrowAnyException();
    }

    @Test
    void actualizar_valido_conDatos_noLanzaExcepcion() {
        assertThatCode(() ->
            new ActualizarPersonalSaludUseCase.Command(
                UUID.randomUUID(),
                Map.of("nombre", "María", "apellido_paterno", "López"),
                Map.of("especialidad", "Enfermería"),
                "admin"))
            .doesNotThrowAnyException();
    }
}

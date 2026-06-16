package mx.ades.modules.comunicados;

import mx.ades.modules.comunicados.domain.model.Periodicidad;
import mx.ades.modules.comunicados.domain.port.in.CrearComunicadoUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ComunicadoDomainTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 6, 15, 8, 0);

    @Test
    void diaria_agrega_un_dia() {
        assertThat(Periodicidad.DIARIA.calcularSiguiente(BASE)).isEqualTo(BASE.plusDays(1));
    }

    @Test
    void semanal_agrega_una_semana() {
        assertThat(Periodicidad.SEMANAL.calcularSiguiente(BASE)).isEqualTo(BASE.plusWeeks(1));
    }

    @Test
    void quincenal_agrega_15_dias() {
        assertThat(Periodicidad.QUINCENAL.calcularSiguiente(BASE)).isEqualTo(BASE.plusDays(15));
    }

    @Test
    void mensual_agrega_un_mes() {
        assertThat(Periodicidad.MENSUAL.calcularSiguiente(BASE)).isEqualTo(BASE.plusMonths(1));
    }

    @Test
    void trimestral_agrega_tres_meses() {
        assertThat(Periodicidad.TRIMESTRAL.calcularSiguiente(BASE)).isEqualTo(BASE.plusMonths(3));
    }

    @Test
    void of_case_insensitive() {
        assertThat(Periodicidad.of("semanal")).isEqualTo(Periodicidad.SEMANAL);
        assertThat(Periodicidad.of("TRIMESTRAL")).isEqualTo(Periodicidad.TRIMESTRAL);
    }

    @Test
    void of_invalido_devuelve_mensual_como_fallback() {
        assertThat(Periodicidad.of("DESCONOCIDO")).isEqualTo(Periodicidad.MENSUAL);
        assertThat(Periodicidad.of(null)).isEqualTo(Periodicidad.MENSUAL);
    }

    // ── CrearComunicadoUseCase.Command ────────────────────────────────────────

    @Test
    void command_crear_sinTitulo_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearComunicadoUseCase.Command(
                null, "Contenido válido", "GENERAL",
                null, null, null, false, null, false, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titulo");
    }

    @Test
    void command_crear_sinContenido_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearComunicadoUseCase.Command(
                "Título válido", "", "GENERAL",
                null, null, null, false, null, false, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contenido");
    }

    @Test
    void command_crear_sinCreadoPor_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearComunicadoUseCase.Command(
                "Título", "Contenido", "GENERAL",
                null, null, null, false, null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creado_por_id");
    }

    @Test
    void command_crear_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CrearComunicadoUseCase.Command(
                "Comunicado bienvenida", "Inicio de ciclo escolar 2026-2027", "GENERAL",
                UUID.randomUUID(), null, null, true,
                LocalDateTime.of(2026, 8, 31, 0, 0), false, null, UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}

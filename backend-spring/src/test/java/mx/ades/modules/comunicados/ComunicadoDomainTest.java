package mx.ades.modules.comunicados;

import mx.ades.modules.comunicados.domain.model.Periodicidad;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}

package mx.ades.modules.conducta;

import mx.ades.modules.conducta.domain.model.EstadoPlan;
import mx.ades.modules.conducta.domain.model.TipoFalta;
import mx.ades.modules.conducta.domain.model.TipoSancion;
import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.planeacion.domain.model.EstadoTema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class ConductaDomainTest {

    // ── TipoFalta ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.requiereSeguimiento={1}")
    @CsvSource({"LEVE, false", "GRAVE, true", "MUY_GRAVE, true"})
    void tipoFalta_requiereSeguimiento(TipoFalta tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereSeguimiento());
    }

    // ── TipoSancion ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.requiereNotificacionPadres={1}")
    @CsvSource({
            "AMONESTACION_VERBAL, false",
            "AMONESTACION_ESCRITA, false",
            "CITATORIO_PADRES, false",
            "ASISTENCIA_PADRES, true",
            "SUSPENSION, true",
            "EXPULSION, true",
    })
    void tipoSancion_requiereNotificacion(TipoSancion tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereNotificacionPadres());
    }

    // ── EstadoTema (planeación) ───────────────────────────────────────────────

    @Test
    void estadoTema_from_sinDatos_esPENDIENTE() {
        assertEquals(EstadoTema.PENDIENTE, EstadoTema.from(false, false));
    }

    @Test
    void estadoTema_from_conPlaneacion_esPLANEADO() {
        assertEquals(EstadoTema.PLANEADO, EstadoTema.from(false, true));
    }

    @Test
    void estadoTema_from_conAvanceCompletado_esIMPARTIDO() {
        assertEquals(EstadoTema.IMPARTIDO, EstadoTema.from(true, true));
    }

    @ParameterizedTest(name = "{0}.puedeAvanzarA({1})={2}")
    @CsvSource({
            "PENDIENTE, PLANEADO, true",
            "PENDIENTE, IMPARTIDO, false",
            "PLANEADO, IMPARTIDO, true",
            "PLANEADO, PENDIENTE, false",
            "IMPARTIDO, PLANEADO, false",
            "IMPARTIDO, PENDIENTE, false",
    })
    void estadoTema_transiciones(EstadoTema origen, EstadoTema destino, boolean esperado) {
        assertEquals(esperado, origen.puedeAvanzarA(destino));
    }

    // ── EstadoPlan ────────────────────────────────────────────────────────────

    @Test
    void borrador_permite_seguimiento() {
        assertThat(EstadoPlan.BORRADOR.permiteNuevoSeguimiento()).isTrue();
    }

    @Test
    void completado_no_permite_seguimiento_y_es_cerrado() {
        assertThat(EstadoPlan.COMPLETADO.permiteNuevoSeguimiento()).isFalse();
        assertThat(EstadoPlan.COMPLETADO.esCerrado()).isTrue();
    }

    @Test
    void of_case_insensitive_funciona() {
        assertThat(EstadoPlan.of("en_proceso")).isEqualTo(EstadoPlan.EN_PROCESO);
    }

    @Test
    void of_invalido_lanza_excepcion() {
        assertThatThrownBy(() -> EstadoPlan.of("INVALIDO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estado_plan inválido");
    }

    // ── CrearPlanMejoraUseCase.Command ────────────────────────────────────────

    @Test
    void command_objetivo_vacio_lanza_excepcion() {
        assertThatThrownBy(() -> new CrearPlanMejoraUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "  ", null, null, null, null, "prof"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objetivo_general");
    }

    @Test
    void command_sin_reporte_lanza_excepcion() {
        assertThatThrownBy(() -> new CrearPlanMejoraUseCase.Command(
                null, UUID.randomUUID(), null, UUID.randomUUID(),
                "Objetivo válido", null, null, null, null, "prof"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reporte_id");
    }

    @Test
    void command_valido_no_lanza_excepcion() {
        assertThatCode(() -> new CrearPlanMejoraUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Mejorar la conducta en clases", null, null, null, null, "prof"))
                .doesNotThrowAnyException();
    }
}

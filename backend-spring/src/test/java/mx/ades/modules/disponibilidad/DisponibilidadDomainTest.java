package mx.ades.modules.disponibilidad;

import mx.ades.modules.disponibilidad.domain.model.DiaSemana;
import mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class DisponibilidadDomainTest {

    // ── DiaSemana ─────────────────────────────────────────────────────────────

    @Test
    void diaSemana_fromIndice_todosLosDias() {
        assertEquals(DiaSemana.LUNES,    DiaSemana.fromIndice(0));
        assertEquals(DiaSemana.MARTES,   DiaSemana.fromIndice(1));
        assertEquals(DiaSemana.MIERCOLES,DiaSemana.fromIndice(2));
        assertEquals(DiaSemana.JUEVES,   DiaSemana.fromIndice(3));
        assertEquals(DiaSemana.VIERNES,  DiaSemana.fromIndice(4));
        assertEquals(DiaSemana.SABADO,   DiaSemana.fromIndice(5));
        assertEquals(DiaSemana.DOMINGO,  DiaSemana.fromIndice(6));
    }

    @Test
    void diaSemana_fromIndice_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> DiaSemana.fromIndice(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dia_semana inválido");
    }

    @ParameterizedTest(name = "{0}.esLaborable={1}")
    @CsvSource({
            "LUNES, true",
            "MARTES, true",
            "MIERCOLES, true",
            "JUEVES, true",
            "VIERNES, true",
            "SABADO, false",
            "DOMINGO, false",
    })
    void diaSemana_esLaborable(DiaSemana dia, boolean esperado) {
        assertEquals(esperado, dia.esLaborable());
    }

    @Test
    void diaSemana_nombreDeIndice_valido() {
        assertEquals("Lunes",   DiaSemana.nombreDeIndice(0));
        assertEquals("Viernes", DiaSemana.nombreDeIndice(4));
        assertEquals("Domingo", DiaSemana.nombreDeIndice(6));
    }

    @Test
    void diaSemana_nombreDeIndice_invalido_retornaInterrogacion() {
        assertEquals("?", DiaSemana.nombreDeIndice(99));
    }

    @Test
    void diaSemana_getIndice_correcto() {
        assertEquals(0, DiaSemana.LUNES.getIndice());
        assertEquals(6, DiaSemana.DOMINGO.getIndice());
    }

    // ── GuardarDisponibilidadUseCase.Command ──────────────────────────────────

    @Test
    void command_sinProfesorId_lanzaExcepcion() {
        List<GuardarDisponibilidadUseCase.Slot> slots = List.of(
                new GuardarDisponibilidadUseCase.Slot(0, LocalTime.of(8, 0), LocalTime.of(10, 0), true, null));
        assertThatThrownBy(() -> new GuardarDisponibilidadUseCase.Command(
                null, null, slots, null, null, "rh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profesor_id");
    }

    @Test
    void command_sinSlots_lanzaExcepcion() {
        assertThatThrownBy(() -> new GuardarDisponibilidadUseCase.Command(
                UUID.randomUUID(), null, List.of(), null, null, "rh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slots");
    }

    @Test
    void command_nulo_lanzaExcepcion() {
        assertThatThrownBy(() -> new GuardarDisponibilidadUseCase.Command(
                UUID.randomUUID(), null, null, null, null, "rh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slots");
    }

    @Test
    void command_valido_noLanzaExcepcion() {
        List<GuardarDisponibilidadUseCase.Slot> slots = List.of(
                new GuardarDisponibilidadUseCase.Slot(0, LocalTime.of(8, 0), LocalTime.of(10, 0), true, null),
                new GuardarDisponibilidadUseCase.Slot(1, LocalTime.of(10, 0), LocalTime.of(12, 0), true, null));
        assertThatCode(() -> new GuardarDisponibilidadUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), slots, 20.0, 16.0, "dir"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_sinCiclo_esValido() {
        List<GuardarDisponibilidadUseCase.Slot> slots = List.of(
                new GuardarDisponibilidadUseCase.Slot(4, LocalTime.of(7, 0), LocalTime.of(9, 0), true, null));
        assertThatCode(() -> new GuardarDisponibilidadUseCase.Command(
                UUID.randomUUID(), null, slots, null, null, "user"))
                .doesNotThrowAnyException();
    }
}

package mx.ades.modules.esquemas_ponderacion;

import mx.ades.modules.esquemas_ponderacion.domain.model.ItemPonderacion;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class EsquemasPonderacionDomainTest {

    // ── ItemPonderacion ───────────────────────────────────────────────────────

    @Test
    void item_sinTipo_lanzaExcepcion() {
        assertThatThrownBy(() -> new ItemPonderacion(null, null, 50.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_item");
    }

    @Test
    void item_pesoCero_lanzaExcepcion() {
        assertThatThrownBy(() -> new ItemPonderacion("EXAMEN", null, 0.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("peso_porcentaje");
    }

    @Test
    void item_valido_noLanzaExcepcion() {
        assertThatCode(() -> new ItemPonderacion("EXAMEN", "Examen Bimestral", 60.0, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void item_ordenDisplay_defaultUno() {
        ItemPonderacion item = new ItemPonderacion("TAREA", null, 40.0, null);
        assertEquals(1, item.ordenDisplay());
    }

    // ── CrearEsquemaUseCase.Command ───────────────────────────────────────────

    @Test
    void command_crear_sinNombre_lanzaExcepcion() {
        List<ItemPonderacion> items = List.of(new ItemPonderacion("EXAMEN", null, 100.0, 1));
        assertThatThrownBy(() -> new CrearEsquemaUseCase.Command(
                "", UUID.randomUUID(), null, LocalDate.now(), null,
                items, UUID.randomUUID(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void command_crear_sumaNoBaja100_lanzaExcepcion() {
        List<ItemPonderacion> items = List.of(
                new ItemPonderacion("EXAMEN", null, 60.0, 1),
                new ItemPonderacion("TAREA", null, 30.0, 2));
        assertThatThrownBy(() -> new CrearEsquemaUseCase.Command(
                "Esquema General", UUID.randomUUID(), null, LocalDate.now(), null,
                items, UUID.randomUUID(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sumar 100%");
    }

    @Test
    void command_crear_suma100_valido() {
        List<ItemPonderacion> items = List.of(
                new ItemPonderacion("EXAMEN", null, 60.0, 1),
                new ItemPonderacion("TAREA", null, 40.0, 2));
        assertThatCode(() -> new CrearEsquemaUseCase.Command(
                "Esquema General", UUID.randomUUID(), null, LocalDate.now(), null,
                items, UUID.randomUUID(), "admin"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_crear_tresItems_suma100() {
        List<ItemPonderacion> items = List.of(
                new ItemPonderacion("EXAMEN", null, 40.0, 1),
                new ItemPonderacion("TAREA", null, 40.0, 2),
                new ItemPonderacion("PARTICIPACION", null, 20.0, 3));
        assertThatCode(() -> new CrearEsquemaUseCase.Command(
                "Esquema Trimestral", UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31),
                items, UUID.randomUUID(), "dir"))
                .doesNotThrowAnyException();
    }

    // ── ActualizarEsquemaUseCase.Command ──────────────────────────────────────

    @Test
    void command_actualizar_sinEsquemaId_lanzaExcepcion() {
        List<ItemPonderacion> items = List.of(new ItemPonderacion("EXAMEN", null, 100.0, 1));
        assertThatThrownBy(() -> new ActualizarEsquemaUseCase.Command(
                null, "Esquema", UUID.randomUUID(), null, LocalDate.now(), null,
                items, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("esquema_id");
    }

    @Test
    void command_actualizar_pesosNoSuman100_lanzaExcepcion() {
        List<ItemPonderacion> items = List.of(new ItemPonderacion("EXAMEN", null, 80.0, 1));
        assertThatThrownBy(() -> new ActualizarEsquemaUseCase.Command(
                UUID.randomUUID(), "Esquema", UUID.randomUUID(), null, LocalDate.now(), null,
                items, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100%");
    }

    @Test
    void command_actualizar_valido_noLanzaExcepcion() {
        List<ItemPonderacion> items = List.of(
                new ItemPonderacion("EXAMEN", null, 70.0, 1),
                new ItemPonderacion("PROYECTO", "Proyecto final", 30.0, 2));
        assertThatCode(() -> new ActualizarEsquemaUseCase.Command(
                UUID.randomUUID(), "Esquema Actualizado", UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, items, "coord"))
                .doesNotThrowAnyException();
    }
}

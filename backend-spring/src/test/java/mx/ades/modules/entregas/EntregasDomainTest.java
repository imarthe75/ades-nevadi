package mx.ades.modules.entregas;

import mx.ades.modules.entregas.domain.model.EstatusEntrega;
import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.RegistrarExcusaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class EntregasDomainTest {

    // ── EstatusEntrega ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.esCalificable={1}")
    @CsvSource({"ENTREGADA, true", "PENDIENTE, false", "CALIFICADA, false", "EXCUSA, false"})
    void estatusEntrega_esCalificable(EstatusEntrega estatus, boolean esperado) {
        assertEquals(esperado, estatus.esCalificable());
    }

    @ParameterizedTest(name = "{0}.esTerminal={1}")
    @CsvSource({"CALIFICADA, true", "EXCUSA, true", "PENDIENTE, false", "ENTREGADA, false"})
    void estatusEntrega_esTerminal(EstatusEntrega estatus, boolean esperado) {
        assertEquals(esperado, estatus.esTerminal());
    }

    @Test
    void estatusEntrega_of_caseInsensitive() {
        assertEquals(EstatusEntrega.ENTREGADA, EstatusEntrega.of("entregada"));
        assertEquals(EstatusEntrega.CALIFICADA, EstatusEntrega.of("CALIFICADA"));
    }

    @Test
    void estatusEntrega_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> EstatusEntrega.of("RECHAZADA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RECHAZADA");
    }

    // ── SubirEntregaUseCase.Command ───────────────────────────────────────────

    @Test
    void command_subir_sinTareaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new SubirEntregaUseCase.Command(null, UUID.randomUUID(), null, null, "alumno01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tarea_id");
    }

    @Test
    void command_subir_sinAlumnoId_lanzaExcepcion() {
        assertThatThrownBy(() -> new SubirEntregaUseCase.Command(UUID.randomUUID(), null, null, null, "alumno01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alumno_id");
    }

    @Test
    void command_subir_sinArchivo_valido() {
        assertThatCode(() -> new SubirEntregaUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), "Mi tarea terminada", null, "alumno01"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_subir_conArchivo_valido() {
        assertThatCode(() -> new SubirEntregaUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), null, "https://minio/tarea.pdf", "alumno01"))
                .doesNotThrowAnyException();
    }

    // ── CalificarEntregaUseCase.Command ───────────────────────────────────────

    @Test
    void command_calificar_sinEntregaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new CalificarEntregaUseCase.Command(null, 8.5, null, UUID.randomUUID(), "profe01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entrega_id");
    }

    @Test
    void command_calificar_calificacionFueraDeRango_lanzaExcepcion() {
        assertThatThrownBy(() -> new CalificarEntregaUseCase.Command(
                UUID.randomUUID(), 110.0, null, UUID.randomUUID(), "profe01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 y 10");
    }

    @Test
    void command_calificar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CalificarEntregaUseCase.Command(
                UUID.randomUUID(), 9.5, "Excelente trabajo", UUID.randomUUID(), "profe01"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_calificar_calificacionNula_valida() {
        assertThatCode(() -> new CalificarEntregaUseCase.Command(
                UUID.randomUUID(), null, "Sin calificación aún", UUID.randomUUID(), "profe01"))
                .doesNotThrowAnyException();
    }

    // ── RegistrarExcusaUseCase.Command ────────────────────────────────────────

    @Test
    void command_excusa_sinEntregaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarExcusaUseCase.Command(null, "Enfermedad", "profe01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entrega_id");
    }

    @Test
    void command_excusa_sinMotivo_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarExcusaUseCase.Command(UUID.randomUUID(), "", "profe01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void command_excusa_valido_noLanzaExcepcion() {
        assertThatCode(() -> new RegistrarExcusaUseCase.Command(
                UUID.randomUUID(), "Enfermedad justificada con constancia médica", "profe01"))
                .doesNotThrowAnyException();
    }
}

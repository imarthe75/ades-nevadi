package mx.ades.modules.justificaciones;

import mx.ades.modules.justificaciones.domain.model.AccionJustificacion;
import mx.ades.modules.justificaciones.domain.model.EstadoJustificacion;
import mx.ades.modules.justificaciones.domain.model.TipoJustificacion;
import mx.ades.modules.justificaciones.domain.port.in.RegistrarJustificacionUseCase;
import mx.ades.modules.justificaciones.domain.port.in.ResolverJustificacionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class JustificacionesDomainTest {

    // ── TipoJustificacion ─────────────────────────────────────────────────────

    @Test
    void tipoJustificacion_of_caseInsensitive() {
        assertEquals(TipoJustificacion.MEDICA, TipoJustificacion.of("medica"));
        assertEquals(TipoJustificacion.FAMILIAR, TipoJustificacion.of("FAMILIAR"));
        assertEquals(TipoJustificacion.DEPORTIVA, TipoJustificacion.of("Deportiva"));
    }

    @Test
    void tipoJustificacion_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoJustificacion.of("VACACIONES"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_justificacion inválido");
    }

    @Test
    void tipoJustificacion_of_null_retornaMedica() {
        assertEquals(TipoJustificacion.MEDICA, TipoJustificacion.of(null));
    }

    // ── EstadoJustificacion ───────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.permiteResolucion={1}")
    @CsvSource({"PENDIENTE, true", "APROBADA, false", "RECHAZADA, false"})
    void estadoJustificacion_permiteResolucion(EstadoJustificacion estado, boolean esperado) {
        assertEquals(esperado, estado.permiteResolucion());
    }

    @Test
    void estadoJustificacion_pendiente_esPendiente() {
        assertTrue(EstadoJustificacion.PENDIENTE.esPendiente());
        assertFalse(EstadoJustificacion.APROBADA.esPendiente());
    }

    // ── AccionJustificacion ───────────────────────────────────────────────────

    @Test
    void accion_aprobar_noRequiereMotivo() {
        assertFalse(AccionJustificacion.APROBAR.requiereMotivo());
    }

    @Test
    void accion_rechazar_requiereMotivo() {
        assertTrue(AccionJustificacion.RECHAZAR.requiereMotivo());
    }

    @ParameterizedTest(name = "{0}.estadoResultante={1}")
    @CsvSource({"APROBAR, APROBADA", "RECHAZAR, RECHAZADA"})
    void accion_estadoResultante(AccionJustificacion accion, EstadoJustificacion esperado) {
        assertEquals(esperado, accion.estadoResultante());
    }

    @Test
    void accion_of_caseInsensitive() {
        assertEquals(AccionJustificacion.APROBAR, AccionJustificacion.of("aprobar"));
        assertEquals(AccionJustificacion.RECHAZAR, AccionJustificacion.of("RECHAZAR"));
    }

    @Test
    void accion_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> AccionJustificacion.of("IGNORAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APROBAR o RECHAZAR");
    }

    @Test
    void accion_of_vacio_lanzaExcepcion() {
        assertThatThrownBy(() -> AccionJustificacion.of(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccionJustificacion.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── RegistrarJustificacionUseCase.Command ─────────────────────────────────

    @Test
    void command_registrar_sinAsistenciaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new RegistrarJustificacionUseCase.Command(
                null, TipoJustificacion.MEDICA, "motivo", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asistencia_id");
    }

    @Test
    void command_registrar_tipoNull_defaultsMedica() {
        var cmd = new RegistrarJustificacionUseCase.Command(
                UUID.randomUUID(), null, "dolor", null, UUID.randomUUID());
        assertEquals(TipoJustificacion.MEDICA, cmd.tipo());
    }

    // ── ResolverJustificacionUseCase.Command ──────────────────────────────────

    @Test
    void command_resolver_rechazar_sinMotivo_lanzaExcepcion() {
        assertThatThrownBy(() -> new ResolverJustificacionUseCase.Command(
                UUID.randomUUID(), AccionJustificacion.RECHAZAR, null,
                UUID.randomUUID(), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo_rechazo");
    }

    @Test
    void command_resolver_rechazar_motivoBlanco_lanzaExcepcion() {
        assertThatThrownBy(() -> new ResolverJustificacionUseCase.Command(
                UUID.randomUUID(), AccionJustificacion.RECHAZAR, "   ",
                UUID.randomUUID(), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo_rechazo");
    }

    @Test
    void command_resolver_aprobar_sinMotivo_esValido() {
        assertThatCode(() -> new ResolverJustificacionUseCase.Command(
                UUID.randomUUID(), AccionJustificacion.APROBAR, null,
                UUID.randomUUID(), 2))
                .doesNotThrowAnyException();
    }

    @Test
    void command_resolver_sinJustificacionId_lanzaExcepcion() {
        assertThatThrownBy(() -> new ResolverJustificacionUseCase.Command(
                null, AccionJustificacion.APROBAR, null, UUID.randomUUID(), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificacion_id");
    }
}

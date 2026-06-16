package mx.ades.modules.expediente_laboral;

import mx.ades.modules.expediente_laboral.domain.model.NivelEstudios;
import mx.ades.modules.expediente_laboral.domain.model.TipoContrato;
import mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.AgregarDocumentoLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpedienteLaboralDomainTest {

    // ── TipoContrato ──────────────────────────────────────────────────────────

    @Test
    void tipoContrato_of_caseInsensitive() {
        assertEquals(TipoContrato.INDEFINIDO, TipoContrato.of("indefinido"));
        assertEquals(TipoContrato.HONORARIOS, TipoContrato.of("HONORARIOS"));
        assertEquals(TipoContrato.COMISION, TipoContrato.of("comision"));
    }

    @Test
    void tipoContrato_of_nulo_retornaIndefinido() {
        assertEquals(TipoContrato.INDEFINIDO, TipoContrato.of(null));
        assertEquals(TipoContrato.INDEFINIDO, TipoContrato.of(""));
    }

    @Test
    void tipoContrato_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoContrato.of("TEMPORAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_contrato inválido");
    }

    // ── NivelEstudios ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.esPosgrado={1}")
    @CsvSource({"MAESTRIA, true", "DOCTORADO, true", "LICENCIATURA, false", "BACHILLERATO, false", "NORMAL_BASICA, false"})
    void nivelEstudios_esPosgrado(NivelEstudios nivel, boolean esperado) {
        assertEquals(esperado, nivel.esPosgrado());
    }

    @Test
    void nivelEstudios_of_nulo_retornaNulo() {
        assertNull(NivelEstudios.of(null));
        assertNull(NivelEstudios.of(""));
    }

    @Test
    void nivelEstudios_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> NivelEstudios.of("PRIMARIA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nivel_estudios inválido");
    }

    // ── CrearExpedienteLaboralUseCase.Command ─────────────────────────────────

    @Test
    void command_crear_sinPersonaId_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearExpedienteLaboralUseCase.Command(
                null, "INDEFINIDO", LocalDate.now(), null, 10000.0,
                null, null, null, null, null, null, null, null, null, null, "admin-id", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persona_id");
    }

    @Test
    void command_crear_sinPermiso_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearExpedienteLaboralUseCase.Command(
                UUID.randomUUID(), "INDEFINIDO", LocalDate.now(), null, 10000.0,
                null, null, null, null, null, null, null, null, null, null, "docente-id", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo RH");
    }

    @Test
    void command_crear_fechaFinAnterior_lanzaExcepcion() {
        LocalDate inicio = LocalDate.of(2026, 6, 1);
        LocalDate fin = LocalDate.of(2026, 5, 1);
        assertThatThrownBy(() -> new CrearExpedienteLaboralUseCase.Command(
                UUID.randomUUID(), "DETERMINADO", inicio, fin, 8000.0,
                null, null, null, null, null, null, null, null, null, null, "rh-id", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fecha_fin_contrato");
    }

    @Test
    void command_crear_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CrearExpedienteLaboralUseCase.Command(
                UUID.randomUUID(), "INDEFINIDO", LocalDate.of(2020, 1, 1), null,
                15000.0, "12345678901", null, null, null, null,
                "LICENCIATURA", "Educación", "UPN", null, null, "rh-id", 1))
                .doesNotThrowAnyException();
    }

    // ── ActualizarExpedienteLaboralUseCase.Command ────────────────────────────

    @Test
    void command_actualizar_sinId_lanzaExcepcion() {
        ActualizarExpedienteLaboralUseCase.Patch patch = new ActualizarExpedienteLaboralUseCase.Patch(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> new ActualizarExpedienteLaboralUseCase.Command(
                null, patch, "rh-id", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void command_actualizar_sinPermiso_lanzaExcepcion() {
        ActualizarExpedienteLaboralUseCase.Patch patch = new ActualizarExpedienteLaboralUseCase.Patch(
                "HONORARIOS", null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> new ActualizarExpedienteLaboralUseCase.Command(
                UUID.randomUUID(), patch, "docente-id", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo RH");
    }

    // ── AgregarDocumentoLaboralUseCase.Command ────────────────────────────────

    @Test
    void command_documento_tipoInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> new AgregarDocumentoLaboralUseCase.Command(
                UUID.randomUUID(), "expediente_fisico", "https://minio/doc.pdf", "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_documento inválido");
    }

    @Test
    void command_documento_valido_noLanzaExcepcion() {
        assertThatCode(() -> new AgregarDocumentoLaboralUseCase.Command(
                UUID.randomUUID(), "contrato", "https://minio/contrato.pdf", "rh-id"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_documento_tiposValidos_todos() {
        String[] tipos = {"contrato", "titulo", "cedula", "nss", "identificacion", "acta_nacimiento", "curp_doc", "imss", "otro"};
        for (String tipo : tipos) {
            assertThatCode(() -> new AgregarDocumentoLaboralUseCase.Command(UUID.randomUUID(), tipo, "url", "admin"))
                    .doesNotThrowAnyException();
        }
    }
}

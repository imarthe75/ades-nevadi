package mx.ades.modules.expediente;

import mx.ades.modules.expediente.domain.model.CalificacionExtra;
import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.model.TipoDocumentoExpediente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ExpedienteDomainTest {

    // ── TipoBaja ──────────────────────────────────────────────────────────────

    @Test
    void definitiva_desactiva_estudiante() {
        assertThat(TipoBaja.DEFINITIVA.desactivaEstudiante()).isTrue();
    }

    @Test
    void desercion_desactiva_estudiante() {
        assertThat(TipoBaja.DESERCION.desactivaEstudiante()).isTrue();
    }

    @Test
    void temporal_no_desactiva_estudiante() {
        assertThat(TipoBaja.TEMPORAL.desactivaEstudiante()).isFalse();
    }

    @Test
    void traslado_no_desactiva_estudiante() {
        assertThat(TipoBaja.TRASLADO.desactivaEstudiante()).isFalse();
    }

    @Test
    void tipoBaja_of_case_insensitive() {
        assertThat(TipoBaja.of("temporal")).isEqualTo(TipoBaja.TEMPORAL);
        assertThat(TipoBaja.of("DEFINITIVA")).isEqualTo(TipoBaja.DEFINITIVA);
    }

    @Test
    void tipoBaja_of_invalido_lanza_excepcion() {
        assertThatThrownBy(() -> TipoBaja.of("OTRO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_baja inválido");
    }

    // ── CalificacionExtra ─────────────────────────────────────────────────────

    @Test
    void calificacion_extra_valida_en_rango() {
        CalificacionExtra cal = CalificacionExtra.of(8.5);
        assertThat(cal.valor()).isEqualByComparingTo(new BigDecimal("8.50"));
    }

    @Test
    void calificacion_extra_minimo_valido() {
        CalificacionExtra cal = CalificacionExtra.of(0.0);
        assertThat(cal.valor()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void calificacion_extra_maximo_valido() {
        CalificacionExtra cal = CalificacionExtra.of(10.0);
        assertThat(cal.valor()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void calificacion_extra_negativa_lanza_excepcion() {
        assertThatThrownBy(() -> CalificacionExtra.of(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calificacion_extra_mayor_diez_lanza_excepcion() {
        assertThatThrownBy(() -> CalificacionExtra.of(10.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calificacion_extra_acredita_con_minimo_aprobatorio() {
        CalificacionExtra cal = CalificacionExtra.of(6.0);
        assertThat(cal.acredita(new BigDecimal("6"))).isTrue();
        assertThat(cal.acredita(new BigDecimal("7"))).isFalse();
    }

    // ── TipoDocumentoExpediente ───────────────────────────────────────────────

    @Test
    void validar_archivo_pdf_valido() {
        assertThatCode(() ->
            TipoDocumentoExpediente.validarArchivo("application/pdf", 1024 * 1024L)
        ).doesNotThrowAnyException();
    }

    @Test
    void validar_archivo_mime_invalido_lanza_excepcion() {
        assertThatThrownBy(() ->
            TipoDocumentoExpediente.validarArchivo("application/zip", 1024L)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("MIME no permitido");
    }

    @Test
    void validar_archivo_muy_grande_lanza_excepcion() {
        long masDeDosM = 3L * 1024 * 1024;
        assertThatThrownBy(() ->
            TipoDocumentoExpediente.validarArchivo("application/pdf", masDeDosM)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("2 MB");
    }

    @Test
    void requeridos_incluyen_curp_y_acta() {
        assertThat(TipoDocumentoExpediente.REQUERIDOS)
                .contains(TipoDocumentoExpediente.CURP, TipoDocumentoExpediente.ACTA_NACIMIENTO);
    }
}

package mx.ades.modules.badges;

import mx.ades.modules.badges.domain.model.CriterioTipo;
import mx.ades.modules.badges.domain.model.MetricaBadge;
import mx.ades.modules.badges.domain.model.TipoBadge;
import mx.ades.modules.badges.domain.port.in.AutoEvaluarBadgesUseCase;
import mx.ades.modules.badges.domain.port.in.CrearBadgeUseCase;
import mx.ades.modules.badges.domain.port.in.OtorgarBadgeUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class BadgesDomainTest {

    // ── TipoBadge ─────────────────────────────────────────────────────────────

    @Test
    void tipoBadge_of_caseInsensitive() {
        assertEquals(TipoBadge.ACADEMICO, TipoBadge.of("academico"));
        assertEquals(TipoBadge.ASISTENCIA, TipoBadge.of("ASISTENCIA"));
    }

    @Test
    void tipoBadge_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoBadge.of("OTRO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_badge inválido");
    }

    @Test
    void tipoBadge_ofNullable_null_retornaNull() {
        assertNull(TipoBadge.ofNullable(null));
        assertNull(TipoBadge.ofNullable(""));
    }

    // ── CriterioTipo ──────────────────────────────────────────────────────────

    @Test
    void criterioTipo_of_null_defaultsManual() {
        assertEquals(CriterioTipo.MANUAL, CriterioTipo.of(null));
        assertEquals(CriterioTipo.MANUAL, CriterioTipo.of(""));
    }

    @Test
    void criterioTipo_of_automatico() {
        assertEquals(CriterioTipo.AUTOMATICO, CriterioTipo.of("AUTOMATICO"));
        assertEquals(CriterioTipo.AUTOMATICO, CriterioTipo.of("automatico"));
    }

    @ParameterizedTest(name = "{0}.esAutomatico={1}")
    @CsvSource({"MANUAL, false", "AUTOMATICO, true"})
    void criterioTipo_esAutomatico(CriterioTipo tipo, boolean esperado) {
        assertEquals(esperado, tipo.esAutomatico());
    }

    // ── MetricaBadge ──────────────────────────────────────────────────────────

    @Test
    void metricaBadge_ofNullable_null_retornaNull() {
        assertNull(MetricaBadge.ofNullable(null));
        assertNull(MetricaBadge.ofNullable("  "));
    }

    @Test
    void metricaBadge_ofNullable_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> MetricaBadge.ofNullable("NOTAS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metrica_badge inválida");
    }

    @Test
    void metricaBadge_ofNullable_valido() {
        assertEquals(MetricaBadge.PCT_ASISTENCIA, MetricaBadge.ofNullable("pct_asistencia"));
        assertEquals(MetricaBadge.PROMEDIO_GENERAL, MetricaBadge.ofNullable("PROMEDIO_GENERAL"));
        assertEquals(MetricaBadge.SIN_REPORTES_CONDUCTA, MetricaBadge.ofNullable("SIN_REPORTES_CONDUCTA"));
    }

    // ── CrearBadgeUseCase.Command ──────────────────────────────────────────────

    @Test
    void command_crear_sinNombre_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearBadgeUseCase.Command(
                null, "desc", "pi-star", "#D02030",
                TipoBadge.ACADEMICO, CriterioTipo.MANUAL, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void command_crear_sinTipo_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearBadgeUseCase.Command(
                "Badge Puntualidad", "desc", "pi-star", "#D02030",
                null, CriterioTipo.MANUAL, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_badge");
    }

    @Test
    void command_crear_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CrearBadgeUseCase.Command(
                "Badge Asistencia Perfecta", null, "pi-check", "#00AA00",
                TipoBadge.ASISTENCIA, CriterioTipo.AUTOMATICO,
                "pct_asistencia", BigDecimal.valueOf(95.0), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    // ── OtorgarBadgeUseCase.Command ────────────────────────────────────────────

    @Test
    void command_otorgar_sinBadgeId_lanzaExcepcion() {
        assertThatThrownBy(() -> new OtorgarBadgeUseCase.Command(
                null, UUID.randomUUID(), UUID.randomUUID(), "por mérito", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("badge_id");
    }

    @Test
    void command_otorgar_sinEstudianteId_lanzaExcepcion() {
        assertThatThrownBy(() -> new OtorgarBadgeUseCase.Command(
                UUID.randomUUID(), null, UUID.randomUUID(), "por mérito", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estudiante_id");
    }

    // ── AutoEvaluarBadgesUseCase.Result ────────────────────────────────────────

    @Test
    void autoEvaluar_result_valoresCorrectos() {
        var result = new AutoEvaluarBadgesUseCase.Result(5, 42);
        assertEquals(5, result.badgesEvaluados());
        assertEquals(42, result.totalOtorgados());
    }
}

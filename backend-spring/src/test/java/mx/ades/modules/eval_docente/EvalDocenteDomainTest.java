package mx.ades.modules.eval_docente;

import mx.ades.modules.eval_docente.domain.model.EstadoEvaluacion;
import mx.ades.modules.eval_docente.domain.model.TipoEvaluador;
import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.EnviarEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class EvalDocenteDomainTest {

    // ── TipoEvaluador ─────────────────────────────────────────────────────────

    @Test
    void tipoEvaluador_of_caseInsensitive() {
        assertEquals(TipoEvaluador.AUTOEVALUACION, TipoEvaluador.of("autoevaluacion"));
        assertEquals(TipoEvaluador.DIRECTIVO, TipoEvaluador.of("DIRECTIVO"));
        assertEquals(TipoEvaluador.ALUMNO, TipoEvaluador.of("alumno"));
        assertEquals(TipoEvaluador.PARES, TipoEvaluador.of("pares"));
    }

    @Test
    void tipoEvaluador_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoEvaluador.of("EXTERNO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_evaluador inválido");
    }

    @Test
    void tipoEvaluador_of_nulo_lanzaExcepcion() {
        assertThatThrownBy(() -> TipoEvaluador.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_evaluador es requerido");
    }

    // ── EstadoEvaluacion ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.esEditable={1}")
    @CsvSource({"BORRADOR, true", "ENVIADA, false", "APROBADA, false"})
    void estadoEvaluacion_esEditable(EstadoEvaluacion estado, boolean esperado) {
        assertEquals(esperado, estado.esEditable());
    }

    @ParameterizedTest(name = "{0}.esAprobada={1}")
    @CsvSource({"BORRADOR, false", "ENVIADA, false", "APROBADA, true"})
    void estadoEvaluacion_esAprobada(EstadoEvaluacion estado, boolean esperado) {
        assertEquals(esperado, estado.esAprobada());
    }

    @Test
    void estadoEvaluacion_of_invalido_lanzaExcepcion() {
        assertThatThrownBy(() -> EstadoEvaluacion.of("CANCELADA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── CrearEvaluacionUseCase.Command ────────────────────────────────────────

    @Test
    void command_crear_sinProfesorId_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearEvaluacionUseCase.Command(
                null, UUID.randomUUID(), UUID.randomUUID(), "DIRECTIVO", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profesor_id");
    }

    @Test
    void command_crear_sinCicloId_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearEvaluacionUseCase.Command(
                UUID.randomUUID(), null, UUID.randomUUID(), "DIRECTIVO", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ciclo_escolar_id");
    }

    @Test
    void command_crear_sinTipoEvaluador_lanzaExcepcion() {
        assertThatThrownBy(() -> new CrearEvaluacionUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo_evaluador");
    }

    @Test
    void command_crear_valido_noLanzaExcepcion() {
        assertThatCode(() -> new CrearEvaluacionUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "AUTOEVALUACION", "Muy buen desempeño", "profe01"))
                .doesNotThrowAnyException();
    }

    // ── GuardarCriteriosUseCase.Command ───────────────────────────────────────

    @Test
    void command_guardar_sinEvalId_lanzaExcepcion() {
        List<GuardarCriteriosUseCase.CriterioCalificacion> criterios = List.of(
                new GuardarCriteriosUseCase.CriterioCalificacion(UUID.randomUUID(), 8, null));
        assertThatThrownBy(() -> new GuardarCriteriosUseCase.Command(null, criterios))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eval_id");
    }

    @Test
    void command_guardar_sinCriterios_lanzaExcepcion() {
        assertThatThrownBy(() -> new GuardarCriteriosUseCase.Command(UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterios");
    }

    @Test
    void criterioCalificacion_sinCriterioId_lanzaExcepcion() {
        assertThatThrownBy(() -> new GuardarCriteriosUseCase.CriterioCalificacion(null, 9, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterio_id");
    }

    // ── EnviarEvaluacionUseCase.Command ───────────────────────────────────────

    @Test
    void command_enviar_sinEvalId_lanzaExcepcion() {
        assertThatThrownBy(() -> new EnviarEvaluacionUseCase.Command(null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eval_id");
    }

    @Test
    void command_enviar_valido_noLanzaExcepcion() {
        assertThatCode(() -> new EnviarEvaluacionUseCase.Command(UUID.randomUUID(), "profe01"))
                .doesNotThrowAnyException();
    }
}

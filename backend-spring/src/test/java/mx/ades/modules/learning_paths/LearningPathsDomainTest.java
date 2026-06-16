package mx.ades.modules.learning_paths;

import mx.ades.modules.learning_paths.application.service.LearningPathApplicationService;
import mx.ades.modules.learning_paths.domain.model.EstatusAsignacion;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningPathsDomainTest {

    // ── EstatusAsignacion ─────────────────────────────────────────────────────

    @Test
    void estatus_completado_cuando_todos_obligatorios_completados() {
        assertThat(EstatusAsignacion.PENDIENTE.transicion(5, 5)).isEqualTo(EstatusAsignacion.COMPLETADO);
        assertThat(EstatusAsignacion.PENDIENTE.transicion(6, 5)).isEqualTo(EstatusAsignacion.COMPLETADO);
    }

    @Test
    void estatus_en_progreso_cuando_hay_completados_pero_no_todos() {
        assertThat(EstatusAsignacion.PENDIENTE.transicion(3, 5)).isEqualTo(EstatusAsignacion.EN_PROGRESO);
    }

    @Test
    void estatus_pendiente_cuando_ningun_completado() {
        assertThat(EstatusAsignacion.PENDIENTE.transicion(0, 5)).isEqualTo(EstatusAsignacion.PENDIENTE);
    }

    @Test
    void estatus_pendiente_cuando_sin_obligatorios() {
        assertThat(EstatusAsignacion.PENDIENTE.transicion(0, 0)).isEqualTo(EstatusAsignacion.PENDIENTE);
    }

    @Test
    void completado_es_estado_final() {
        assertThat(EstatusAsignacion.COMPLETADO.esFinal()).isTrue();
        assertThat(EstatusAsignacion.EN_PROGRESO.esFinal()).isFalse();
        assertThat(EstatusAsignacion.PENDIENTE.esFinal()).isFalse();
    }

    @Test
    void of_parsea_case_insensitive() {
        assertThat(EstatusAsignacion.of("completado")).isEqualTo(EstatusAsignacion.COMPLETADO);
        assertThat(EstatusAsignacion.of("EN_PROGRESO")).isEqualTo(EstatusAsignacion.EN_PROGRESO);
        assertThat(EstatusAsignacion.of(null)).isEqualTo(EstatusAsignacion.PENDIENTE);
        assertThat(EstatusAsignacion.of("INVALIDO")).isEqualTo(EstatusAsignacion.PENDIENTE);
    }

    // ── RegistrarProgresoUseCase.Command ──────────────────────────────────────

    @Test
    void command_sin_asignacion_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RegistrarProgresoUseCase.Command(null, UUID.randomUUID(), 30, 8.5));
    }

    @Test
    void command_sin_recurso_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RegistrarProgresoUseCase.Command(UUID.randomUUID(), null, 30, 8.5));
    }

    // ── LearningPathApplicationService ───────────────────────────────────────

    LearningPathRepositoryPort repo;
    LearningPathApplicationService service;
    UUID asigId   = UUID.randomUUID();
    UUID recursoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo    = mock(LearningPathRepositoryPort.class);
        service = new LearningPathApplicationService(repo);
    }

    @Test
    void progreso_transiciona_a_completado_cuando_obligatorios_completos() {
        when(repo.calcularProgreso(asigId)).thenReturn(
                new LearningPathRepositoryPort.ProgresoStats(5, 5, 100.0));

        var result = service.ejecutar(new RegistrarProgresoUseCase.Command(asigId, recursoId, 45, 9.0));

        assertThat(result.estatus()).isEqualTo("COMPLETADO");
        assertThat(result.pctCompletado()).isEqualTo(100.0);
        verify(repo).actualizarEstatus(asigId, "COMPLETADO", 100.0);
    }

    @Test
    void progreso_transiciona_a_en_progreso_cuando_parcial() {
        when(repo.calcularProgreso(asigId)).thenReturn(
                new LearningPathRepositoryPort.ProgresoStats(2, 5, 40.0));

        var result = service.ejecutar(new RegistrarProgresoUseCase.Command(asigId, recursoId, 20, null));

        assertThat(result.estatus()).isEqualTo("EN_PROGRESO");
        assertThat(result.pctCompletado()).isEqualTo(40.0);
        verify(repo).upsertProgreso(asigId, recursoId, 20, null);
    }
}

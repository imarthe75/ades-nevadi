package mx.ades.modules.evaluaciones;

import mx.ades.modules.evaluaciones.application.service.EvaluacionApplicationService;
import mx.ades.modules.evaluaciones.domain.model.SlotHorario;
import mx.ades.modules.evaluaciones.domain.port.in.AsignarAulaHoraUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.AsignacionAulaRepositoryPort;
import mx.ades.modules.evaluaciones.domain.port.out.CalificacionEvaluacionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluacionApplicationServiceTest {

    @Mock AsignacionAulaRepositoryPort aulaRepo;
    @Mock CalificacionEvaluacionRepositoryPort calificacionRepo;
    @Mock ApplicationEventPublisher events;

    EvaluacionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluacionApplicationService(aulaRepo, calificacionRepo, events);
    }

    // ── AsignarAulaHora ───────────────────────────────────────────────────────

    @Test
    void asignar_aula_sin_conflicto_persiste_y_publica_evento() {
        UUID aulaId = UUID.randomUUID();
        UUID asignacionId = UUID.randomUUID();
        SlotHorario slot = SlotHorario.of("10:00", "11:00");

        when(aulaRepo.existeConflicto(any(), any(), any())).thenReturn(false);
        when(aulaRepo.guardar(any(), any(), any(), any(), any(), any())).thenReturn(asignacionId);

        UUID result = service.ejecutar(new AsignarAulaHoraUseCase.Command(
                UUID.randomUUID(), aulaId, LocalDate.now(), slot, "Examen final", "prof1"));

        assertThat(result).isEqualTo(asignacionId);
        verify(aulaRepo).guardar(any(), eq(aulaId), any(), eq(slot), eq("Examen final"), eq("prof1"));
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void asignar_aula_con_conflicto_lanza_409() {
        when(aulaRepo.existeConflicto(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.ejecutar(new AsignarAulaHoraUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                SlotHorario.of("08:00", "09:00"), null, "prof1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ocupada");
    }

    // ── CalificarEvaluacionMasivo ─────────────────────────────────────────────

    @Test
    void calificar_masivo_inserta_nuevas_y_actualiza_existentes() {
        UUID evalId = UUID.randomUUID();
        UUID est1 = UUID.randomUUID();
        UUID est2 = UUID.randomUUID();
        UUID calId2 = UUID.randomUUID();

        when(calificacionRepo.findIdActiva(evalId, est1)).thenReturn(Optional.empty());
        when(calificacionRepo.findIdActiva(evalId, est2)).thenReturn(Optional.of(calId2));

        List<CalificarEvaluacionMasivoUseCase.EntradaCalificacion> entradas = List.of(
                new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(est1, 8.5, "Bien"),
                new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(est2, 7.0, null));

        int updated = service.ejecutar(
                new CalificarEvaluacionMasivoUseCase.Command(evalId, entradas, "profesor1"));

        assertThat(updated).isEqualTo(2);
        verify(calificacionRepo).insertar(eq(evalId), eq(est1), eq(8.5), eq("Bien"), eq("profesor1"));
        verify(calificacionRepo).actualizar(eq(calId2), eq(7.0), isNull(), eq("profesor1"));
    }

    @Test
    void calificar_masivo_retorna_cero_si_lista_vacia() {
        assertThatThrownBy(() ->
            new CalificarEvaluacionMasivoUseCase.Command(UUID.randomUUID(), List.of(), "prof")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

package mx.ades.modules.expediente;

import mx.ades.modules.expediente.application.service.ExpedienteApplicationService;
import mx.ades.modules.expediente.domain.event.BajaRegistradaEvent;
import mx.ades.modules.expediente.domain.model.CalificacionExtra;
import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.port.in.*;
import mx.ades.modules.expediente.domain.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpedienteApplicationServiceTest {

    @Mock BajaRepositoryPort bajaRepo;
    @Mock ExtraordinarioRepositoryPort extraRepo;
    @Mock ConstanciaRepositoryPort constanciaRepo;
    @Mock ExpedienteRepositoryPort expedienteRepo;
    @Mock ApplicationEventPublisher events;

    ExpedienteApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExpedienteApplicationService(bajaRepo, extraRepo, constanciaRepo, expedienteRepo, events);
    }

    // ── RegistrarBaja ─────────────────────────────────────────────────────────

    @Test
    void baja_definitiva_desactiva_y_publica_evento() {
        UUID estudianteId = UUID.randomUUID();
        UUID bajaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(bajaRepo.guardar(eq(estudianteId), eq(TipoBaja.DEFINITIVA),
                any(), any(), any(), any(), any(), any(), eq(userId)))
                .thenReturn(bajaId);

        RegistrarBajaUseCase.Command cmd = new RegistrarBajaUseCase.Command(
                estudianteId, TipoBaja.DEFINITIVA, "Cambio de escuela",
                LocalDate.now(), null, null, null, null, userId, "admin");

        RegistrarBajaUseCase.Result result = service.ejecutar(cmd);

        assertThat(result.bajaId()).isEqualTo(bajaId);
        assertThat(result.estudianteDesactivado()).isTrue();
        verify(bajaRepo).desactivarEstudiante(estudianteId);

        ArgumentCaptor<BajaRegistradaEvent> cap = ArgumentCaptor.forClass(BajaRegistradaEvent.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().tipo()).isEqualTo(TipoBaja.DEFINITIVA);
        assertThat(cap.getValue().estudianteDesactivado()).isTrue();
    }

    @Test
    void baja_temporal_no_desactiva_estudiante() {
        UUID estudianteId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(bajaRepo.guardar(any(), eq(TipoBaja.TEMPORAL), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());

        RegistrarBajaUseCase.Result result = service.ejecutar(
                new RegistrarBajaUseCase.Command(estudianteId, TipoBaja.TEMPORAL,
                        "Enfermedad", LocalDate.now(), LocalDate.now().plusMonths(3),
                        null, null, null, userId, "admin"));

        assertThat(result.estudianteDesactivado()).isFalse();
        verify(bajaRepo, never()).desactivarEstudiante(any());
    }

    // ── CalificarExtraordinario ───────────────────────────────────────────────

    @Test
    void calificar_extraordinario_activo_persiste_y_publica_evento() {
        UUID extraId = UUID.randomUUID();
        CalificacionExtra cal = CalificacionExtra.of(7.5);

        when(extraRepo.existeActivo(extraId)).thenReturn(true);

        service.ejecutar(new CalificarExtraordinarioUseCase.Command(
                extraId, cal, true, LocalDate.now(), "profesor1"));

        verify(extraRepo).calificar(eq(extraId), eq(cal.valor()), eq(true), any());
        verify(events).publishEvent(any());
    }

    @Test
    void calificar_extraordinario_inexistente_lanza_404() {
        UUID extraId = UUID.randomUUID();
        when(extraRepo.existeActivo(extraId)).thenReturn(false);

        assertThatThrownBy(() -> service.ejecutar(new CalificarExtraordinarioUseCase.Command(
                extraId, CalificacionExtra.of(5.0), false, null, "prof")))
                .hasMessageContaining("404");
    }

    // ── EmitirConstancia ──────────────────────────────────────────────────────

    @Test
    void emitir_constancia_genera_folio_y_persiste() {
        UUID estudianteId = UUID.randomUUID();
        UUID constanciaId = UUID.randomUUID();

        when(constanciaRepo.generarFolio("INSCRIPCION")).thenReturn("INS-2026-0001");
        when(constanciaRepo.guardar(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(constanciaId);

        EmitirConstanciaUseCase.Result result = service.ejecutar(
                new EmitirConstanciaUseCase.Command(
                        estudianteId, "INSCRIPCION", UUID.randomUUID(),
                        "Padre", "Trámite migratorio", null, null,
                        UUID.randomUUID(), "secretaria"));

        assertThat(result.constanciaId()).isEqualTo(constanciaId);
        assertThat(result.folio()).isEqualTo("INS-2026-0001");
    }

    // ── VerificarExpediente ───────────────────────────────────────────────────

    @Test
    void verificar_expediente_nivel_acceso_valido_llama_repo() {
        UUID expId = UUID.randomUUID();
        UUID directorId = UUID.randomUUID();

        service.ejecutar(new VerificarExpedienteUseCase.Command(expId, "Completo", 1, directorId));

        verify(expedienteRepo).marcarVerificado(expId, "Completo", directorId);
    }

    @Test
    void verificar_expediente_nivel_acceso_insuficiente_lanza_excepcion() {
        assertThatThrownBy(() ->
            new VerificarExpedienteUseCase.Command(UUID.randomUUID(), null, 3, UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

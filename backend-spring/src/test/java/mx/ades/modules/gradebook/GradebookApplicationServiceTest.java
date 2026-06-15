package mx.ades.modules.gradebook;

import mx.ades.modules.gradebook.application.service.GradebookApplicationService;
import mx.ades.modules.gradebook.domain.event.AjusteAplicadoEvent;
import mx.ades.modules.gradebook.domain.event.CalificacionCerradaEvent;
import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.model.CalificacionEstado;
import mx.ades.modules.gradebook.domain.port.in.AplicarAjusteUseCase;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import mx.ades.modules.gradebook.domain.port.out.CalificacionPeriodoRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradebookApplicationServiceTest {

    @Mock CalificacionPeriodoRepositoryPort repository;
    @Mock ApplicationEventPublisher events;

    GradebookApplicationService service;

    private final UUID calPeriodoId = UUID.randomUUID();
    private final AjusteManual ajusteValido =
            new AjusteManual(new BigDecimal("1.5"), "Corrección aprobada por coordinación académica");

    @BeforeEach
    void setUp() {
        service = new GradebookApplicationService(repository, events);
    }

    // ── AplicarAjuste ─────────────────────────────────────────────────────────

    @Test
    void aplicarAjuste_calificacionAbierta_actualizaYPublicaEvento() {
        when(repository.findEstado(calPeriodoId)).thenReturn(Optional.of(
                new CalificacionEstado(calPeriodoId, new BigDecimal("8.0"), false)));

        AplicarAjusteUseCase.Result result = service.ejecutar(
                new AplicarAjusteUseCase.Command(calPeriodoId, ajusteValido, "docente@nevadi", false));

        assertEquals(new BigDecimal("9.50"), result.calificacionFinal());
        verify(repository).aplicarAjuste(eq(calPeriodoId), eq(ajusteValido), eq(new BigDecimal("9.50")), eq("docente@nevadi"));

        ArgumentCaptor<AjusteAplicadoEvent> cap = ArgumentCaptor.forClass(AjusteAplicadoEvent.class);
        verify(events).publishEvent(cap.capture());
        assertEquals(calPeriodoId, cap.getValue().calPeriodoId());
        assertEquals(new BigDecimal("9.50"), cap.getValue().calificacionFinal());
    }

    @Test
    void aplicarAjuste_noEncontrada_lanzaNotFound() {
        when(repository.findEstado(calPeriodoId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(
                        new AplicarAjusteUseCase.Command(calPeriodoId, ajusteValido, "user", false)));

        assertEquals(404, ex.getStatusCode().value());
        verify(repository, never()).aplicarAjuste(any(), any(), any(), any());
    }

    @Test
    void aplicarAjuste_cerradaSinAdmin_lanzaForbidden() {
        when(repository.findEstado(calPeriodoId)).thenReturn(Optional.of(
                new CalificacionEstado(calPeriodoId, new BigDecimal("7.0"), true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(
                        new AplicarAjusteUseCase.Command(calPeriodoId, ajusteValido, "docente@nevadi", false)));

        assertEquals(403, ex.getStatusCode().value());
        verify(repository, never()).aplicarAjuste(any(), any(), any(), any());
    }

    @Test
    void aplicarAjuste_cerradaConAdmin_debePermitir() {
        when(repository.findEstado(calPeriodoId)).thenReturn(Optional.of(
                new CalificacionEstado(calPeriodoId, new BigDecimal("7.0"), true)));

        AplicarAjusteUseCase.Result result = service.ejecutar(
                new AplicarAjusteUseCase.Command(calPeriodoId, ajusteValido, "admin@nevadi", true));

        assertEquals(new BigDecimal("8.50"), result.calificacionFinal());
        verify(events).publishEvent(any(AjusteAplicadoEvent.class));
    }

    // ── CerrarCalificacion ────────────────────────────────────────────────────

    @Test
    void cerrarCalificacion_sinPermiso_lanzaForbidden() {
        var cmd = new CerrarCalificacionUseCase.Command(
                calPeriodoId, "docente@nevadi", Set.of("DOCENTE"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(cmd));

        assertEquals(403, ex.getStatusCode().value());
        verify(repository, never()).cerrar(any(), any());
    }

    @Test
    void cerrarCalificacion_conPermiso_cierraYPublicaEvento() {
        when(repository.cerrar(calPeriodoId, "director@nevadi")).thenReturn(true);
        var cmd = new CerrarCalificacionUseCase.Command(
                calPeriodoId, "director@nevadi", Set.of("DIRECTOR"));

        assertDoesNotThrow(() -> service.ejecutar(cmd));

        verify(repository).cerrar(calPeriodoId, "director@nevadi");
        ArgumentCaptor<CalificacionCerradaEvent> cap = ArgumentCaptor.forClass(CalificacionCerradaEvent.class);
        verify(events).publishEvent(cap.capture());
        assertEquals("director@nevadi", cap.getValue().cerradoPor());
    }

    @Test
    void cerrarCalificacion_yaCerrada_lanzaBadRequest() {
        when(repository.cerrar(calPeriodoId, "admin@nevadi")).thenReturn(false);
        var cmd = new CerrarCalificacionUseCase.Command(
                calPeriodoId, "admin@nevadi", Set.of("ADMIN_GLOBAL"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(cmd));

        assertEquals(400, ex.getStatusCode().value());
        verify(events, never()).publishEvent(any());
    }
}

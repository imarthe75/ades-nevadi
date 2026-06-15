package mx.ades.modules.calificaciones;

import mx.ades.modules.calificaciones.application.service.CalificacionApplicationService;
import mx.ades.modules.calificaciones.domain.event.CalificacionCerradaEvent;
import mx.ades.modules.calificaciones.domain.model.Calificacion;
import mx.ades.modules.calificaciones.domain.port.out.CalificacionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalificacionApplicationServiceTest {

    @Mock CalificacionRepositoryPort repository;
    @Mock ApplicationEventPublisher events;

    CalificacionApplicationService service;

    private final UUID estudianteId = UUID.randomUUID();
    private final UUID materiaId    = UUID.randomUUID();
    private final UUID periodoId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CalificacionApplicationService(repository, events);
    }

    @Test
    void guardarManual_aprobado_debePublicarEventoAPROBADO() {
        Calificacion entrada = new Calificacion(null, estudianteId, null, materiaId, periodoId,
                new BigDecimal("9.0"), true, null);
        Calificacion guardada = new Calificacion(UUID.randomUUID(), estudianteId, null, materiaId,
                periodoId, new BigDecimal("9.0"), true, null);
        when(repository.guardar(entrada)).thenReturn(guardada);

        service.ejecutar(entrada);

        ArgumentCaptor<CalificacionCerradaEvent> captor =
                ArgumentCaptor.forClass(CalificacionCerradaEvent.class);
        verify(events).publishEvent(captor.capture());
        assertFalse(captor.getValue().esReprobado());
    }

    @Test
    void guardarManual_reprobado_debePublicarEventoREPROBADO() {
        Calificacion entrada = new Calificacion(null, estudianteId, null, materiaId, periodoId,
                new BigDecimal("4.5"), false, null);
        Calificacion guardada = new Calificacion(UUID.randomUUID(), estudianteId, null, materiaId,
                periodoId, new BigDecimal("4.5"), false, null);
        when(repository.guardar(entrada)).thenReturn(guardada);

        service.ejecutar(entrada);

        ArgumentCaptor<CalificacionCerradaEvent> captor =
                ArgumentCaptor.forClass(CalificacionCerradaEvent.class);
        verify(events).publishEvent(captor.capture());
        assertTrue(captor.getValue().esReprobado());
    }

    @Test
    void obtenerBoleta_debeDelegarAlRepositorio() {
        when(repository.findByEstudianteId(estudianteId)).thenReturn(List.of());
        List<Calificacion> resultado = service.ejecutar(estudianteId);
        verify(repository).findByEstudianteId(estudianteId);
        assertNotNull(resultado);
    }
}

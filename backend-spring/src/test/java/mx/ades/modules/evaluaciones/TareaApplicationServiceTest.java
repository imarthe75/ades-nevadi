package mx.ades.modules.evaluaciones;

import mx.ades.modules.evaluaciones.application.service.TareaApplicationService;
import mx.ades.modules.evaluaciones.domain.event.ActividadCreadaEvent;
import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.model.TipoItem;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CrearActividadUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.TareaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TareaApplicationServiceTest {

    @Mock TareaRepositoryPort repository;
    @Mock ApplicationEventPublisher events;

    TareaApplicationService service;

    private final UUID grupoId    = UUID.randomUUID();
    private final UUID materiaId  = UUID.randomUUID();
    private final UUID tareaId    = UUID.randomUUID();
    private final UUID calificadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TareaApplicationService(repository, events);
    }

    // ── CrearActividad ────────────────────────────────────────────────────────

    @Test
    void crearActividad_conEstudiantes_debeCrearSlotsYPublicarEvento() {
        List<UUID> estudiantes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(repository.guardar(any())).thenReturn(tareaId);
        when(repository.findEstudiantesEnGrupo(grupoId)).thenReturn(estudiantes);
        when(repository.crearSlots(tareaId, estudiantes)).thenReturn(3);

        CrearActividadUseCase.Command cmd = new CrearActividadUseCase.Command(
                "Tarea 1", null, grupoId, materiaId, null, null,
                LocalDate.now(), LocalDate.now().plusDays(7),
                BigDecimal.TEN, TipoItem.TAREA, false, null, "profesor@nevadi.edu.mx");

        CrearActividadUseCase.Result result = service.ejecutar(cmd);

        assertEquals(tareaId, result.tareaId());
        assertEquals(3, result.slotsCreados());

        ArgumentCaptor<ActividadCreadaEvent> captor = ArgumentCaptor.forClass(ActividadCreadaEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals(tareaId, captor.getValue().tareaId());
        assertEquals(3, captor.getValue().slotsCreados());
        assertEquals(TipoItem.TAREA, captor.getValue().tipoItem());
    }

    @Test
    void crearActividad_sinEstudiantesEnGrupo_debeRetornarCeroSlots() {
        when(repository.guardar(any())).thenReturn(tareaId);
        when(repository.findEstudiantesEnGrupo(grupoId)).thenReturn(List.of());
        when(repository.crearSlots(tareaId, List.of())).thenReturn(0);

        CrearActividadUseCase.Command cmd = new CrearActividadUseCase.Command(
                "Examen", null, grupoId, materiaId, null, null,
                LocalDate.now(), LocalDate.now().plusDays(1),
                BigDecimal.TEN, TipoItem.EXAMEN, false, null, "prof@nevadi.edu.mx");

        CrearActividadUseCase.Result result = service.ejecutar(cmd);

        assertEquals(0, result.slotsCreados());
        verify(events).publishEvent(any(ActividadCreadaEvent.class));
    }

    // ── CalificarMasivo ───────────────────────────────────────────────────────

    @Test
    void calificarMasivo_calificacionesValidas_debeActualizar() {
        when(repository.findPuntajeMaximo(tareaId)).thenReturn(Optional.of(new BigDecimal("10")));
        List<ItemCalificacion> items = List.of(
                new ItemCalificacion(UUID.randomUUID(), new BigDecimal("8"), "ok"),
                new ItemCalificacion(UUID.randomUUID(), new BigDecimal("9.5"), "bien"));
        when(repository.calificarItems(eq(tareaId), eq(items), eq(calificadorId))).thenReturn(2);

        int actualizados = service.ejecutar(
                new CalificarMasivoUseCase.Command(tareaId, items, calificadorId));

        assertEquals(2, actualizados);
    }

    @Test
    void calificarMasivo_calificacionExcedePuntajeMaximo_debeLanzarExcepcion() {
        when(repository.findPuntajeMaximo(tareaId)).thenReturn(Optional.of(new BigDecimal("10")));
        List<ItemCalificacion> items = List.of(
                new ItemCalificacion(UUID.randomUUID(), new BigDecimal("11"), "trampa"));

        assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(
                        new CalificarMasivoUseCase.Command(tareaId, items, calificadorId)));

        verify(repository, never()).calificarItems(any(), any(), any());
    }

    @Test
    void calificarMasivo_tareaNoEncontrada_debeLanzarNotFound() {
        when(repository.findPuntajeMaximo(tareaId)).thenReturn(Optional.empty());
        List<ItemCalificacion> items = List.of(
                new ItemCalificacion(UUID.randomUUID(), BigDecimal.TEN, null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.ejecutar(
                        new CalificarMasivoUseCase.Command(tareaId, items, calificadorId)));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void calificarMasivoCommand_listaVacia_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new CalificarMasivoUseCase.Command(tareaId, List.of(), calificadorId));
    }
}

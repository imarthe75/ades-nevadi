package mx.ades.modules.asistencias;

import mx.ades.modules.asistencias.application.service.RegistrarAsistenciaMasivaService;
import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaCommand;
import mx.ades.modules.asistencias.domain.port.out.AsistenciaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrarAsistenciaMasivaServiceTest {

    @Mock
    AsistenciaRepositoryPort repository;

    RegistrarAsistenciaMasivaService service;

    @BeforeEach
    void setUp() {
        service = new RegistrarAsistenciaMasivaService(repository);
    }

    @Test
    void ejecutar_debeGuardarAsistenciasConvertidas() {
        UUID claseId      = UUID.randomUUID();
        UUID estudianteId = UUID.randomUUID();
        var comandos = List.of(
                new RegistrarAsistenciaCommand(claseId, estudianteId, EstatusAsistencia.PRESENTE, null)
        );

        service.ejecutar(comandos, "profesor@nevadi.edu.mx");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Asistencia>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).guardarMasivo(captor.capture());

        List<Asistencia> guardadas = captor.getValue();
        assertEquals(1, guardadas.size());
        assertEquals(claseId,           guardadas.get(0).claseId());
        assertEquals(estudianteId,      guardadas.get(0).estudianteId());
        assertEquals(EstatusAsistencia.PRESENTE, guardadas.get(0).estatus());
        assertNotNull(guardadas.get(0).id());
    }

    @Test
    void ejecutar_conListaVacia_noDebeInvocarRepositorio() {
        service.ejecutar(List.of(), "sistema");
        verifyNoInteractions(repository);
    }

    @Test
    void ejecutar_conMultiplesItems_debePasarTodosAlRepositorio() {
        UUID claseId = UUID.randomUUID();
        var comandos = List.of(
                new RegistrarAsistenciaCommand(claseId, UUID.randomUUID(), EstatusAsistencia.PRESENTE, null),
                new RegistrarAsistenciaCommand(claseId, UUID.randomUUID(), EstatusAsistencia.AUSENTE, null),
                new RegistrarAsistenciaCommand(claseId, UUID.randomUUID(), EstatusAsistencia.TARDE, "llegó tarde")
        );

        service.ejecutar(comandos, "profesor@nevadi.edu.mx");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Asistencia>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).guardarMasivo(captor.capture());
        assertEquals(3, captor.getValue().size());
    }

    @Test
    void consultarPorClase_debeDelegarAlRepositorio() {
        UUID claseId = UUID.randomUUID();
        when(repository.findByClaseId(claseId)).thenReturn(List.of());

        List<Asistencia> resultado = service.ejecutar(claseId);

        verify(repository).findByClaseId(claseId);
        assertNotNull(resultado);
    }
}

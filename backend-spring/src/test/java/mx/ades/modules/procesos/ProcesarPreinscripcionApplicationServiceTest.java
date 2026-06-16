package mx.ades.modules.procesos;

import mx.ades.modules.procesos.application.service.ProcesosApplicationService;
import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcesarPreinscripcionApplicationServiceTest {

    PreinscripcionRepositoryPort repo;
    ProcesosApplicationService service;

    UUID admisionId = UUID.randomUUID();
    UUID cicloId    = UUID.randomUUID();
    UUID grupoId    = UUID.randomUUID();
    UUID estudianteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo    = mock(PreinscripcionRepositoryPort.class);
        service = new ProcesosApplicationService(repo);
    }

    @Test
    void preinscripcion_exitosa_cuando_solicitud_aceptada_y_grupo_con_cupo() {
        var admision = new PreinscripcionRepositoryPort.AdmisionData("Juan", "García", "GARJ010101HDFRCN01");
        var cap      = new PreinscripcionRepositoryPort.GrupoCapacidad(30, 10);
        var expected = new ProcesarPreinscripcionUseCase.PreinscripcionResult(
                estudianteId, "MAT-123456", "Juan", "García", "GARJ010101HDFRCN01");

        when(repo.findAdmisionAceptada(admisionId)).thenReturn(Optional.of(admision));
        when(repo.findCapacidadGrupo(grupoId)).thenReturn(Optional.of(cap));
        when(repo.guardar(any(), any())).thenReturn(expected);

        var cmd    = new ProcesarPreinscripcionUseCase.Command(admisionId, cicloId, grupoId, "secretaria");
        var result = service.ejecutar(cmd);

        assertThat(result.estudianteId()).isEqualTo(estudianteId);
        assertThat(result.matricula()).isEqualTo("MAT-123456");
        verify(repo).guardar(cmd, admision);
    }

    @Test
    void lanza_400_cuando_solicitud_no_esta_aceptada() {
        when(repo.findAdmisionAceptada(admisionId)).thenReturn(Optional.empty());

        var cmd = new ProcesarPreinscripcionUseCase.Command(admisionId, cicloId, grupoId, "secretaria");

        assertThatThrownBy(() -> service.ejecutar(cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repo, never()).guardar(any(), any());
    }

    @Test
    void lanza_404_cuando_grupo_no_existe() {
        var admision = new PreinscripcionRepositoryPort.AdmisionData("Ana", "López", "LOPA010101HDFRCN01");
        when(repo.findAdmisionAceptada(admisionId)).thenReturn(Optional.of(admision));
        when(repo.findCapacidadGrupo(grupoId)).thenReturn(Optional.empty());

        var cmd = new ProcesarPreinscripcionUseCase.Command(admisionId, cicloId, grupoId, "secretaria");

        assertThatThrownBy(() -> service.ejecutar(cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(repo, never()).guardar(any(), any());
    }

    @Test
    void lanza_400_cuando_grupo_esta_lleno() {
        var admision = new PreinscripcionRepositoryPort.AdmisionData("Pedro", "Ruiz", "RUIP010101HDFRCN01");
        var cap      = new PreinscripcionRepositoryPort.GrupoCapacidad(30, 30);

        when(repo.findAdmisionAceptada(admisionId)).thenReturn(Optional.of(admision));
        when(repo.findCapacidadGrupo(grupoId)).thenReturn(Optional.of(cap));

        var cmd = new ProcesarPreinscripcionUseCase.Command(admisionId, cicloId, grupoId, "secretaria");

        assertThatThrownBy(() -> service.ejecutar(cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ResponseStatusException) ex).getReason()).contains("lleno");
                });

        verify(repo, never()).guardar(any(), any());
    }
}

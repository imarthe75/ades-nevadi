package mx.ades.modules.conducta;

import mx.ades.modules.conducta.application.service.ConductaApplicationService;
import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.domain.port.out.PlanMejoraRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConductaApplicationServiceTest {

    @Mock PlanMejoraRepositoryPort planRepo;

    ConductaApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ConductaApplicationService(planRepo);
    }

    @Test
    void crear_plan_exitoso_cuando_no_existe_activo() {
        UUID reporteId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        when(planRepo.existeActivo(reporteId)).thenReturn(false);
        when(planRepo.guardar(any())).thenReturn(planId);

        UUID result = service.ejecutar(new CrearPlanMejoraUseCase.Command(
                reporteId, UUID.randomUUID(), null, UUID.randomUUID(),
                "Mejorar rendimiento y asistencia", null, null, null, null, "docente1"));

        assertThat(result).isEqualTo(planId);
        verify(planRepo).guardar(any());
    }

    @Test
    void crear_plan_lanza_409_cuando_ya_existe_activo() {
        UUID reporteId = UUID.randomUUID();
        when(planRepo.existeActivo(reporteId)).thenReturn(true);

        assertThatThrownBy(() -> service.ejecutar(new CrearPlanMejoraUseCase.Command(
                reporteId, UUID.randomUUID(), null, UUID.randomUUID(),
                "Segundo plan para mismo reporte", null, null, null, null, "docente1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya tiene un plan de mejora activo");

        verify(planRepo, never()).guardar(any());
    }

    @Test
    void no_llama_repo_guardar_si_falla_validacion_command() {
        assertThatThrownBy(() -> service.ejecutar(new CrearPlanMejoraUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "", null, null, null, null, "docente1")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(planRepo);
    }
}

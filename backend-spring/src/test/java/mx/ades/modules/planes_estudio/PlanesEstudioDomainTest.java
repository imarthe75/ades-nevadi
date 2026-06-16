package mx.ades.modules.planes_estudio;

import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PlanesEstudioDomainTest {

    @Test
    void command_sinMateriaId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new AsignarMateriaUseCase.Command(null, UUID.randomUUID(), UUID.randomUUID(), 5.0, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("materia_id");
    }

    @Test
    void command_sinGradoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new AsignarMateriaUseCase.Command(UUID.randomUUID(), null, UUID.randomUUID(), 5.0, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("grado_id");
    }

    @Test
    void command_sinCicloId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new AsignarMateriaUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), null, 5.0, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ciclo_escolar_id");
    }

    @Test
    void command_horasNulas_defaultCero() {
        AsignarMateriaUseCase.Command cmd = new AsignarMateriaUseCase.Command(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        assertThat(cmd.horasSemana()).isEqualTo(0.0);
        assertThat(cmd.esObligatoria()).isTrue();
    }

    @Test
    void command_valido_noLanzaExcepcion() {
        assertThatCode(() ->
            new AsignarMateriaUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4.5, false))
            .doesNotThrowAnyException();
    }
}

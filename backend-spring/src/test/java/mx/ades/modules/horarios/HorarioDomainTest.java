package mx.ades.modules.horarios;

import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class HorarioDomainTest {

    // ── CrearHorarioUseCase.Command ───────────────────────────────────────────

    @Test
    void crear_sinGrupoId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(null, UUID.randomUUID(), null, null, null, 1, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("grupo_id");
    }

    @Test
    void crear_diaSemanaInvalido_negativo_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), -1, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dia_semana");
    }

    @Test
    void crear_diaSemanaInvalido_7_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), 7, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dia_semana");
    }

    @Test
    void crear_sinMateriaId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(UUID.randomUUID(), null, UUID.randomUUID(), null,
                UUID.randomUUID(), 1, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("materia_id");
    }

    @Test
    void crear_sinProfesorId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), null, null,
                UUID.randomUUID(), 1, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("profesor_id");
    }

    @Test
    void crear_sinCicloEscolarId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new CrearHorarioUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                null, 1, "08:00", "09:00", null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ciclo_escolar_id");
    }

    @Test
    void crear_valido_origenNull_defaultMANUAL() {
        CrearHorarioUseCase.Command cmd = new CrearHorarioUseCase.Command(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            UUID.randomUUID(), 1, "08:00", "09:00", null, "user");
        assertThat(cmd.origen()).isEqualTo("MANUAL");
    }

    @Test
    void crear_valido_diaSemana1a5_noLanzaExcepcion() {
        for (int dia = 1; dia <= 5; dia++) {
            final int d = dia;
            assertThatCode(() ->
                new CrearHorarioUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                    UUID.randomUUID(), d, "08:00", "09:00", "MANUAL", "user"))
                .doesNotThrowAnyException();
        }
    }

    // ── ActualizarHorarioUseCase.Command ─────────────────────────────────────

    @Test
    void actualizar_sinHorarioId_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new ActualizarHorarioUseCase.Command(null, null, null, null, null, null, null, null, null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("horario_id");
    }

    @Test
    void actualizar_diaSemanaInvalido_lanzaExcepcion() {
        assertThatThrownBy(() ->
            new ActualizarHorarioUseCase.Command(UUID.randomUUID(), null, null, null, 8, null, null, null, null, "user"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dia_semana");
    }

    @Test
    void actualizar_valido_camposNulos_noLanzaExcepcion() {
        assertThatCode(() ->
            new ActualizarHorarioUseCase.Command(UUID.randomUUID(), null, null, null, null, null, null, null, null, "user"))
            .doesNotThrowAnyException();
    }
}

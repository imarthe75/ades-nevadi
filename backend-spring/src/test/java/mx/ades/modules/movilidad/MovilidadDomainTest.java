package mx.ades.modules.movilidad;

import mx.ades.modules.movilidad.application.service.MovilidadApplicationService;
import mx.ades.modules.movilidad.domain.model.TipoMovilidad;
import mx.ades.modules.movilidad.domain.port.in.RegistrarBajaUseCase;
import mx.ades.modules.movilidad.domain.port.in.RegistrarCambioGrupoUseCase;
import mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovilidadDomainTest {

    // ── TipoMovilidad ─────────────────────────────────────────────────────────

    @Test
    void traslado_y_baja_definitiva_requieren_nivel_2_director() {
        assertThat(TipoMovilidad.TRASLADO.nivelAccesoMinimo()).isEqualTo(2);
        assertThat(TipoMovilidad.BAJA_DEFINITIVA.nivelAccesoMinimo()).isEqualTo(2);
    }

    @Test
    void cambio_grupo_y_baja_temporal_permiten_nivel_3_coordinador() {
        assertThat(TipoMovilidad.CAMBIO_GRUPO.permitePara(3)).isTrue();
        assertThat(TipoMovilidad.BAJA_TEMPORAL.permitePara(3)).isTrue();
        assertThat(TipoMovilidad.TRASLADO.permitePara(3)).isFalse();
    }

    @Test
    void solo_baja_temporal_y_definitiva_desactivan_estudiante() {
        assertThat(TipoMovilidad.BAJA_TEMPORAL.desactivaEstudiante()).isTrue();
        assertThat(TipoMovilidad.BAJA_DEFINITIVA.desactivaEstudiante()).isTrue();
        assertThat(TipoMovilidad.CAMBIO_GRUPO.desactivaEstudiante()).isFalse();
        assertThat(TipoMovilidad.TRASLADO.desactivaEstudiante()).isFalse();
    }

    @Test
    void traslado_y_bajas_generan_registro_baja() {
        assertThat(TipoMovilidad.TRASLADO.generaRegistroBaja()).isTrue();
        assertThat(TipoMovilidad.BAJA_TEMPORAL.generaRegistroBaja()).isTrue();
        assertThat(TipoMovilidad.BAJA_DEFINITIVA.generaRegistroBaja()).isTrue();
        assertThat(TipoMovilidad.CAMBIO_GRUPO.generaRegistroBaja()).isFalse();
    }

    @Test
    void cambio_grupo_mantiene_periodo_escolar() {
        assertThat(TipoMovilidad.CAMBIO_GRUPO.mantienePeriodo()).isTrue();
        assertThat(TipoMovilidad.TRASLADO.mantienePeriodo()).isFalse();
    }

    @Test
    void tipo_baja_db_retorna_valor_correcto() {
        assertThat(TipoMovilidad.BAJA_TEMPORAL.tipoBajaDb()).isEqualTo("TEMPORAL");
        assertThat(TipoMovilidad.BAJA_DEFINITIVA.tipoBajaDb()).isEqualTo("DEFINITIVA");
        assertThat(TipoMovilidad.TRASLADO.tipoBajaDb()).isEqualTo("TRASLADO");
    }

    @Test
    void tipo_baja_db_lanza_excepcion_para_cambio_grupo() {
        assertThatThrownBy(() -> TipoMovilidad.CAMBIO_GRUPO.tipoBajaDb())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Command validation ────────────────────────────────────────────────────

    @Test
    void cambio_grupo_command_sin_estudiante_id_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RegistrarCambioGrupoUseCase.Command(null, UUID.randomUUID(), "motivo", null, null, "user"));
    }

    @Test
    void cambio_grupo_command_sin_motivo_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RegistrarCambioGrupoUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), "", null, null, "user"));
    }

    @Test
    void baja_command_con_tipo_cambio_grupo_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RegistrarBajaUseCase.Command(UUID.randomUUID(), TipoMovilidad.CAMBIO_GRUPO,
                        "motivo", null, null, null, null, null, null, null, "user"));
    }

    // ── MovilidadApplicationService ───────────────────────────────────────────

    MovilidadRepositoryPort repo;
    MovilidadApplicationService service;
    UUID estId  = UUID.randomUUID();
    UUID grupoO = UUID.randomUUID();
    UUID grupoD = UUID.randomUUID();
    UUID inscId = UUID.randomUUID();
    UUID cambioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo    = mock(MovilidadRepositoryPort.class);
        service = new MovilidadApplicationService(repo);
        when(repo.findInscripcionActiva(estId)).thenReturn(Optional.of(
                new MovilidadRepositoryPort.InscripcionActiva(inscId, grupoO, UUID.randomUUID(),
                        "1A", UUID.randomUUID(), 35, 20, true)));
        when(repo.findGrupo(grupoD)).thenReturn(Optional.of(
                new MovilidadRepositoryPort.GrupoInfo(grupoD, "2B", UUID.randomUUID(), 35, 20)));
        when(repo.guardarCambioGrupo(any(), any(), any(), any(), any(), any())).thenReturn(cambioId);
    }

    @Test
    void cambio_grupo_exitoso_actualiza_inscripcion() {
        var result = service.ejecutar(new RegistrarCambioGrupoUseCase.Command(
                estId, grupoD, "Solicitud de alumno", null, null, "admin"));

        assertThat(result.grupoNuevo()).isEqualTo("2B");
        assertThat(result.grupoAnterior()).isEqualTo("1A");
        assertThat(result.cambioId()).isEqualTo(cambioId);
        verify(repo).actualizarGrupoInscripcion(inscId, grupoD, "admin");
    }

    @Test
    void cambio_grupo_falla_si_mismo_grupo() {
        when(repo.findInscripcionActiva(estId)).thenReturn(Optional.of(
                new MovilidadRepositoryPort.InscripcionActiva(inscId, grupoD, UUID.randomUUID(),
                        "2B", UUID.randomUUID(), 35, 20, true)));

        assertThatThrownBy(() -> service.ejecutar(new RegistrarCambioGrupoUseCase.Command(
                estId, grupoD, "motivo", null, null, "admin")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cambio_grupo_falla_si_grupo_lleno() {
        when(repo.findGrupo(grupoD)).thenReturn(Optional.of(
                new MovilidadRepositoryPort.GrupoInfo(grupoD, "2B", UUID.randomUUID(), 30, 30)));

        assertThatThrownBy(() -> service.ejecutar(new RegistrarCambioGrupoUseCase.Command(
                estId, grupoD, "motivo", null, null, "admin")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("lleno");
    }

    @Test
    void baja_temporal_desactiva_estudiante_y_guarda_baja() {
        var cmd = new RegistrarBajaUseCase.Command(
                estId, TipoMovilidad.BAJA_TEMPORAL, "Enfermedad",
                null, null, null, null, null, null, null, "admin");

        var result = service.ejecutar(cmd);

        assertThat(result.tipo()).isEqualTo(TipoMovilidad.BAJA_TEMPORAL);
        verify(repo).desactivarInscripcion(inscId, "admin");
        verify(repo).desactivarEstudiante(estId, "admin");
        verify(repo).guardarBaja(eq(estId), eq(inscId), eq("TEMPORAL"), any(), any(), any(), any(), any(), any(), any());
    }
}

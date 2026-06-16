package mx.ades.modules.encuestas;

import mx.ades.modules.encuestas.application.service.EncuestaApplicationService;
import mx.ades.modules.encuestas.domain.model.TipoRespuesta;
import mx.ades.modules.encuestas.domain.port.in.ResponderEncuestaUseCase;
import mx.ades.modules.encuestas.domain.port.out.EncuestaRespuestaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncuestasDomainTest {

    // ── TipoRespuesta ─────────────────────────────────────────────────────────

    @Test
    void tipo_respuesta_escala_es_escala() {
        assertThat(TipoRespuesta.ESCALA_5.esEscala()).isTrue();
        assertThat(TipoRespuesta.OPCION_MULTIPLE.esEscala()).isFalse();
        assertThat(TipoRespuesta.TEXTO_LIBRE.esTextoLibre()).isTrue();
    }

    @Test
    void tipo_respuesta_escala_requiere_valor_numerico() {
        assertThat(TipoRespuesta.ESCALA_5.requiereValorNumerico()).isTrue();
        assertThat(TipoRespuesta.BOOLEANO.requiereValorNumerico()).isFalse();
    }

    @Test
    void tipo_respuesta_of_parsea_case_insensitive() {
        assertThat(TipoRespuesta.of("escala_5")).isEqualTo(TipoRespuesta.ESCALA_5);
        assertThat(TipoRespuesta.of("BOOLEANO")).isEqualTo(TipoRespuesta.BOOLEANO);
        assertThat(TipoRespuesta.of(null)).isEqualTo(TipoRespuesta.TEXTO_LIBRE);
        assertThat(TipoRespuesta.of("DESCONOCIDO")).isEqualTo(TipoRespuesta.TEXTO_LIBRE);
    }

    // ── ResponderEncuestaUseCase.Command ──────────────────────────────────────

    @Test
    void command_sin_encuesta_id_lanza_excepcion() {
        UUID sesion = UUID.randomUUID();
        var item = new ResponderEncuestaUseCase.RespuestaItem(UUID.randomUUID(), null, 4.0, null);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResponderEncuestaUseCase.Command(null, sesion.toString(), List.of(item), UUID.randomUUID()));
    }

    @Test
    void command_sin_respuestas_lanza_excepcion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResponderEncuestaUseCase.Command(UUID.randomUUID(), "ses-001", List.of(), UUID.randomUUID()));
    }

    // ── EncuestaApplicationService ────────────────────────────────────────────

    EncuestaRespuestaRepositoryPort repo;
    EncuestaApplicationService service;
    UUID encuestaId = UUID.randomUUID();
    UUID preguntaId = UUID.randomUUID();
    UUID usuarioId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(EncuestaRespuestaRepositoryPort.class);
        service = new EncuestaApplicationService(repo);
    }

    @Test
    void responder_exitoso_cuando_encuesta_activa() {
        when(repo.findEstado(encuestaId)).thenReturn(
                new EncuestaRespuestaRepositoryPort.EncuestaEstado(
                        encuestaId, "Satisfacción", true, false, null));
        when(repo.existeRespuesta(preguntaId, "ses-1")).thenReturn(false);

        var cmd = new ResponderEncuestaUseCase.Command(encuestaId, "ses-1",
                List.of(new ResponderEncuestaUseCase.RespuestaItem(preguntaId, null, 5.0, null)), usuarioId);

        ResponderEncuestaUseCase.Result result = service.ejecutar(cmd);

        assertThat(result.guardadas()).isEqualTo(1);
        assertThat(result.sesionId()).isEqualTo("ses-1");
        verify(repo).guardarRespuesta(any());
    }

    @Test
    void responder_falla_cuando_encuesta_inactiva() {
        when(repo.findEstado(encuestaId)).thenReturn(
                new EncuestaRespuestaRepositoryPort.EncuestaEstado(
                        encuestaId, "Test", false, false, null));

        var cmd = new ResponderEncuestaUseCase.Command(encuestaId, "ses-2",
                List.of(new ResponderEncuestaUseCase.RespuestaItem(preguntaId, null, 3.0, null)), usuarioId);

        assertThatThrownBy(() -> service.ejecutar(cmd))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repo, never()).guardarRespuesta(any());
    }

    @Test
    void responder_omite_respuesta_duplicada() {
        when(repo.findEstado(encuestaId)).thenReturn(
                new EncuestaRespuestaRepositoryPort.EncuestaEstado(
                        encuestaId, "Test", true, false, null));
        when(repo.existeRespuesta(preguntaId, "ses-dup")).thenReturn(true);

        var cmd = new ResponderEncuestaUseCase.Command(encuestaId, "ses-dup",
                List.of(new ResponderEncuestaUseCase.RespuestaItem(preguntaId, null, 2.0, null)), usuarioId);

        var result = service.ejecutar(cmd);
        assertThat(result.guardadas()).isZero();
        verify(repo, never()).guardarRespuesta(any());
    }

    @Test
    void responder_crea_alerta_cuando_texto_contiene_acoso() {
        UUID plantelId = UUID.randomUUID();
        when(repo.findEstado(encuestaId)).thenReturn(
                new EncuestaRespuestaRepositoryPort.EncuestaEstado(
                        encuestaId, "Bienestar", true, false, plantelId));
        when(repo.existeRespuesta(preguntaId, "ses-flag")).thenReturn(false);
        when(repo.findEstudianteIdPorUsuario(usuarioId)).thenReturn(null);

        var cmd = new ResponderEncuestaUseCase.Command(encuestaId, "ses-flag",
                List.of(new ResponderEncuestaUseCase.RespuestaItem(
                        preguntaId, "hay acoso en el salón", null, null)), usuarioId);

        service.ejecutar(cmd);
        verify(repo).crearAlertaBullying(isNull(), eq(plantelId), anyString(), anyString(), anyString());
    }
}

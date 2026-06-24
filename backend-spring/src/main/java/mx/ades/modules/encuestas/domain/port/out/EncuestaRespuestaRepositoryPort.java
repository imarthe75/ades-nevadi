package mx.ades.modules.encuestas.domain.port.out;

import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para respuestas de encuestas.
 * <p>Cubre {@code ades_encuesta_respuestas} y permite crear alertas de bullying
 * en {@code ades_alertas_cumplimiento} cuando se detecta contenido sensible.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EncuestaRespuestaRepositoryPort {

    record EncuestaEstado(UUID encuestaId, String titulo, boolean activa, boolean anonima, UUID plantelId) {}

    record RespuestaData(UUID encuestaId, UUID preguntaId, UUID usuarioId, String sesionId,
                         String textoRespuesta, Double valorNumerico, String opcionSeleccionada) {}

    EncuestaEstado findEstado(UUID encuestaId);

    boolean existeRespuesta(UUID preguntaId, String sesionId);

    void guardarRespuesta(RespuestaData data);

    void crearAlertaBullying(UUID estudianteId, UUID plantelId, String texto, String encuestaTitulo, String sesionId);

    UUID findEstudianteIdPorUsuario(UUID usuarioId);
}

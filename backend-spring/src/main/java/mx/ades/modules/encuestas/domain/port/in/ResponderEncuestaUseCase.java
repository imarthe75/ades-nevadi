package mx.ades.modules.encuestas.domain.port.in;

import java.util.List;
import java.util.UUID;

public interface ResponderEncuestaUseCase {

    record RespuestaItem(UUID preguntaId, String textoRespuesta, Double valorNumerico, String opcionSeleccionada) {
        public RespuestaItem {
            if (preguntaId == null) throw new IllegalArgumentException("pregunta_id es obligatorio");
        }
    }

    record Command(UUID encuestaId, String sesionId, List<RespuestaItem> respuestas, UUID usuarioId) {
        public Command {
            if (encuestaId == null) throw new IllegalArgumentException("encuesta_id es obligatorio");
            if (respuestas == null || respuestas.isEmpty())
                throw new IllegalArgumentException("Se requiere al menos una respuesta");
            if (sesionId == null || sesionId.isBlank())
                throw new IllegalArgumentException("sesion_id es obligatorio");
        }
    }

    record Result(String sesionId, int guardadas) {}

    Result ejecutar(Command command);
}

package mx.ades.modules.foros.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface PublicarAnuncioUseCase {

    record Command(
            String titulo,
            String contenido,
            UUID plantelId,
            String nivelEducativo,
            String fechaInicio,
            String fechaFin,
            Boolean esUrgente,
            Integer nivelAcceso
    ) {
        public Command {
            if (titulo == null || titulo.isBlank())
                throw new IllegalArgumentException("El título del anuncio es requerido");
            if (nivelAcceso != null && nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere Coordinador o superior");
        }
    }

    Map<String, Object> publicar(Command cmd);
}

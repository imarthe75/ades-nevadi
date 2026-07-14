package mx.ades.modules.evaluaciones.domain.port.in;

import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;

import java.util.List;
import java.util.UUID;

public interface CalificarMasivoUseCase {

    record Command(UUID tareaId, List<ItemCalificacion> items, UUID calificadorId) {
        public Command {
            // IllegalArgumentException (no NullPointerException): es la excepción que el
            // GlobalExceptionHandler traduce a 400 limpio; ver ItemCalificacion para el detalle.
            if (tareaId == null)     throw new IllegalArgumentException("tareaId requerido");
            if (calificadorId == null) throw new IllegalArgumentException("calificadorId requerido");
            if (items == null || items.isEmpty()) throw new IllegalArgumentException("items no puede estar vacío");
        }
    }

    /** Returns total number of updated rows. */
    int ejecutar(Command command);
}

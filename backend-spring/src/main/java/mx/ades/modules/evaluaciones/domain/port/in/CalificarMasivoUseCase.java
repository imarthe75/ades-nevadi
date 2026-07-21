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

    /**
     * H-3 (auditoría 2026-07-20, decisión de negocio confirmada 2026-07-21): calificar
     * una entrega en PENDIENTE (nunca subida por el alumno) está permitido — un docente
     * puede necesitar registrar un 0 explícito por no entrega — pero debe quedar
     * distinguible de una entrega real revisada. {@code sinEntrega} lista los
     * estudianteId que estaban en PENDIENTE antes de esta llamada, para que el
     * frontend lo muestre como confirmación explícita en vez de un efecto silencioso.
     */
    record Result(int actualizados, List<UUID> sinEntrega) {}

    Result ejecutar(Command command);
}

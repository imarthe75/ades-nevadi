package mx.ades.modules.biblioteca.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarPrestamoUseCase {

    record Command(
            UUID libroId,
            UUID personaId,
            UUID plantelId,
            LocalDate fechaDevolucionEsperada,
            String observaciones,
            int nivelAcceso
    ) {
        public Command {
            if (libroId == null)
                throw new IllegalArgumentException("libro_id es requerido");
            if (personaId == null)
                throw new IllegalArgumentException("persona_id es requerido");
            if (fechaDevolucionEsperada == null)
                throw new IllegalArgumentException("fecha_devolucion_esperada es requerida");
        }
    }

    UUID prestar(Command cmd);
}

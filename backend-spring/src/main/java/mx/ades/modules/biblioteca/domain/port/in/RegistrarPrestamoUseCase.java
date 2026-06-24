package mx.ades.modules.biblioteca.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para registrar un préstamo de libro
 * en el dominio de biblioteca.
 *
 * <p>Decrementa atómicamente el contador de ejemplares disponibles en la misma
 * transacción. Falla si no hay ejemplares disponibles.</p>
 *
 * @author ADES
 * @since 2026
 */
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

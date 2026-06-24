package mx.ades.modules.biblioteca.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para actualizar los datos bibliográficos
 * de un libro existente en el dominio de biblioteca.
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarLibroUseCase {

    record Command(
            UUID libroId,
            String titulo,
            String autor,
            String isbn,
            String editorial,
            Integer anioPublicacion,
            String categoria,
            String ubicacion,
            Integer ejemplaresTotal,
            int nivelAcceso
    ) {
        public Command {
            if (libroId == null)
                throw new IllegalArgumentException("libro_id es requerido");
        }
    }

    void actualizar(Command cmd);
}

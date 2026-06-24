package mx.ades.modules.biblioteca.domain.port.in;

import mx.ades.modules.biblioteca.domain.model.CategoriaLibro;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para registrar un nuevo libro en el acervo
 * de la biblioteca en el dominio de biblioteca.
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarLibroUseCase {

    record Command(
            String titulo,
            String autor,
            String isbn,
            String editorial,
            Integer anioPublicacion,
            CategoriaLibro categoria,
            String ubicacion,
            UUID plantelId,
            int ejemplaresTotal,
            int nivelAcceso
    ) {
        public Command {
            if (titulo == null || titulo.isBlank())
                throw new IllegalArgumentException("titulo es requerido");
            if (categoria == null)
                throw new IllegalArgumentException("categoria es requerida");
            if (ejemplaresTotal < 0)
                throw new IllegalArgumentException("ejemplares_total no puede ser negativo");
        }
    }

    UUID registrar(Command cmd);
}

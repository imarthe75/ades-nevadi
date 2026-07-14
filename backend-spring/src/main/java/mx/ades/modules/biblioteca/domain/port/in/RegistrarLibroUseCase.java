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
            // CHECK ades_biblioteca_libros: anio_publicacion NULL o entre 1400 y 2200.
            // El frontend ya limita el input a ese rango (p-inputNumber min/max), pero
            // el backend no lo validaba — un cliente que llame la API directo podía
            // disparar la violación del CHECK a nivel BD (hallazgo de auditoría).
            if (anioPublicacion != null && (anioPublicacion < 1400 || anioPublicacion > 2200))
                throw new IllegalArgumentException("anio_publicacion debe estar entre 1400 y 2200");
        }
    }

    UUID registrar(Command cmd);
}

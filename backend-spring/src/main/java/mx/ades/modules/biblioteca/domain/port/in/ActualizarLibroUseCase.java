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
            // CHECK ades_biblioteca_libros: anio_publicacion NULL o entre 1400 y 2200
            // (mismo hallazgo que en RegistrarLibroUseCase — faltaba en actualización).
            if (anioPublicacion != null && (anioPublicacion < 1400 || anioPublicacion > 2200))
                throw new IllegalArgumentException("anio_publicacion debe estar entre 1400 y 2200");
        }
    }

    void actualizar(Command cmd);
}

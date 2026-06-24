package mx.ades.modules.biblioteca.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para dar de baja (soft delete) un libro
 * del acervo de la biblioteca en el dominio de biblioteca.
 *
 * <p>Requiere {@code nivelAcceso <= 3} (Coordinador o superior).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EliminarLibroUseCase {

    void eliminar(UUID libroId, int nivelAcceso);
}

package mx.ades.modules.biblioteca.domain.port.out;

import mx.ades.modules.biblioteca.BibliotecaLibro;
import mx.ades.modules.biblioteca.BibliotecaPrestamo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BibliotecaRepositoryPort {

    // ── Acervo ───────────────────────────────────────────────────────────────
    BibliotecaLibro saveLibro(BibliotecaLibro libro);

    Optional<BibliotecaLibro> findLibroActivo(UUID id);

    List<Map<String, Object>> listLibros(String texto, String categoria, UUID plantelId, boolean soloDisponibles);

    // ── Circulación ──────────────────────────────────────────────────────────
    BibliotecaPrestamo savePrestamo(BibliotecaPrestamo prestamo);

    Optional<BibliotecaPrestamo> findPrestamoAbierto(UUID id);

    List<Map<String, Object>> listPrestamos(UUID personaId, UUID libroId, String estatus, UUID plantelId);

    /**
     * Toma un ejemplar de forma atómica (decremento condicional).
     * @return true si había disponibilidad y se decrementó; false si no.
     */
    boolean tomarEjemplar(UUID libroId);

    /** Regresa un ejemplar al acervo (incremento acotado a ejemplares_total). */
    void devolverEjemplar(UUID libroId);
}

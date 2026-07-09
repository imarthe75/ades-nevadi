package mx.ades.modules.biblioteca;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface BibliotecaPrestamoRepository extends JpaRepository<BibliotecaPrestamo, UUID> {

    @EntityGraph(attributePaths = {"libro", "estudiante", "estudiante.persona"})
    Optional<BibliotecaPrestamo> findById(UUID id);

    @EntityGraph(attributePaths = {"libro", "estudiante", "estudiante.persona"})
    @Query("SELECT p FROM BibliotecaPrestamo p WHERE p.estudiante.id = :estudianteId")
    List<BibliotecaPrestamo> findByEstudianteId(@Param("estudianteId") UUID estudianteId);

    @EntityGraph(attributePaths = {"libro", "estudiante", "estudiante.persona"})
    @Query("SELECT p FROM BibliotecaPrestamo p WHERE p.libro.id = :libroId AND p.fechaDevolucion IS NULL")
    List<BibliotecaPrestamo> findLibrosPendientes(@Param("libroId") UUID libroId);
}

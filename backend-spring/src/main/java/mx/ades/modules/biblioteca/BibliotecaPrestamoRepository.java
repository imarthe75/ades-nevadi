package mx.ades.modules.biblioteca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * BibliotecaPrestamo no mapea relaciones JPA — solo FKs planas
 * (libro_id, persona_id), por lo que no aplica @EntityGraph.
 */
@Repository
public interface BibliotecaPrestamoRepository extends JpaRepository<BibliotecaPrestamo, UUID> {

    @Query("SELECT p FROM BibliotecaPrestamo p WHERE p.personaId = :estudianteId")
    List<BibliotecaPrestamo> findByEstudianteId(@Param("estudianteId") UUID estudianteId);

    @Query("SELECT p FROM BibliotecaPrestamo p WHERE p.libroId = :libroId AND p.fechaDevolucionReal IS NULL")
    List<BibliotecaPrestamo> findLibrosPendientes(@Param("libroId") UUID libroId);
}

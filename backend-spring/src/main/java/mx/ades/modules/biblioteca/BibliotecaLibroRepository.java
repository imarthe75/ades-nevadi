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
public interface BibliotecaLibroRepository extends JpaRepository<BibliotecaLibro, UUID> {

    @EntityGraph(attributePaths = {"categoria", "autor"})
    Optional<BibliotecaLibro> findById(UUID id);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("SELECT l FROM BibliotecaLibro l WHERE l.isActive = true ORDER BY l.titulo")
    List<BibliotecaLibro> findAllActive();

    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("SELECT l FROM BibliotecaLibro l WHERE l.categoria.id = :categoriaId AND l.isActive = true")
    List<BibliotecaLibro> findByCategoriaId(@Param("categoriaId") UUID categoriaId);
}

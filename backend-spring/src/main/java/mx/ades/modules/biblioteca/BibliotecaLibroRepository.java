package mx.ades.modules.biblioteca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * BibliotecaLibro no mapea relaciones JPA — categoria y autor son columnas
 * de texto planas, no aplica @EntityGraph.
 */
@Repository
public interface BibliotecaLibroRepository extends JpaRepository<BibliotecaLibro, UUID> {

    @Query("SELECT l FROM BibliotecaLibro l WHERE l.isActive = true ORDER BY l.titulo")
    List<BibliotecaLibro> findAllActive();

    @Query("SELECT l FROM BibliotecaLibro l WHERE l.categoria = :categoria AND l.isActive = true")
    List<BibliotecaLibro> findByCategoria(@Param("categoria") String categoria);
}

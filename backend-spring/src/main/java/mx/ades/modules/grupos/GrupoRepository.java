package mx.ades.modules.grupos;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    @EntityGraph(attributePaths = {"grado", "grado.nivel", "grado.plantel", "docentes", "aula"})
    @Query("SELECT DISTINCT g FROM Grupo g " +
           "LEFT JOIN FETCH g.grado gr " +
           "LEFT JOIN FETCH gr.nivel " +
           "LEFT JOIN FETCH gr.plantel " +
           "WHERE gr.plantel.id = :plantelId")
    List<Grupo> findByPlantelId(@Param("plantelId") UUID plantelId);

    @EntityGraph(attributePaths = {"grado", "grado.nivel", "docentes", "aula"})
    Optional<Grupo> findById(UUID id);

    @EntityGraph(attributePaths = {"grado", "grado.nivel", "docentes"})
    @Query("SELECT g FROM Grupo g WHERE g.grado.id = :gradoId")
    List<Grupo> findByGradoId(@Param("gradoId") UUID gradoId);
}

package mx.ades.modules.grupos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    // Grupo no mapea relaciones JPA (@ManyToOne) — solo FKs planas (grado_id,
    // aula_id, etc.), por lo que el join a plantel se resuelve vía SQL nativo
    // a través de ades_grados en lugar de un path de entidad inexistente.
    @Query(value = "SELECT g.* FROM ades_grupos g " +
                    "JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE gr.plantel_id = :plantelId", nativeQuery = true)
    List<Grupo> findByPlantelId(@Param("plantelId") UUID plantelId);

    List<Grupo> findByGradoId(UUID gradoId);
}

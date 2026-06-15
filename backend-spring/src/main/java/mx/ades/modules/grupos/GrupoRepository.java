package mx.ades.modules.grupos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, UUID> {
    
    @Query("SELECT g FROM Grupo g WHERE g.gradoId IN (SELECT gr.id FROM Grado gr WHERE gr.plantel.id = :plantelId)")
    List<Grupo> findByPlantelId(@Param("plantelId") UUID plantelId);
}

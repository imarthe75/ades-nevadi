package mx.ades.modules.horarios;

import mx.ades.modules.horarios.solver.HorarioCorrida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface HorarioCorridaRepository extends JpaRepository<HorarioCorrida, UUID> {
	List<HorarioCorrida> findTop20ByPlantelIdOrderByFechaCreacionDesc(UUID plantelId);

	List<HorarioCorrida> findTop20ByPlantelIdAndCicloEscolarIdOrderByFechaCreacionDesc(UUID plantelId, UUID cicloEscolarId);

    @Query("SELECT MAX(c.version) FROM HorarioCorrida c WHERE c.plantelId = :plantelId AND c.cicloEscolarId = :cicloEscolarId")
    Integer findMaxVersionByPlantelIdAndCicloEscolarId(@Param("plantelId") UUID plantelId, @Param("cicloEscolarId") UUID cicloEscolarId);
}
package mx.ades.modules.horarios.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioFranjaRepository extends JpaRepository<HorarioFranja, UUID> {

    @Query("SELECT f FROM HorarioFranja f WHERE f.cicloEscolarId = :cicloId AND f.isActive = true AND " +
           "(f.plantelId = :plantelId OR f.plantelId IS NULL) AND " +
           "(f.nivelEducativoId = :nivelId OR f.nivelEducativoId IS NULL)")
    List<HorarioFranja> findFranjasAplicables(
            @Param("plantelId") UUID plantelId,
            @Param("cicloId") UUID cicloId,
            @Param("nivelId") UUID nivelId
    );

    List<HorarioFranja> findByNivelEducativoIdOrderByDiaSemanaAscHoraInicioAsc(UUID nivelEducativoId);

    List<HorarioFranja> findByPlantelIdOrderByDiaSemanaAscHoraInicioAsc(UUID plantelId);
}

package mx.ades.modules.horarios.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioReglaRepository extends JpaRepository<HorarioRegla, UUID> {
    List<HorarioRegla> findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(UUID plantelId, UUID cicloEscolarId);
}

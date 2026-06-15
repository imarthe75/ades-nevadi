package mx.ades.modules.medico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidenteMedicoRepository extends JpaRepository<IncidenteMedico, UUID> {
    List<IncidenteMedico> findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(UUID estudianteId);
}

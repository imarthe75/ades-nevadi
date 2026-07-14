package mx.ades.modules.medico;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * IncidenteMedico no mapea relaciones JPA — estudiante_id y
 * personal_salud_id son FKs planas, no aplica @EntityGraph.
 */
@Repository
public interface IncidenteMedicoRepository extends JpaRepository<IncidenteMedico, UUID> {

    List<IncidenteMedico> findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(UUID estudianteId, Pageable pageable);
}

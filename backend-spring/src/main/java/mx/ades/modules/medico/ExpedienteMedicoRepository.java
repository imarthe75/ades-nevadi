package mx.ades.modules.medico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ExpedienteMedico no mapea relaciones JPA — estudiante_id es FK plana y
 * alergias/condiciones/medicamentos son columnas de texto, no aplica
 * @EntityGraph.
 */
@Repository
public interface ExpedienteMedicoRepository extends JpaRepository<ExpedienteMedico, UUID> {

    Optional<ExpedienteMedico> findByEstudianteId(UUID estudianteId);
}

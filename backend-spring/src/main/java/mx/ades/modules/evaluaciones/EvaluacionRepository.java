package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Evaluacion no mapea relaciones JPA — grupo_id y materia_id son FKs
 * planas; no existe columna/relación de rúbrica en esta tabla.
 */
@Repository
public interface EvaluacionRepository extends JpaRepository<Evaluacion, UUID> {

    List<Evaluacion> findByGrupoId(UUID grupoId);
}

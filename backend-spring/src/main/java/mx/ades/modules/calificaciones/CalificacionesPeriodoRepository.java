package mx.ades.modules.calificaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalificacionesPeriodoRepository extends JpaRepository<CalificacionesPeriodo, UUID> {
    List<CalificacionesPeriodo> findByEstudianteId(UUID estudianteId);
    Optional<CalificacionesPeriodo> findByEstudianteIdAndMateriaIdAndPeriodoEvaluacionId(UUID estudianteId, UUID materiaId, UUID periodoEvaluacionId);
}

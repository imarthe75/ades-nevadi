package mx.ades.modules.calificaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA Spring Data para {@link CalificacionEntity}.
 *
 * @author ADES
 * @since 2026
 */
@Repository
public interface CalificacionJpaRepository extends JpaRepository<CalificacionEntity, UUID> {
    List<CalificacionEntity> findByEstudianteId(UUID estudianteId);
    Optional<CalificacionEntity> findByEstudianteIdAndMateriaIdAndPeriodoEvaluacionId(
            UUID estudianteId, UUID materiaId, UUID periodoId);
}

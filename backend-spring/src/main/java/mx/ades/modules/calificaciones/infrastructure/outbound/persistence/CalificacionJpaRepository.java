package mx.ades.modules.calificaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA Spring Data para {@link CalificacionEntity}.
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 *
 * @author ADES
 * @since 2026
 */
@Repository
public interface CalificacionJpaRepository extends JpaRepository<CalificacionEntity, UUID> {

    @EntityGraph(attributePaths = {"estudiante", "materia", "periodoEvaluacion", "estudiante.persona"})
    List<CalificacionEntity> findByEstudianteId(UUID estudianteId);

    @EntityGraph(attributePaths = {"estudiante", "materia", "periodoEvaluacion", "estudiante.persona"})
    Optional<CalificacionEntity> findByEstudianteIdAndMateriaIdAndPeriodoEvaluacionId(
            UUID estudianteId, UUID materiaId, UUID periodoId);

    @EntityGraph(attributePaths = {"estudiante", "materia", "periodoEvaluacion"})
    @Query("SELECT c FROM CalificacionEntity c WHERE c.periodoEvaluacion.id = :periodoId")
    List<CalificacionEntity> findByPeriodoEvaluacionId(@Param("periodoId") UUID periodoId);

    @EntityGraph(attributePaths = {"estudiante", "materia", "periodoEvaluacion"})
    @Query("SELECT c FROM CalificacionEntity c WHERE c.materia.id = :materiaId")
    List<CalificacionEntity> findByMateriaId(@Param("materiaId") UUID materiaId);
}

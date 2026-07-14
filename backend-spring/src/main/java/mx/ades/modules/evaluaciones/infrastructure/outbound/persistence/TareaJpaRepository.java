package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link TareaEntity} ({@code ades_tareas}).
 * TareaEntity no mapea relaciones JPA — grupo_id, plan_trabajo_id y
 * rubrica_id son FKs planas, no aplica @EntityGraph.
 *
 * @author ADES
 * @since 2026
 */
public interface TareaJpaRepository extends JpaRepository<TareaEntity, UUID> {

    List<TareaEntity> findByPlanTrabajoId(UUID planTrabajoId);
}

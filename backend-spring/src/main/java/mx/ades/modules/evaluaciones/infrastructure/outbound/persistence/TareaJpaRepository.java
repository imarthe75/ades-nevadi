package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link TareaEntity} ({@code ades_tareas}).
 *
 * @author ADES
 * @since 2026
 */
public interface TareaJpaRepository extends JpaRepository<TareaEntity, UUID> {
}

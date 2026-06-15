package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TareaJpaRepository extends JpaRepository<TareaEntity, UUID> {
}

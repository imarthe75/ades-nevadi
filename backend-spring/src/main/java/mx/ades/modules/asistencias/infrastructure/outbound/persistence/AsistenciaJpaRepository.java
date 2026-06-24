package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio JPA Spring Data para {@link AsistenciaEntity}.
 *
 * @author ADES
 * @since 2026
 */
@Repository
public interface AsistenciaJpaRepository extends JpaRepository<AsistenciaEntity, UUID> {
    List<AsistenciaEntity> findByClaseId(UUID claseId);
}

package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AsistenciaJpaRepository extends JpaRepository<AsistenciaEntity, UUID> {
    List<AsistenciaEntity> findByClaseId(UUID claseId);
}

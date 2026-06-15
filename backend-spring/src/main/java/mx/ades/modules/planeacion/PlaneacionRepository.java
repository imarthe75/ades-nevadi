package mx.ades.modules.planeacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaneacionRepository extends JpaRepository<Planeacion, UUID> {
    List<Planeacion> findByGrupoId(UUID grupoId);
}

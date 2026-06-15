package mx.ades.modules.asistencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, UUID> {
    List<Asistencia> findByClaseId(UUID claseId);
}

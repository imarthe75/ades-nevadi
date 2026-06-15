package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EscalaEvaluacionRepository extends JpaRepository<EscalaEvaluacion, UUID> {
    List<EscalaEvaluacion> findByNivelEducativoAndIsActiveTrueOrderByNombre(String nivelEducativo);
    List<EscalaEvaluacion> findByIsActiveTrueOrderByNivelEducativoAscNombreAsc();
}

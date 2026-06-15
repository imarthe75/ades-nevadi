package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TareaEntregaRepository extends JpaRepository<TareaEntrega, UUID> {
    List<TareaEntrega> findByTareaId(UUID tareaId);
    List<TareaEntrega> findByEstudianteId(UUID estudianteId);
}

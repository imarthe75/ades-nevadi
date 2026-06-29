package mx.ades.modules.horarios.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface HorarioIndisponibilidadRepository extends JpaRepository<HorarioIndisponibilidad, UUID> {
    
    List<HorarioIndisponibilidad> findByProfesorIdAndCicloEscolarId(UUID profesorId, UUID cicloEscolarId);
    
}

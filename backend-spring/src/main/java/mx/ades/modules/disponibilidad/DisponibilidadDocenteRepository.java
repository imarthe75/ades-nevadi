package mx.ades.modules.disponibilidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DisponibilidadDocenteRepository extends JpaRepository<DisponibilidadDocente, UUID> {
}

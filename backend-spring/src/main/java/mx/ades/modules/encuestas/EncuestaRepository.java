package mx.ades.modules.encuestas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EncuestaRepository extends JpaRepository<Encuesta, UUID> {
}

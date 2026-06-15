package mx.ades.modules.comunicados;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID> {
    List<Comunicado> findByIsActiveTrue();
}

package mx.ades.modules.comunicados;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcuseComunicadoRepository extends JpaRepository<AcuseComunicado, UUID> {
    Optional<AcuseComunicado> findByComunicadoIdAndUsuarioId(UUID comunicadoId, UUID usuarioId);
}

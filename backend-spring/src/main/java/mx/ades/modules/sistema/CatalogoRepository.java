package mx.ades.modules.sistema;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface CatalogoRepository extends JpaRepository<Catalogo, UUID> {
    List<Catalogo> findByIsActiveTrueOrderByCodigo();
    Optional<Catalogo> findByIdAndIsActiveTrue(UUID id);
}

package mx.ades.modules.sistema;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface CatalogoItemRepository extends JpaRepository<CatalogoItem, UUID> {
    Optional<CatalogoItem> findByIdAndIsActiveTrue(UUID id);
}

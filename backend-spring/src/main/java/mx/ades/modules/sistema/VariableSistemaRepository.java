package mx.ades.modules.sistema;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
public interface VariableSistemaRepository extends JpaRepository<VariableSistema, UUID>, JpaSpecificationExecutor<VariableSistema> {

    @EntityGraph(attributePaths = {})
    Optional<VariableSistema> findByNombreAndIsActiveTrue(String nombre);

    @EntityGraph(attributePaths = {})
    List<VariableSistema> findByIsActiveTrueAndEncriptadoFalseAndNombreIn(List<String> nombres);

    @EntityGraph(attributePaths = {})
    Optional<VariableSistema> findById(UUID id);
}

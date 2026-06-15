package mx.ades.modules.sistema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface VariableSistemaRepository extends JpaRepository<VariableSistema, UUID>, JpaSpecificationExecutor<VariableSistema> {
    Optional<VariableSistema> findByNombreAndIsActiveTrue(String nombre);
    List<VariableSistema> findByIsActiveTrueAndEncriptadoFalseAndNombreIn(List<String> nombres);
}

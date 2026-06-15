package mx.ades.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentidadInstitucionalRepository extends JpaRepository<IdentidadInstitucional, UUID> {
    List<IdentidadInstitucional> findByIsActiveTrueAndPlantelIdIsNullOrderByTipoElemento();
    Optional<IdentidadInstitucional> findByTipoElementoAndPlantelIdIsNullAndIsActiveTrue(String tipoElemento);
}

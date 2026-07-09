package mx.ades.modules.usuarios;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface RolRepository extends JpaRepository<Rol, UUID> {

    @EntityGraph(attributePaths = {"permisos"})
    List<Rol> findByNombreRolIn(List<String> nombres);

    @EntityGraph(attributePaths = {"permisos"})
    Optional<Rol> findByNombreRol(String nombreRol);

    @EntityGraph(attributePaths = {"permisos"})
    Optional<Rol> findById(UUID id);
}

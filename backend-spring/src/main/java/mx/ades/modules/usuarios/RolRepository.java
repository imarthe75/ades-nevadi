package mx.ades.modules.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rol no mapea relaciones JPA (no tiene campo "permisos"), no aplica
 * @EntityGraph.
 */
@Repository
public interface RolRepository extends JpaRepository<Rol, UUID> {

    List<Rol> findByNombreRolIn(List<String> nombres);

    Optional<Rol> findByNombreRol(String nombreRol);
}

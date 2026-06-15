package mx.ades.modules.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolRepository extends JpaRepository<Rol, UUID> {
    List<Rol> findByNombreRolIn(List<String> nombres);
    java.util.Optional<Rol> findByNombreRol(String nombreRol);
}

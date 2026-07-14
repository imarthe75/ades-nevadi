package mx.ades.modules.usuarios;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Usuario solo mapea una relación JPA real: rol (@ManyToOne). persona_id
 * y plantel_id son FKs planas — @EntityGraph solo aplica a "rol".
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    @EntityGraph(attributePaths = {"rol"})
    Optional<Usuario> findByOidcSubOrEmailInstitucional(String oidcSub, String emailInstitucional);

    @EntityGraph(attributePaths = {"rol"})
    @Query("SELECT u FROM Usuario u WHERE u.oidcSub = :sub OR (u.oidcSub = :username AND :username IS NOT NULL) OR (u.emailInstitucional = :email AND :email IS NOT NULL) OR (u.nombreUsuario = :username AND :username IS NOT NULL) OR (u.nombreUsuario = :email AND :email IS NOT NULL)")
    Optional<Usuario> findByOidcSubOrEmailOrUsername(@Param("sub") String sub, @Param("email") String email, @Param("username") String username);

    @EntityGraph(attributePaths = {"rol"})
    Optional<Usuario> findById(UUID id);

    @EntityGraph(attributePaths = {"rol"})
    @Query("SELECT u FROM Usuario u WHERE u.isActive = true")
    List<Usuario> findAllActive();
}

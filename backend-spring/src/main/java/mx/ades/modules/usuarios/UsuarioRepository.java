package mx.ades.modules.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByOidcSubOrEmailInstitucional(String oidcSub, String emailInstitucional);

    @Query("SELECT u FROM Usuario u WHERE u.oidcSub = :sub OR (u.oidcSub = :username AND :username IS NOT NULL) OR (u.emailInstitucional = :email AND :email IS NOT NULL) OR (u.nombreUsuario = :username AND :username IS NOT NULL) OR (u.nombreUsuario = :email AND :email IS NOT NULL)")
    Optional<Usuario> findByOidcSubOrEmailOrUsername(@Param("sub") String sub, @Param("email") String email, @Param("username") String username);
}

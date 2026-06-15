package mx.ades.security;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.usuarios.Rol;
import mx.ades.modules.usuarios.RolRepository;
import mx.ades.modules.usuarios.Usuario;
import mx.ades.modules.usuarios.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdesUserService {

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final JdbcTemplate jdbc;

    @Transactional
    public AdesUser resolveUser(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        Usuario usuario = usuarioRepo
                .findByOidcSubOrEmailInstitucional(sub, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no registrado en ADES. Contacta al administrador."));

        List<String> jwtGroups = jwt.getClaimAsStringList("groups");
        if (jwtGroups == null || jwtGroups.isEmpty()) {
            jwtGroups = jwt.getClaimAsStringList("roles");
        }
        if (jwtGroups != null && !jwtGroups.isEmpty()) {
            syncRoles(usuario.getId(), jwtGroups);
        }

        return buildAdesUser(usuario);
    }

    private void syncRoles(UUID userId, List<String> jwtGroups) {
        List<UUID> dbRoleIds = jdbc.queryForList(
                "SELECT rol_id FROM ades_usuario_roles WHERE usuario_id = ?",
                UUID.class, userId);
        List<UUID> jwtRoleIds = rolRepo.findByNombreRolIn(jwtGroups)
                .stream().map(Rol::getId).toList();

        if (!new HashSet<>(dbRoleIds).equals(new HashSet<>(jwtRoleIds))) {
            jdbc.update("DELETE FROM ades_usuario_roles WHERE usuario_id = ?", userId);
            jwtRoleIds.forEach(rid ->
                    jdbc.update("INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso) VALUES (?,?,100)",
                            userId, rid));
        }
    }

    private AdesUser buildAdesUser(Usuario usuario) {
        List<String> roles = jdbc.queryForList(
                "SELECT r.nombre_rol FROM ades_roles r " +
                        "JOIN ades_usuario_roles ur ON ur.rol_id = r.id " +
                        "WHERE ur.usuario_id = ?",
                String.class, usuario.getId());

        if (roles.isEmpty() && usuario.getRol() != null) {
            roles = List.of(usuario.getRol().getNombreRol());
        }

        return AdesUser.builder()
                .id(usuario.getId())
                .username(usuario.getNombreUsuario())
                .email(usuario.getEmailInstitucional())
                .personaId(usuario.getPersonaId())
                .plantelId(usuario.getPlantelId())
                .nivelEducativoId(usuario.getNivelEducativoId())
                .rolPrincipalId(usuario.getRol() != null ? usuario.getRol().getId() : null)
                .roles(roles)
                .nivelAcceso(usuario.getRol() != null ? usuario.getRol().getNivelAcceso() : 99)
                .build();
    }
}

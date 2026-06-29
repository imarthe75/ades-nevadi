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
import java.util.Map;
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
        String username = jwt.getClaimAsString("preferred_username");

        Usuario usuario = usuarioRepo
                .findByOidcSubOrEmailOrUsername(sub, email, username)
                .orElseThrow(() -> {
                    System.out.println("USER NOT FOUND IN DB. sub=" + sub + ", email=" + email + ", username=" + username);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no registrado en ADES. Contacta al administrador.");
                });

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

    /**
     * Devuelve el plantel_id efectivo para queries de datos sensibles.
     * - nivel_acceso = 1 (superadmin): usa el plantelId del request (puede ser null = todos)
     * - nivel_acceso > 1: fuerza el plantel del usuario — no puede ver datos de otros planteles
     */
    public UUID getEffectivePlantelId(AdesUser user, UUID requestedPlantelId) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            return user.getPlantelId();
        }
        return requestedPlantelId;
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

        String nombreCompleto = null;
        if (usuario.getPersonaId() != null) {
            try {
                nombreCompleto = jdbc.queryForObject(
                        "SELECT TRIM(CONCAT(nombre, ' ', apellido_paterno, ' ', COALESCE(apellido_materno, ''))) FROM ades_personas WHERE id = ?",
                        String.class, usuario.getPersonaId());
            } catch (Exception e) {}
        }

        String nombrePlantel = null;
        if (usuario.getPlantelId() != null) {
            try {
                nombrePlantel = jdbc.queryForObject(
                        "SELECT nombre_plantel FROM ades_planteles WHERE id = ?",
                        String.class, usuario.getPlantelId());
            } catch (Exception e) {}
        }

        String nombreNivel = null;
        if (usuario.getNivelEducativoId() != null) {
            try {
                nombreNivel = jdbc.queryForObject(
                        "SELECT nombre_nivel FROM ades_niveles_educativos WHERE id = ?",
                        String.class, usuario.getNivelEducativoId());
            } catch (Exception e) {}
        }

        UUID grupoId = null;
        UUID gradoId = null;
        String nombreGrupo = null;
        String nombreGrado = null;

        if (roles.contains("ALUMNO")) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT i.grupo_id, g.grado_id, g.nombre_grupo, gr.nombre_grado " +
                        "FROM ades_inscripciones i " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "JOIN ades_grados gr ON gr.id = g.grado_id " +
                        "JOIN ades_estudiantes est ON est.id = i.estudiante_id " +
                        "WHERE est.persona_id = ? AND i.is_active = TRUE LIMIT 1",
                        usuario.getPersonaId());
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    grupoId = (UUID) row.get("grupo_id");
                    gradoId = (UUID) row.get("grado_id");
                    nombreGrupo = (String) row.get("nombre_grupo");
                    nombreGrado = (String) row.get("nombre_grado");
                }
            } catch (Exception e) {}
        } else if (roles.contains("DOCENTE")) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT DISTINCT c.grupo_id, g.grado_id, g.nombre_grupo, gr.nombre_grado " +
                        "FROM ades_clases c " +
                        "JOIN ades_grupos g ON g.id = c.grupo_id " +
                        "JOIN ades_grados gr ON gr.id = g.grado_id " +
                        "JOIN ades_profesores prof ON prof.id = c.profesor_id " +
                        "WHERE prof.persona_id = ?",
                        usuario.getPersonaId());
                if (rows.size() == 1) {
                    Map<String, Object> row = rows.get(0);
                    grupoId = (UUID) row.get("grupo_id");
                    gradoId = (UUID) row.get("grado_id");
                    nombreGrupo = (String) row.get("nombre_grupo");
                    nombreGrado = (String) row.get("nombre_grado");
                }
            } catch (Exception e) {}
        }

        return AdesUser.builder()
                .id(usuario.getId())
                .username(usuario.getNombreUsuario())
                .email(usuario.getEmailInstitucional())
                .personaId(usuario.getPersonaId())
                .plantelId(usuario.getPlantelId())
                .nivelEducativoId(usuario.getNivelEducativoId())
                .gradoId(gradoId)
                .grupoId(grupoId)
                .nombreGrado(nombreGrado)
                .nombreGrupo(nombreGrupo)
                .nombreCompleto(nombreCompleto)
                .nombrePlantel(nombrePlantel)
                .nombreNivel(nombreNivel)
                .rolPrincipalId(usuario.getRol() != null ? usuario.getRol().getId() : null)
                .roles(roles)
                .nivelAcceso(usuario.getRol() != null ? usuario.getRol().getNivelAcceso() : 99)
                .build();
    }
}

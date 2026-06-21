package mx.ades.modules.usuarios;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuariosController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    /**
     * Retorna el perfil del usuario autenticado incluyendo estudiante_id (si es alumno)
     * y profesor_id (si es docente), para que los módulos como mi-progreso puedan operar.
     */
    @GetMapping("/mi-perfil")
    public ResponseEntity<Map<String, Object>> miPerfil(@AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String sub = jwt.getSubject();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sub", sub);
        result.put("nombre_usuario", user.getUsername());
        result.put("email", user.getEmail());
        result.put("roles", user.getRoles());
        result.put("plantel_id", user.getPlantelId());
        result.put("nivel_acceso", user.getNivelAcceso());

        // Buscar usuario en BD para obtener persona_id
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id AS usuario_id, persona_id, plantel_id AS plantel_id_bd, nivel_educativo_id " +
            "FROM ades_usuarios WHERE oidc_sub = ? AND is_active = true LIMIT 1", sub);

        if (!rows.isEmpty()) {
            Map<String, Object> u = rows.get(0);
            result.put("usuario_id", u.get("usuario_id"));
            result.put("persona_id", u.get("persona_id"));
            result.put("nivel_educativo_id", u.get("nivel_educativo_id"));

            Object personaId = u.get("persona_id");
            if (personaId != null) {
                // Buscar si es estudiante
                List<Map<String, Object>> est = jdbc.queryForList(
                    "SELECT id AS estudiante_id, matricula FROM ades_estudiantes " +
                    "WHERE persona_id = ?::uuid AND is_active = true LIMIT 1",
                    personaId.toString());
                if (!est.isEmpty()) {
                    result.put("estudiante_id", est.get(0).get("estudiante_id"));
                    result.put("matricula", est.get(0).get("matricula"));
                }

                // Buscar si es profesor
                List<Map<String, Object>> prof = jdbc.queryForList(
                    "SELECT id AS profesor_id FROM ades_profesores " +
                    "WHERE persona_id = ?::uuid AND is_active = true LIMIT 1",
                    personaId.toString());
                if (!prof.isEmpty()) {
                    result.put("profesor_id", prof.get(0).get("profesor_id"));
                }
            }
        }

        return ResponseEntity.ok(result);
    }
}

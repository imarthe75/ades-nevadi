package mx.ades.modules.auditoria;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarAuditLog(
            @RequestParam(value = "limite", defaultValue = "200") int limite,
            @RequestParam(value = "entidad", required = false) String entidad,
            @RequestParam(value = "accion", required = false) String accion,
            @RequestParam(value = "usuario_id", required = false) UUID usuarioId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL tiene acceso al registro de auditoría");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, usuario_id, nombre_usuario, ip_origen, accion, entidad, entidad_id, " +
                "endpoint, metodo_http, codigo_respuesta, duracion_ms, fecha_creacion, row_version " +
                "FROM ades_audit_log WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (entidad != null && !entidad.isBlank()) {
            sql.append("AND entidad = ? ");
            params.add(entidad);
        }
        if (accion != null && !accion.isBlank()) {
            sql.append("AND accion = ? ");
            params.add(accion);
        }
        if (usuarioId != null) {
            sql.append("AND usuario_id = ? ");
            params.add(usuarioId);
        }

        sql.append("ORDER BY fecha_creacion DESC LIMIT ?");
        params.add(limite);

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }
}

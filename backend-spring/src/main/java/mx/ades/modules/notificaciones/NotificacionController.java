package mx.ades.modules.notificaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping("/mis-notificaciones")
    public ResponseEntity<List<Map<String, Object>>> misNotificaciones(
            @RequestParam(value = "solo_no_leidas", defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT id, titulo, mensaje AS cuerpo, tipo, leido, fecha_creacion " +
                "FROM ades_notificaciones_sistema " +
                "WHERE usuario_id = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(user.getId());

        if (soloNoLeidas) {
            sql.append("AND leido = FALSE ");
        }

        sql.append("ORDER BY fecha_creacion DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

        // Ensure string formats match the expected type
        for (Map<String, Object> r : rows) {
            if (r.get("id") != null) {
                r.put("id", r.get("id").toString());
            }
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/no-leidas-count")
    public ResponseEntity<Map<String, Object>> noLeidasCount(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_notificaciones_sistema WHERE usuario_id = ? AND leido = FALSE",
                Long.class, user.getId()
        );

        return ResponseEntity.ok(Map.of("total", count != null ? count : 0L));
    }

    @PutMapping("/{notifId}/leer")
    public ResponseEntity<Map<String, Object>> marcarLeida(
            @PathVariable("notifId") UUID notifId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        jdbc.update(
                "UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE id = ? AND usuario_id = ?",
                notifId, user.getId()
        );

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PutMapping("/leer-todas")
    public ResponseEntity<Map<String, Object>> marcarTodasLeidas(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        jdbc.update(
                "UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE usuario_id = ? AND leido = FALSE",
                user.getId()
        );

        return ResponseEntity.ok(Map.of("ok", true));
    }
}

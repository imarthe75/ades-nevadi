package mx.ades.modules.notificaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.notificaciones.query.NotificacionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final NotificacionQueryService queryService;

    @GetMapping("/mis-notificaciones")
    public ResponseEntity<List<Map<String, Object>>> misNotificaciones(
            @RequestParam(value = "solo_no_leidas", defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.misNotificaciones(user.getId(), soloNoLeidas, limit));
    }

    @GetMapping("/no-leidas-count")
    public ResponseEntity<Map<String, Object>> noLeidasCount(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        return ResponseEntity.ok(Map.of("total", queryService.contarNoLeidas(user.getId())));
    }

    @PutMapping("/{notifId}/leer")
    public ResponseEntity<Map<String, Object>> marcarLeida(
            @PathVariable("notifId") UUID notifId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        jdbc.update("UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE id = ? AND usuario_id = ?",
                notifId, user.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PutMapping("/leer-todas")
    public ResponseEntity<Map<String, Object>> marcarTodasLeidas(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        jdbc.update("UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE usuario_id = ? AND leido = FALSE",
                user.getId());
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

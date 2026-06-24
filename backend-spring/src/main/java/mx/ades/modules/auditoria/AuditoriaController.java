package mx.ades.modules.auditoria;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.auditoria.query.AuditoriaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * Adaptador REST para la consulta del registro de auditoría del sistema.
 * Expone un único endpoint GET bajo /api/v1/auditoria que devuelve el log
 * de cambios con hash MD5 encadenado almacenado en {@code auditoria.log_auditoria}.
 * Filtrable por entidad, acción, usuario y límite de registros.
 * Acceso exclusivo para ADMIN_GLOBAL (nivelAcceso = 0 en esta implementación).
 * Los datos de auditoría contienen trazabilidad completa de INSERT/UPDATE/DELETE
 * con identificación del usuario que realizó cada operación.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AdesUserService userService;
    private final AuditoriaQueryService queryService;

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
        return ResponseEntity.ok(queryService.listar(entidad, accion, usuarioId, limite));
    }
}

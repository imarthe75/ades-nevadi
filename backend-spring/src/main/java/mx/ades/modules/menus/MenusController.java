package mx.ades.modules.menus;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.menus.query.MenusQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenusController {

    private final AdesUserService userService;
    private final MenusQueryService queryService;

    @Data
    public static class MenuNode {
        private UUID id;
        private String label;
        private String route;
        private String icon;
        private UUID parentId;
        private Integer peso;
        private List<MenuNode> children = new ArrayList<>();
    }

    @GetMapping("/mi-menu")
    public ResponseEntity<List<MenuNode>> obtenerMiMenu(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() <= 1) {
            return ResponseEntity.ok(queryService.menuParaAdminGlobal());
        }
        return ResponseEntity.ok(queryService.menuParaRol(user.getRolPrincipalId()));
    }
}

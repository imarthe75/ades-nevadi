package mx.ades.modules.menus;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

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

        List<Map<String, Object>> rows;
        if (user.getNivelAcceso() <= 1) {
            String sql = "SELECT id, label, route, icon, parent_id, peso FROM ades_menus WHERE is_active = TRUE ORDER BY peso";
            rows = jdbc.queryForList(sql);
        } else {
            String sql = "SELECT m.id, m.label, m.route, m.icon, m.parent_id, m.peso " +
                    "FROM ades_menus m " +
                    "JOIN ades_menu_roles mr ON m.id = mr.menu_id " +
                    "WHERE m.is_active = TRUE AND mr.rol_id = ? " +
                    "ORDER BY m.peso";
            rows = jdbc.queryForList(sql, user.getRolPrincipalId());
        }

        return ResponseEntity.ok(construirArbol(rows));
    }

    private List<MenuNode> construirArbol(List<Map<String, Object>> rows) {
        Map<UUID, MenuNode> porId = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            MenuNode node = new MenuNode();
            node.setId((UUID) row.get("id"));
            node.setLabel((String) row.get("label"));
            node.setRoute((String) row.get("route"));
            node.setIcon((String) row.get("icon"));
            node.setParentId((UUID) row.get("parent_id"));
            node.setPeso((Integer) row.get("peso"));
            porId.put(node.getId(), node);
        }

        List<MenuNode> raiz = new ArrayList<>();
        for (MenuNode node : porId.values()) {
            if (node.getParentId() != null && porId.containsKey(node.getParentId())) {
                porId.get(node.getParentId()).getChildren().add(node);
            } else if (node.getParentId() == null) {
                raiz.add(node);
            }
        }

        raiz.sort(Comparator.comparingInt(m -> m.getPeso() != null ? m.getPeso() : 0));
        return raiz;
    }
}

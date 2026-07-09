package mx.ades.modules.menus.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.menus.MenusController.MenuNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MenusQueryService {

    private final JdbcTemplate jdbc;

    public List<MenuNode> menuParaAdminGlobal() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, label, route, icon, parent_id, peso FROM ades_menus WHERE is_active = TRUE ORDER BY peso");
        return construirArbol(rows);
    }

    public List<MenuNode> menuParaRol(UUID rolId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT m.id, m.label, m.route, m.icon, m.parent_id, m.peso " +
            "FROM ades_menus m " +
            "JOIN ades_menu_roles mr ON m.id = mr.menu_id " +
            "WHERE m.is_active = TRUE AND mr.rol_id = ? ORDER BY m.peso", rolId);
        return construirArbol(rows);
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

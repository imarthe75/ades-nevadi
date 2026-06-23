package mx.ades.modules.menus;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.menus.query.MenusQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenusController {

    private final AdesUserService userService;
    private final MenusQueryService queryService;
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
        if (user.getNivelAcceso() <= 1) {
            return ResponseEntity.ok(queryService.menuParaAdminGlobal());
        }
        return ResponseEntity.ok(queryService.menuParaRol(user.getRolPrincipalId()));
    }

    /**
     * Catálogo completo de menús activos con metadatos de visibilidad por nivel.
     * El frontend Angular lo usa para construir el sidebar dinámicamente.
     * GET /api/v1/menus/catalogo
     */
    @GetMapping("/catalogo")
    public ResponseEntity<List<Map<String, Object>>> catalogo(
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(jdbc.queryForList(
            "SELECT clave, seccion, label, icon AS icono, route AS ruta, " +
            "nivel_maximo, nivel_minimo, is_active AS activo, peso AS orden " +
            "FROM ades_menus WHERE is_active = TRUE ORDER BY peso"));
    }

    /**
     * Mapa de permisos del rol del usuario activo.
     * Para roles sin registro explícito → permisos por defecto (ver+editar+crear, no eliminar).
     * GET /api/v1/mis-permisos
     */
    @GetMapping("/mis-permisos")
    public ResponseEntity<Map<String, Object>> misPermisos(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID rolId = user.getRolPrincipalId();
        Map<String, Object> permisos = new LinkedHashMap<>();
        if (rolId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT m.clave, mr.puede_ver, mr.puede_editar, mr.puede_crear, mr.puede_eliminar " +
                "FROM ades_menu_roles mr " +
                "JOIN ades_menus m ON m.id = mr.menu_id " +
                "WHERE mr.rol_id = ?", rolId);
            for (Map<String, Object> row : rows) {
                String clave = (String) row.get("clave");
                permisos.put(clave, Map.of(
                    "puedeVer",      Boolean.TRUE.equals(row.get("puede_ver")),
                    "puedeEditar",   Boolean.TRUE.equals(row.get("puede_editar")),
                    "puedeCrear",    Boolean.TRUE.equals(row.get("puede_crear")),
                    "puedeEliminar", Boolean.TRUE.equals(row.get("puede_eliminar"))
                ));
            }
        }
        return ResponseEntity.ok(permisos);
    }
}

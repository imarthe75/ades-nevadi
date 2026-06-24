package mx.ades.modules.sistema;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogosSistemaController {

    private final AdesUserService userService;
    private final CatalogoRepository catalogoRepo;
    private final CatalogoItemRepository itemRepo;
    private final VariableSistemaRepository variableRepo;
    private final SistemaWriteService writeService;

    private void requireAdmin(AdesUser user) {
        if (user.getNivelAcceso() > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol de Administrador Global o de Plantel");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCHEMAS / DTOS
    // ──────────────────────────────────────────────────────────────────────────
    @Data
    public static class CatalogoRequest {
        private String codigo;
        private String nombre;
        private String descripcion;
        private Integer rowVersion;
    }

    @Data
    public static class ItemRequest {
        private String valor;
        private String descripcion;
        private Integer orden;
        private Integer rowVersion;
    }

    @Data
    public static class VariableRequest {
        private String nombre;
        private String tipoValor;
        private String valor;
        private String descripcion;
        private String grupo;
        private Integer rowVersion;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CATÁLOGOS ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/catalogos")
    public ResponseEntity<List<Catalogo>> listarCatalogos(
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(catalogoRepo.findByIsActiveTrueOrderByCodigo());
    }

    @GetMapping("/catalogos/{id}")
    public ResponseEntity<Catalogo> obtenerCatalogo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));
        return ResponseEntity.ok(cat);
    }

    @PostMapping("/catalogos")
    public ResponseEntity<Catalogo> crearCatalogo(
            @RequestBody CatalogoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(writeService.crearCatalogo(body.getCodigo(), body.getNombre(), body.getDescripcion()));
    }

    @PatchMapping("/catalogos/{id}")
    public ResponseEntity<Catalogo> actualizarCatalogo(
            @PathVariable("id") UUID id,
            @RequestBody CatalogoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.ok(writeService.actualizarCatalogo(
            id, body.getCodigo(), body.getNombre(), body.getDescripcion(), body.getRowVersion()));
    }

    @DeleteMapping("/catalogos/{id}")
    public ResponseEntity<Void> eliminarCatalogo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        writeService.eliminarCatalogo(id);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ITEMS DE CATÁLOGO ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/catalogos/{id}/items")
    public ResponseEntity<CatalogoItem> agregarItem(
            @PathVariable("id") UUID catalogoId,
            @RequestBody ItemRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(writeService.agregarItem(catalogoId, body.getValor(), body.getDescripcion(), body.getOrden()));
    }

    @PatchMapping("/catalogos/items/{item_id}")
    public ResponseEntity<CatalogoItem> actualizarItem(
            @PathVariable("item_id") UUID itemId,
            @RequestBody ItemRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.ok(writeService.actualizarItem(
            itemId, body.getValor(), body.getDescripcion(), body.getOrden(), body.getRowVersion()));
    }

    @DeleteMapping("/catalogos/items/{item_id}")
    public ResponseEntity<Void> eliminarItem(
            @PathVariable("item_id") UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        writeService.eliminarItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/catalogos/{id}/items/reorder")
    public ResponseEntity<Map<String, String>> reordenarItems(
            @PathVariable("id") UUID catalogoId,
            @RequestBody List<Map<String, Object>> ordenData,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        writeService.reordenarItems(catalogoId, ordenData);
        return ResponseEntity.ok(Map.of("message", "Items reordenados correctamente"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VARIABLES DEL SISTEMA ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/config/variables")
    public ResponseEntity<List<VariableSistema>> listarVariables(
            @RequestParam(value = "grupo", required = false) String grupo,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "tipo_valor", required = false) String tipoValor,
            @RequestParam(value = "solo_lectura", required = false) Boolean soloLectura,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Specification<VariableSistema> spec = Specification.where((root, query, cb) -> cb.equal(root.get("isActive"), true));

        if (grupo != null && !grupo.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("grupo"), grupo));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nombre")), "%" + search.toLowerCase() + "%"));
        }
        if (tipoValor != null && !tipoValor.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoValor"), tipoValor));
        }
        if (soloLectura != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("soloLectura"), soloLectura));
        }

        return ResponseEntity.ok(variableRepo.findAll(spec, Sort.by("grupo", "nombre")));
    }

    @GetMapping("/config/public")
    public ResponseEntity<List<VariableSistema>> variablesPublicas() {
        List<String> nombres = Arrays.asList("NOMBRE_INSTITUCION", "NOMBRE_SISTEMA", "JSON_CONFIG_UI", "JSON_MARCA", "URL_PORTAL");
        return ResponseEntity.ok(variableRepo.findByIsActiveTrueAndEncriptadoFalseAndNombreIn(nombres));
    }

    @PostMapping("/config/variables")
    public ResponseEntity<VariableSistema> crearVariable(
            @RequestBody VariableRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(writeService.crearVariable(
            body.getNombre(), body.getTipoValor(), body.getValor(), body.getDescripcion(), body.getGrupo()));
    }

    @PatchMapping("/config/variables/{nombre}")
    public ResponseEntity<VariableSistema> actualizarVariable(
            @PathVariable("nombre") String nombre,
            @RequestBody VariableRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.ok(writeService.actualizarVariable(
            nombre, body.getValor(), body.getDescripcion(), body.getGrupo(), body.getRowVersion()));
    }
}

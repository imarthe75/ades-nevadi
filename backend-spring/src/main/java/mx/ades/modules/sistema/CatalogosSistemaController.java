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
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional
    public ResponseEntity<Catalogo> crearCatalogo(
            @RequestBody CatalogoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Catalogo cat = new Catalogo();
        cat.setCodigo(body.getCodigo());
        cat.setNombre(body.getNombre());
        cat.setDescripcion(body.getDescripcion());
        cat.setIsActive(true);

        Catalogo saved = catalogoRepo.save(cat);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/catalogos/{id}")
    @Transactional
    public ResponseEntity<Catalogo> actualizarCatalogo(
            @PathVariable("id") UUID id,
            @RequestBody CatalogoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));

        if (body.getRowVersion() != null && !body.getRowVersion().equals(cat.getRowVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: el registro fue modificado por otro usuario.");
        }

        if (body.getCodigo() != null) cat.setCodigo(body.getCodigo());
        if (body.getNombre() != null) cat.setNombre(body.getNombre());
        if (body.getDescripcion() != null) cat.setDescripcion(body.getDescripcion());

        Catalogo saved = catalogoRepo.save(cat);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/catalogos/{id}")
    @Transactional
    public ResponseEntity<Void> eliminarCatalogo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));

        cat.setIsActive(false);
        for (CatalogoItem item : cat.getItems()) {
            item.setIsActive(false);
        }

        catalogoRepo.save(cat);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ITEMS DE CATÁLOGO ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/catalogos/{id}/items")
    @Transactional
    public ResponseEntity<CatalogoItem> agregarItem(
            @PathVariable("id") UUID catalogoId,
            @RequestBody ItemRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(catalogoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));

        CatalogoItem item = new CatalogoItem();
        item.setCatalogo(cat);
        item.setValor(body.getValor());
        item.setDescripcion(body.getDescripcion());
        if (body.getOrden() != null) item.setOrden(body.getOrden());
        item.setIsActive(true);

        CatalogoItem saved = itemRepo.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/catalogos/items/{item_id}")
    @Transactional
    public ResponseEntity<CatalogoItem> actualizarItem(
            @PathVariable("item_id") UUID itemId,
            @RequestBody ItemRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        CatalogoItem item = itemRepo.findByIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));

        if (body.getRowVersion() != null && !body.getRowVersion().equals(item.getRowVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: el registro fue modificado por otro usuario.");
        }

        if (body.getValor() != null) item.setValor(body.getValor());
        if (body.getDescripcion() != null) item.setDescripcion(body.getDescripcion());
        if (body.getOrden() != null) item.setOrden(body.getOrden());

        CatalogoItem saved = itemRepo.save(item);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/catalogos/items/{item_id}")
    @Transactional
    public ResponseEntity<Void> eliminarItem(
            @PathVariable("item_id") UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        CatalogoItem item = itemRepo.findByIdAndIsActiveTrue(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));

        item.setIsActive(false);
        itemRepo.save(item);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/catalogos/{id}/items/reorder")
    @Transactional
    public ResponseEntity<Map<String, String>> reordenarItems(
            @PathVariable("id") UUID catalogoId,
            @RequestBody List<Map<String, Object>> ordenData,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(catalogoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));

        Map<String, Integer> ordenMap = new HashMap<>();
        for (Map<String, Object> item : ordenData) {
            if (item.containsKey("id") && item.containsKey("orden")) {
                ordenMap.put(item.get("id").toString(), ((Number) item.get("orden")).intValue());
            }
        }

        for (CatalogoItem item : cat.getItems()) {
            if (ordenMap.containsKey(item.getId().toString())) {
                item.setOrden(ordenMap.get(item.getId().toString()));
            }
        }

        catalogoRepo.save(cat);
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
    @Transactional
    public ResponseEntity<VariableSistema> crearVariable(
            @RequestBody VariableRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Optional<VariableSistema> existing = variableRepo.findByNombreAndIsActiveTrue(body.getNombre());
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una variable con nombre '" + body.getNombre() + "'");
        }

        validarValorPorTipo(body.getValor(), body.getTipoValor());

        VariableSistema var = new VariableSistema();
        var.setNombre(body.getNombre());
        var.setTipoValor(body.getTipoValor());
        var.setValor(body.getValor());
        var.setDescripcion(body.getDescripcion());
        var.setGrupo(body.getGrupo());
        var.setIsActive(true);

        VariableSistema saved = variableRepo.save(var);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/config/variables/{nombre}")
    @Transactional
    public ResponseEntity<VariableSistema> actualizarVariable(
            @PathVariable("nombre") String nombre,
            @RequestBody VariableRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        VariableSistema var = variableRepo.findByNombreAndIsActiveTrue(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable '" + nombre + "' no encontrada"));

        if (var.getSoloLectura()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La variable '" + nombre + "' es de solo lectura y no puede modificarse desde la UI");
        }

        if (body.getRowVersion() != null && !body.getRowVersion().equals(var.getRowVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: el registro fue modificado por otro usuario.");
        }

        if (body.getValor() != null) {
            validarValorPorTipo(body.getValor(), var.getTipoValor());
            var.setValor(body.getValor());
        }
        if (body.getDescripcion() != null) var.setDescripcion(body.getDescripcion());
        if (body.getGrupo() != null) var.setGrupo(body.getGrupo());

        VariableSistema saved = variableRepo.save(var);
        return ResponseEntity.ok(saved);
    }

    private void validarValorPorTipo(String valor, String tipoValor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        if ("JSON".equalsIgnoreCase(tipoValor)) {
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(valor);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El valor proporcionado no es un JSON válido");
            }
        } else if ("NUMERO".equalsIgnoreCase(tipoValor)) {
            try {
                Double.parseDouble(valor);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El valor proporcionado no es un número válido");
            }
        }
    }
}

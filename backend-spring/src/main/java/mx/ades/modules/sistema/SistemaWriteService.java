package mx.ades.modules.sistema;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de escritura para catálogos y variables del sistema.
 * CatalogosSistemaController delega aquí todas las mutaciones (POST / PATCH / DELETE).
 */
@Service
@RequiredArgsConstructor
public class SistemaWriteService {

    private final CatalogoRepository catalogoRepo;
    private final CatalogoItemRepository itemRepo;
    private final VariableSistemaRepository variableRepo;

    // ── CATÁLOGOS ─────────────────────────────────────────────────────────────

    @Transactional
    public Catalogo crearCatalogo(String codigo, String nombre, String descripcion) {
        Catalogo cat = new Catalogo();
        cat.setCodigo(codigo);
        cat.setNombre(nombre);
        cat.setDescripcion(descripcion);
        cat.setIsActive(true);
        return catalogoRepo.save(cat);
    }

    @Transactional
    public Catalogo actualizarCatalogo(UUID id, String codigo, String nombre, String descripcion, Integer rowVersion) {
        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));
        if (rowVersion != null && !rowVersion.equals(cat.getRowVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: registro modificado por otro usuario.");
        if (codigo != null) cat.setCodigo(codigo);
        if (nombre != null) cat.setNombre(nombre);
        if (descripcion != null) cat.setDescripcion(descripcion);
        return catalogoRepo.save(cat);
    }

    @Transactional
    public void eliminarCatalogo(UUID id) {
        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));
        cat.setIsActive(false);
        for (CatalogoItem item : cat.getItems()) item.setIsActive(false);
        catalogoRepo.save(cat);
    }

    // ── ÍTEMS ─────────────────────────────────────────────────────────────────

    @Transactional
    public CatalogoItem agregarItem(UUID catalogoId, String valor, String descripcion, Integer orden) {
        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(catalogoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));
        CatalogoItem item = new CatalogoItem();
        item.setCatalogo(cat);
        item.setValor(valor);
        item.setDescripcion(descripcion);
        if (orden != null) item.setOrden(orden);
        item.setIsActive(true);
        return itemRepo.save(item);
    }

    @Transactional
    public CatalogoItem actualizarItem(UUID itemId, String valor, String descripcion, Integer orden, Integer rowVersion) {
        CatalogoItem item = itemRepo.findByIdAndIsActiveTrue(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
        if (rowVersion != null && !rowVersion.equals(item.getRowVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: registro modificado por otro usuario.");
        if (valor != null) item.setValor(valor);
        if (descripcion != null) item.setDescripcion(descripcion);
        if (orden != null) item.setOrden(orden);
        return itemRepo.save(item);
    }

    @Transactional
    public void eliminarItem(UUID itemId) {
        CatalogoItem item = itemRepo.findByIdAndIsActiveTrue(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
        item.setIsActive(false);
        itemRepo.save(item);
    }

    @Transactional
    public void reordenarItems(UUID catalogoId, List<Map<String, Object>> ordenData) {
        Catalogo cat = catalogoRepo.findByIdAndIsActiveTrue(catalogoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado"));
        Map<String, Integer> ordenMap = new java.util.HashMap<>();
        for (Map<String, Object> entry : ordenData) {
            if (entry.containsKey("id") && entry.containsKey("orden"))
                ordenMap.put(entry.get("id").toString(), ((Number) entry.get("orden")).intValue());
        }
        for (CatalogoItem item : cat.getItems()) {
            if (ordenMap.containsKey(item.getId().toString()))
                item.setOrden(ordenMap.get(item.getId().toString()));
        }
        catalogoRepo.save(cat);
    }

    // ── VARIABLES ─────────────────────────────────────────────────────────────

    @Transactional
    public VariableSistema crearVariable(String nombre, String tipoValor, String valor, String descripcion, String grupo) {
        Optional<VariableSistema> existing = variableRepo.findByNombreAndIsActiveTrue(nombre);
        if (existing.isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una variable con nombre '" + nombre + "'");
        validarValorPorTipo(valor, tipoValor);
        VariableSistema var = new VariableSistema();
        var.setNombre(nombre);
        var.setTipoValor(tipoValor);
        var.setValor(valor);
        var.setDescripcion(descripcion);
        var.setGrupo(grupo);
        var.setIsActive(true);
        return variableRepo.save(var);
    }

    @Transactional
    public VariableSistema actualizarVariable(String nombre, String valor, String descripcion, String grupo, Integer rowVersion) {
        VariableSistema var = variableRepo.findByNombreAndIsActiveTrue(nombre)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable '" + nombre + "' no encontrada"));
        if (var.getSoloLectura())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La variable '" + nombre + "' es de solo lectura");
        if (rowVersion != null && !rowVersion.equals(var.getRowVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: registro modificado por otro usuario.");
        if (valor != null) { validarValorPorTipo(valor, var.getTipoValor()); var.setValor(valor); }
        if (descripcion != null) var.setDescripcion(descripcion);
        if (grupo != null) var.setGrupo(grupo);
        return variableRepo.save(var);
    }

    private void validarValorPorTipo(String valor, String tipoValor) {
        if (valor == null || valor.isBlank()) return;
        if ("JSON".equalsIgnoreCase(tipoValor)) {
            try { new ObjectMapper().readTree(valor); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El valor no es un JSON válido"); }
        } else if ("NUMERO".equalsIgnoreCase(tipoValor)) {
            try { Double.parseDouble(valor); }
            catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El valor debe ser numérico"); }
        } else if ("BOOLEAN".equalsIgnoreCase(tipoValor)) {
            if (!"true".equalsIgnoreCase(valor) && !"false".equalsIgnoreCase(valor))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El valor debe ser 'true' o 'false'");
        }
    }
}

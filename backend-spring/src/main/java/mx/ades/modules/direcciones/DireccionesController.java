package mx.ades.modules.direcciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DireccionesController {

    private final AdesUserService userService;
    private final DireccionesQueryService queryService;
    private final DireccionesWriteService writeService;

    // ── SEPOMEX ───────────────────────────────────────────────────────────────

    @GetMapping("/catalogs/sepomex/por-cp")
    public ResponseEntity<List<Map<String, Object>>> porCp(@RequestParam("cp") String cp) {
        if (cp == null || !cp.matches("\\d{5}")) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(queryService.porCp(cp));
    }

    @GetMapping("/catalogs/sepomex/buscar")
    public ResponseEntity<List<Map<String, Object>>> buscar(
            @RequestParam("q") String q,
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(queryService.buscar(q, limit));
    }

    @GetMapping("/catalogs/tipos-asentamiento")
    public ResponseEntity<List<Map<String, Object>>> tiposAsentamiento() {
        return ResponseEntity.ok(queryService.tiposAsentamiento());
    }

    @GetMapping("/catalogs/estados-mexico")
    public ResponseEntity<List<Map<String, Object>>> estados() {
        return ResponseEntity.ok(queryService.estados());
    }

    @GetMapping("/catalogs/municipios")
    public ResponseEntity<List<Map<String, Object>>> municipios(
            @RequestParam(name = "estado_id", required = false) UUID estadoId) {
        return ResponseEntity.ok(queryService.municipios(estadoId));
    }

    // ── DIRECCIONES CRUD ──────────────────────────────────────────────────────

    @GetMapping("/direcciones")
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam("entidad_tipo") String entidadTipo,
            @RequestParam("entidad_id") UUID entidadId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarDirecciones(entidadTipo, entidadId));
    }

    @PostMapping("/direcciones")
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody DireccionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (body.getEntidadTipo() == null || body.getEntidadId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "entidad_tipo y entidad_id son requeridos");
        }
        UUID id = writeService.crearDireccion(body, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getDirById(id));
    }

    @PatchMapping("/direcciones/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody DireccionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<Map<String, Object>> existing = queryService.fetchDirForUpdate(id);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
        }
        if (body.getRowVersion() != null) {
            int cv = ((Number) existing.get(0).get("row_version")).intValue();
            if (body.getRowVersion() != cv) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia");
            }
        }
        String et = (String) existing.get(0).get("entidad_tipo");
        UUID ei = (UUID) existing.get(0).get("entidad_id");
        writeService.actualizarDireccion(id, body, et, ei, user.getUsername());
        return ResponseEntity.ok(queryService.getDirById(id));
    }

    @DeleteMapping("/direcciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarDir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int n = writeService.eliminarDireccion(id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
    }

    @PatchMapping("/direcciones/{id}/principal")
    public ResponseEntity<Map<String, Object>> setPrincipal(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> existing = queryService.fetchDirPrincipalRef(id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
        String et = (String) existing.get(0).get("entidad_tipo");
        UUID ei = (UUID) existing.get(0).get("entidad_id");
        writeService.setPrincipalDireccion(id, et, ei);
        return ResponseEntity.ok(queryService.getDirById(id));
    }

    // ── MEDIOS DE CONTACTO ────────────────────────────────────────────────────

    @GetMapping("/persona-contactos")
    public ResponseEntity<List<Map<String, Object>>> listarContactos(
            @RequestParam("persona_id") UUID personaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listarContactos(personaId));
    }

    @PostMapping("/persona-contactos")
    public ResponseEntity<Map<String, Object>> crearContacto(
            @RequestBody PersonaContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (body.getPersonaId() == null || body.getMedio() == null || body.getValor() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "persona_id, medio y valor son requeridos");
        }
        UUID id = writeService.crearContacto(body, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getContactoById(id));
    }

    @PatchMapping("/persona-contactos/{id}")
    public ResponseEntity<Map<String, Object>> actualizarContacto(
            @PathVariable UUID id,
            @RequestBody PersonaContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<Map<String, Object>> existing = queryService.fetchContactoForUpdate(id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
        if (body.getRowVersion() != null) {
            int cv = ((Number) existing.get(0).get("row_version")).intValue();
            if (body.getRowVersion() != cv) throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        }
        writeService.actualizarContacto(id, body, user.getUsername());
        return ResponseEntity.ok(queryService.getContactoById(id));
    }

    @DeleteMapping("/persona-contactos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarContacto(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int n = writeService.eliminarContacto(id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
    }

    // ── Payloads ──────────────────────────────────────────────────────────────

    @Data
    public static class DireccionPayload {
        private String entidadTipo;
        private UUID   entidadId;
        private String tipoDireccion;
        private Boolean esPrincipal;
        private String tipoVia;
        private String calle;
        private String numeroExterior;
        private String numeroInterior;
        private String entreCalles1;
        private String entreCalles2;
        private String referencia;
        private UUID   codigoPostalId;
        private UUID   localidadId;
        private BigDecimal latitud;
        private BigDecimal longitud;
        private String precisionGps;
        private Integer rowVersion;
    }

    @Data
    public static class PersonaContactoPayload {
        private UUID   personaId;
        private String medio;
        private String tipo;
        private String valor;
        private String etiqueta;
        private Boolean esPrincipal;
        private Integer orden;
        private String notas;
        private Integer rowVersion;
    }
}

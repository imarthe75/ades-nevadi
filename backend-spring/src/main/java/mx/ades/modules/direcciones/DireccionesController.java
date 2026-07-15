package mx.ades.modules.direcciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

/**
 * Adaptador REST para la gestión de direcciones y medios de contacto.
 * Expone endpoints bajo /api/v1 para dos dominios: catálogos SEPOMEX
 * (búsqueda por código postal, asentamientos, estados, municipios — sin autenticación)
 * y CRUD de direcciones (/api/v1/direcciones) y medios de contacto de persona
 * (/api/v1/persona-contactos) con soporte para múltiples tipos de dirección,
 * geolocalización GPS, optimistic locking (rowVersion) y marcado de dirección
 * principal. Las operaciones de escritura requieren JWT válido y el scoping
 * de entidad (entidad_tipo + entidad_id) garantiza integridad referencial.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DireccionesController {

    private final AdesUserService userService;
    private final DireccionesQueryService queryService;
    private final DireccionesWriteService writeService;

    /**
     * Direcciones y medios de contacto son PII (domicilio, GPS, teléfono, email) de
     * alumnos y personal. Solo personal escolar (nivelAcceso &le;4) puede crear,
     * modificar o eliminar estos registros — sin este chequeo cualquier cuenta
     * autenticada (incluyendo padres/alumnos) podía editar o borrar el domicilio de
     * cualquier entidad del sistema con solo conocer su UUID (BFLA, OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

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
            @RequestBody @Valid DireccionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
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
            @RequestBody @Valid DireccionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
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
        requireStaff(userService.resolveUser(jwt));
        int n = writeService.eliminarDireccion(id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
    }

    @PatchMapping("/direcciones/{id}/principal")
    public ResponseEntity<Map<String, Object>> setPrincipal(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
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

    // Espejo de los CHECK constraints chk_pc_medio / chk_pc_tipo de ades_persona_contactos
    // y de las opciones del LOV en domicilio.component.ts (MEDIOS / TIPOS_CONT). El frontend
    // ya restringe a estos valores vía p-select, pero sin este backstop server-side un
    // valor fuera del enum caía directo en el CHECK de BD -> 409 genérico "duplicado o
    // referencia inválida" en vez de un 422 claro.
    private static final Set<String> MEDIOS_VALIDOS =
            Set.of("CELULAR", "FIJO", "WHATSAPP", "EMAIL", "TELEGRAM", "FAX", "OTRO");
    private static final Set<String> TIPOS_CONTACTO_VALIDOS =
            Set.of("PERSONAL", "TRABAJO", "FAMILIAR", "INSTITUCIONAL", "EMERGENCIA");

    @PostMapping("/persona-contactos")
    public ResponseEntity<Map<String, Object>> crearContacto(
            @RequestBody @Valid PersonaContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        if (body.getPersonaId() == null || body.getMedio() == null || body.getValor() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "persona_id, medio y valor son requeridos");
        }
        validarMedioYTipo(body.getMedio(), body.getTipo());
        UUID id = writeService.crearContacto(body, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getContactoById(id));
    }

    @PatchMapping("/persona-contactos/{id}")
    public ResponseEntity<Map<String, Object>> actualizarContacto(
            @PathVariable UUID id,
            @RequestBody @Valid PersonaContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        List<Map<String, Object>> existing = queryService.fetchContactoForUpdate(id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
        if (body.getRowVersion() != null) {
            int cv = ((Number) existing.get(0).get("row_version")).intValue();
            if (body.getRowVersion() != cv) throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        }
        validarMedioYTipo(body.getMedio(), body.getTipo());
        writeService.actualizarContacto(id, body, user.getUsername());
        return ResponseEntity.ok(queryService.getContactoById(id));
    }

    private void validarMedioYTipo(String medio, String tipo) {
        if (medio != null && !MEDIOS_VALIDOS.contains(medio)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "medio inválido. Valores permitidos: " + MEDIOS_VALIDOS);
        }
        if (tipo != null && !TIPOS_CONTACTO_VALIDOS.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "tipo inválido. Valores permitidos: " + TIPOS_CONTACTO_VALIDOS);
        }
    }

    @DeleteMapping("/persona-contactos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarContacto(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        int n = writeService.eliminarContacto(id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
    }

    // ── Payloads ──────────────────────────────────────────────────────────────

    @Data
    public static class DireccionPayload {
        @NotBlank(message = "entidadTipo es obligatorio")
        private String entidadTipo;
        @NotNull(message = "entidadId es obligatorio")
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
        @NotNull(message = "personaId es obligatorio")
        private UUID   personaId;
        @NotBlank(message = "medio es obligatorio")
        private String medio;
        private String tipo;
        @NotBlank(message = "valor es obligatorio")
        @Size(max = 255, message = "valor máximo 255 caracteres")
        private String valor;
        private String etiqueta;
        private Boolean esPrincipal;
        private Integer orden;
        private String notas;
        private Integer rowVersion;
    }
}

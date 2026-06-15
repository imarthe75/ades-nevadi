package mx.ades.modules.direcciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    // ══════════════════════════════════════════════════════════════════════════
    // SEPOMEX — Búsqueda de asentamientos y códigos postales
    // Utiliza: ades_codigos_postales → ades_localidades → ades_municipios
    //                               → ades_estados → ades_paises
    //                               → ades_tipos_asentamiento
    // ══════════════════════════════════════════════════════════════════════════

    private static final String SEPOMEX_SELECT =
            "SELECT DISTINCT ON (cp.localidad_id) " +
            "  cp.id AS cp_id, l.id AS localidad_id, cp.codigo_postal, " +
            "  l.nombre_localidad, ta.nombre_tipo AS tipo_asentamiento, " +
            "  m.id AS municipio_id, m.nombre_municipio, " +
            "  e.id AS estado_id, e.nombre_estado, " +
            "  pa.nombre_pais " +
            "FROM ades_codigos_postales cp " +
            "JOIN ades_localidades l ON l.id = cp.localidad_id " +
            "JOIN ades_municipios m ON m.id = cp.municipio_id " +
            "JOIN ades_estados e ON e.id = cp.estado_id " +
            "JOIN ades_paises pa ON pa.id = e.pais_id " +
            "LEFT JOIN ades_tipos_asentamiento ta ON ta.id = cp.tipo_asentamiento_id " +
            "WHERE cp.is_active = TRUE ";

    @GetMapping("/catalogs/sepomex/por-cp")
    public ResponseEntity<List<Map<String, Object>>> porCp(@RequestParam("cp") String cp) {
        if (cp == null || !cp.matches("\\d{5}")) return ResponseEntity.ok(List.of());
        List<Map<String, Object>> rows = jdbc.queryForList(
                SEPOMEX_SELECT + "AND cp.codigo_postal = ? " +
                "ORDER BY cp.localidad_id, l.nombre_localidad",
                cp);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/catalogs/sepomex/buscar")
    public ResponseEntity<List<Map<String, Object>>> buscar(
            @RequestParam("q") String q,
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.ok(List.of());
        String term = "%" + q.trim() + "%";
        List<Map<String, Object>> rows = jdbc.queryForList(
                SEPOMEX_SELECT +
                "AND (l.nombre_localidad ILIKE ? OR cp.codigo_postal ILIKE ? OR m.nombre_municipio ILIKE ?) " +
                "ORDER BY cp.localidad_id, l.nombre_localidad LIMIT ?",
                term, term, term, limit);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/catalogs/tipos-asentamiento")
    public ResponseEntity<List<Map<String, Object>>> tiposAsentamiento() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, clave_tipo, nombre_tipo FROM ades_tipos_asentamiento " +
                "WHERE is_active = TRUE ORDER BY nombre_tipo"));
    }

    @GetMapping("/catalogs/estados-mexico")
    public ResponseEntity<List<Map<String, Object>>> estados() {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT e.id, e.clave_estado, e.nombre_estado, pa.nombre_pais " +
                "FROM ades_estados e JOIN ades_paises pa ON pa.id = e.pais_id " +
                "WHERE e.is_active = TRUE ORDER BY e.nombre_estado"));
    }

    @GetMapping("/catalogs/municipios")
    public ResponseEntity<List<Map<String, Object>>> municipios(
            @RequestParam(name = "estado_id", required = false) UUID estadoId) {
        if (estadoId != null) {
            return ResponseEntity.ok(jdbc.queryForList(
                    "SELECT id, clave_municipio, nombre_municipio FROM ades_municipios " +
                    "WHERE estado_id = ? AND is_active = TRUE ORDER BY nombre_municipio",
                    estadoId));
        }
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, clave_municipio, nombre_municipio FROM ades_municipios " +
                "WHERE is_active = TRUE ORDER BY nombre_municipio"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIRECCIONES CRUD
    // entidad_tipo = 'PERSONA' + entidad_id = persona_id para todos los actores
    // entidad_tipo = 'PLANTEL' + entidad_id = plantel_id para instalaciones
    // ══════════════════════════════════════════════════════════════════════════

    private static final String DIR_SELECT =
            "SELECT d.id, d.entidad_tipo, d.entidad_id, d.tipo_direccion, d.es_principal, " +
            "  d.tipo_via, d.calle, d.numero_exterior, d.numero_interior, " +
            "  d.entre_calle_1, d.entre_calle_2, d.referencia, " +
            "  d.codigo_postal_id, d.localidad_id, " +
            "  cp.codigo_postal, l.nombre_localidad, ta.nombre_tipo AS tipo_asentamiento, " +
            "  m.nombre_municipio, e.nombre_estado, pa.nombre_pais, " +
            "  d.latitud, d.longitud, d.precision_gps, d.row_version, d.fecha_creacion " +
            "FROM ades_direcciones d " +
            "LEFT JOIN ades_codigos_postales cp ON cp.id = d.codigo_postal_id " +
            "LEFT JOIN ades_localidades l ON l.id = d.localidad_id " +
            "LEFT JOIN ades_municipios m ON m.id = cp.municipio_id " +
            "LEFT JOIN ades_estados e ON e.id = cp.estado_id " +
            "LEFT JOIN ades_paises pa ON pa.id = e.pais_id " +
            "LEFT JOIN ades_tipos_asentamiento ta ON ta.id = cp.tipo_asentamiento_id ";

    @GetMapping("/direcciones")
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam("entidad_tipo") String entidadTipo,
            @RequestParam("entidad_id") UUID entidadId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> rows = jdbc.queryForList(
                DIR_SELECT + "WHERE d.entidad_tipo = ? AND d.entidad_id = ? AND d.is_active = TRUE " +
                "ORDER BY d.es_principal DESC, d.fecha_creacion",
                entidadTipo, entidadId);
        return ResponseEntity.ok(rows);
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

        if (Boolean.TRUE.equals(body.getEsPrincipal())) {
            jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE " +
                    "WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE",
                    body.getEntidadTipo(), body.getEntidadId());
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_direcciones " +
                "(id, entidad_tipo, entidad_id, tipo_direccion, es_principal, " +
                " tipo_via, calle, numero_exterior, numero_interior, " +
                " entre_calle_1, entre_calle_2, referencia, " +
                " codigo_postal_id, localidad_id, latitud, longitud, precision_gps, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                body.getEntidadTipo(), body.getEntidadId(),
                body.getTipoDireccion() != null ? body.getTipoDireccion() : "PRINCIPAL",
                body.getEsPrincipal() != null ? body.getEsPrincipal() : false,
                body.getTipoVia(), body.getCalle(), body.getNumeroExterior(), body.getNumeroInterior(),
                body.getEntreCalles1(), body.getEntreCalles2(), body.getReferencia(),
                body.getCodigoPostalId(), body.getLocalidadId(),
                body.getLatitud(), body.getLongitud(), body.getPrecisionGps(),
                user.getUsername(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(getDirById(id));
    }

    @PatchMapping("/direcciones/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody DireccionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT entidad_tipo, entidad_id, row_version FROM ades_direcciones " +
                "WHERE id = ? AND is_active = TRUE", id);
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

        if (Boolean.TRUE.equals(body.getEsPrincipal())) {
            jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE " +
                    "WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE AND id != ?",
                    et, ei, id);
        }

        StringBuilder sql = new StringBuilder(
                "UPDATE ades_direcciones SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(user.getUsername());

        addStr(sql, params, "tipo_direccion", body.getTipoDireccion());
        if (body.getEsPrincipal() != null) { sql.append(", es_principal = ?"); params.add(body.getEsPrincipal()); }
        addStr(sql, params, "tipo_via", body.getTipoVia());
        addStr(sql, params, "calle", body.getCalle());
        addStr(sql, params, "numero_exterior", body.getNumeroExterior());
        addStr(sql, params, "numero_interior", body.getNumeroInterior());
        addStr(sql, params, "entre_calle_1", body.getEntreCalles1());
        addStr(sql, params, "entre_calle_2", body.getEntreCalles2());
        addStr(sql, params, "referencia", body.getReferencia());
        if (body.getCodigoPostalId() != null) { sql.append(", codigo_postal_id = ?"); params.add(body.getCodigoPostalId()); }
        if (body.getLocalidadId() != null) { sql.append(", localidad_id = ?"); params.add(body.getLocalidadId()); }
        if (body.getLatitud() != null) { sql.append(", latitud = ?"); params.add(body.getLatitud()); }
        if (body.getLongitud() != null) { sql.append(", longitud = ?"); params.add(body.getLongitud()); }
        addStr(sql, params, "precision_gps", body.getPrecisionGps());

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());

        return ResponseEntity.ok(getDirById(id));
    }

    @DeleteMapping("/direcciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarDir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int n = jdbc.update("UPDATE ades_direcciones SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
    }

    @PatchMapping("/direcciones/{id}/principal")
    public ResponseEntity<Map<String, Object>> setPrincipal(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT entidad_tipo, entidad_id FROM ades_direcciones WHERE id = ? AND is_active = TRUE", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
        String et = (String) existing.get(0).get("entidad_tipo");
        UUID ei = (UUID) existing.get(0).get("entidad_id");
        jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE", et, ei);
        jdbc.update("UPDATE ades_direcciones SET es_principal = TRUE WHERE id = ?", id);
        return ResponseEntity.ok(getDirById(id));
    }

    private Map<String, Object> getDirById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(DIR_SELECT + "WHERE d.id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MEDIOS DE CONTACTO (teléfonos, emails, etc.)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/persona-contactos")
    public ResponseEntity<List<Map<String, Object>>> listarContactos(
            @RequestParam("persona_id") UUID personaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, persona_id, medio, tipo, valor, etiqueta, es_principal, " +
                "orden, verificado, notas, row_version " +
                "FROM ades_persona_contactos " +
                "WHERE persona_id = ? AND is_active = TRUE " +
                "ORDER BY es_principal DESC, medio, orden",
                personaId));
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
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_persona_contactos " +
                "(id, persona_id, medio, tipo, valor, etiqueta, es_principal, orden, notas, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, body.getPersonaId(), body.getMedio(),
                body.getTipo() != null ? body.getTipo() : "PERSONAL",
                body.getValor(), body.getEtiqueta(),
                body.getEsPrincipal() != null ? body.getEsPrincipal() : false,
                body.getOrden() != null ? body.getOrden() : 1,
                body.getNotas(), user.getUsername(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(getContactoById(id));
    }

    @PatchMapping("/persona-contactos/{id}")
    public ResponseEntity<Map<String, Object>> actualizarContacto(
            @PathVariable UUID id,
            @RequestBody PersonaContactoPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT row_version FROM ades_persona_contactos WHERE id = ? AND is_active = TRUE", id);
        if (existing.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
        if (body.getRowVersion() != null) {
            int cv = ((Number) existing.get(0).get("row_version")).intValue();
            if (body.getRowVersion() != cv) throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia");
        }

        StringBuilder sql = new StringBuilder(
                "UPDATE ades_persona_contactos SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(user.getUsername());

        addStr(sql, params, "medio", body.getMedio());
        addStr(sql, params, "tipo", body.getTipo());
        addStr(sql, params, "valor", body.getValor());
        addStr(sql, params, "etiqueta", body.getEtiqueta());
        if (body.getEsPrincipal() != null) { sql.append(", es_principal = ?"); params.add(body.getEsPrincipal()); }
        if (body.getOrden() != null) { sql.append(", orden = ?"); params.add(body.getOrden()); }
        addStr(sql, params, "notas", body.getNotas());

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());
        return ResponseEntity.ok(getContactoById(id));
    }

    @DeleteMapping("/persona-contactos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarContacto(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int n = jdbc.update("UPDATE ades_persona_contactos SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no encontrado");
    }

    private Map<String, Object> getContactoById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, persona_id, medio, tipo, valor, etiqueta, es_principal, orden, verificado, notas, row_version " +
                "FROM ades_persona_contactos WHERE id = ?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void addStr(StringBuilder sql, List<Object> params, String col, String val) {
        if (val != null) { sql.append(", ").append(col).append(" = ?"); params.add(val); }
    }

    // ── Payloads ─────────────────────────────────────────────────────────────

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

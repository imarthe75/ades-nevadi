package mx.ades.modules.portal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Endpoints de administración del portal de convocatorias.
 * Requiere JWT ADES (Authentik) — bajo /api/v1/ protegido por Spring Security.
 * Acceso: nivel_acceso <= 2 (admin/director).
 */
@RestController
@RequestMapping("/api/v1/portal/admin")
@RequiredArgsConstructor
@Slf4j
public class PortalAdminController {

    private final JdbcTemplate jdbc;
    private final AdesUserService userService;
    private final PortalStorageService storage;
    private final PortalEmailService emailService;

    private void requireAdmin(AdesUser u) {
        if (u.getNivelAcceso() == null || u.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere perfil de Administrador");
        }
    }

    // ─────────────────────────────────────────────────────────
    // CONVOCATORIAS — CRUD
    // ─────────────────────────────────────────────────────────

    @GetMapping("/convocatorias")
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String tipo,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder("""
            SELECT c.id, c.categoria, c.tipo, c.titulo,
                   c.fecha_inicio_postulacion, c.fecha_cierre_postulacion,
                   c.cupo_maximo, c.cupo_actual, c.is_published, c.is_active,
                   p.nombre_plantel,
                   (SELECT COUNT(*) FROM portal.postulaciones po WHERE po.convocatoria_id = c.id AND po.is_active = TRUE) AS total_postulaciones,
                   (SELECT COUNT(*) FROM portal.postulaciones po WHERE po.convocatoria_id = c.id AND po.estado = 'ENVIADA' AND po.is_active = TRUE) AS pendientes_revision
            FROM portal.convocatorias c
            LEFT JOIN ades_planteles p ON p.id = c.plantel_id
            WHERE c.is_active = TRUE
            """);
        List<Object> params = new ArrayList<>();
        if (categoria != null && !categoria.isBlank()) {
            sql.append("AND c.categoria = ?::portal.categoria_convocatoria ");
            params.add(categoria.toUpperCase());
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append("AND c.tipo = ?::portal.tipo_convocatoria ");
            params.add(tipo.toUpperCase());
        }
        if (plantelId != null) {
            sql.append("AND c.plantel_id = ? ");
            params.add(plantelId);
        }
        sql.append("ORDER BY c.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, 100));
        params.add(skip);
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @Data
    public static class ConvocatoriaRequest {
        private String categoria; // RECURSOS_HUMANOS | OFERTA_EDUCATIVA — inferida del tipo si omitida
        private String tipo;
        private String titulo;
        private String descripcion;
        private String requisitosGenerales;
        private UUID plantelId;
        private UUID nivelEducativoId;
        private String fechaInicioPostulacion;
        private String fechaCierrePostulacion;
        private Integer cupoMaximo;
        private String imagenUrl;
        private String avisoPrivacidadVersion;
        private List<RequisitoRequest> requisitos;
    }

    @Data
    public static class RequisitoRequest {
        private String nombre;
        private String descripcion;
        private boolean esObligatorio;
        private List<String> tiposMimePermitidos;
        private Integer tamanoMaximoMb;
        private Integer orden;
    }

    @PostMapping("/convocatorias")
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody ConvocatoriaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        // Inferir categoría si el frontend no la envía
        String categoriaResuelta = body.getCategoria() != null && !body.getCategoria().isBlank()
                ? body.getCategoria().toUpperCase()
                : jdbc.queryForObject(
                    "SELECT portal.inferir_categoria(?::portal.tipo_convocatoria)::TEXT",
                    String.class, body.getTipo().toUpperCase());

        UUID id = jdbc.queryForObject("""
            INSERT INTO portal.convocatorias
              (categoria, tipo, titulo, descripcion, requisitos_generales, plantel_id, nivel_educativo_id,
               fecha_inicio_postulacion, fecha_cierre_postulacion,
               cupo_maximo, imagen_url, aviso_privacidad_version,
               is_published, is_active, usuario_creacion)
            VALUES (?::portal.categoria_convocatoria,?::portal.tipo_convocatoria,?,?,?,?,?,
                    ?::TIMESTAMPTZ,?::TIMESTAMPTZ,
                    ?,?,?,
                    FALSE, TRUE, ?)
            RETURNING id
            """, UUID.class,
                categoriaResuelta,
                body.getTipo(), body.getTitulo(), body.getDescripcion(), body.getRequisitosGenerales(),
                body.getPlantelId(), body.getNivelEducativoId(),
                body.getFechaInicioPostulacion(), body.getFechaCierrePostulacion(),
                body.getCupoMaximo(), body.getImagenUrl(),
                body.getAvisoPrivacidadVersion() != null ? body.getAvisoPrivacidadVersion() : "1.0",
                user.getUsername());

        if (body.getRequisitos() != null) {
            insertarRequisitos(id, body.getRequisitos(), user.getUsername());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/convocatorias/{id}")
    public ResponseEntity<Void> actualizar(
            @PathVariable UUID id,
            @RequestBody ConvocatoriaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        String categoriaActualizada = body.getCategoria() != null && !body.getCategoria().isBlank()
                ? body.getCategoria().toUpperCase()
                : jdbc.queryForObject(
                    "SELECT portal.inferir_categoria(?::portal.tipo_convocatoria)::TEXT",
                    String.class, body.getTipo().toUpperCase());

        int updated = jdbc.update("""
            UPDATE portal.convocatorias SET
              categoria = ?::portal.categoria_convocatoria,
              tipo = ?::portal.tipo_convocatoria, titulo = ?, descripcion = ?,
              requisitos_generales = ?, plantel_id = ?, nivel_educativo_id = ?,
              fecha_inicio_postulacion = ?::TIMESTAMPTZ, fecha_cierre_postulacion = ?::TIMESTAMPTZ,
              cupo_maximo = ?, imagen_url = ?, usuario_modificacion = ?
            WHERE id = ? AND is_active = TRUE
            """,
                categoriaActualizada,
                body.getTipo(), body.getTitulo(), body.getDescripcion(),
                body.getRequisitosGenerales(), body.getPlantelId(), body.getNivelEducativoId(),
                body.getFechaInicioPostulacion(), body.getFechaCierrePostulacion(),
                body.getCupoMaximo(), body.getImagenUrl(), user.getUsername(), id);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convocatoria no encontrada");

        if (body.getRequisitos() != null) {
            jdbc.update("UPDATE portal.requisitos_documentos SET is_active = FALSE WHERE convocatoria_id = ?", id);
            insertarRequisitos(id, body.getRequisitos(), user.getUsername());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/convocatorias/{id}/publicar")
    public ResponseEntity<Map<String, Object>> publicar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        int upd = jdbc.update("""
            UPDATE portal.convocatorias
            SET is_published = NOT is_published, fecha_publicacion = CASE WHEN NOT is_published THEN NOW() ELSE fecha_publicacion END,
                usuario_modificacion = ?
            WHERE id = ? AND is_active = TRUE
            RETURNING is_published
            """, user.getUsername(), id);
        if (upd == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/convocatorias/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        jdbc.update("UPDATE portal.convocatorias SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // POSTULACIONES
    // ─────────────────────────────────────────────────────────

    @GetMapping("/postulaciones")
    public ResponseEntity<List<Map<String, Object>>> listarPostulaciones(
            @RequestParam(name = "convocatoria_id", required = false) UUID convocatoriaId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder("""
            SELECT po.id, po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   u.nombre_completo, u.email, u.telefono,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   (SELECT COUNT(*) FROM portal.documentos d WHERE d.postulacion_id = po.id AND d.is_active = TRUE) AS total_documentos
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.is_active = TRUE
            """);
        List<Object> params = new ArrayList<>();
        if (convocatoriaId != null) { sql.append("AND po.convocatoria_id = ? "); params.add(convocatoriaId); }
        if (estado != null && !estado.isBlank()) { sql.append("AND po.estado = ?::portal.estado_postulacion "); params.add(estado); }
        if (q != null && !q.isBlank()) {
            sql.append("AND (u.nombre_completo ILIKE ? OR u.email ILIKE ? OR po.folio ILIKE ?) ");
            String like = "%" + q + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append("ORDER BY po.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(Math.min(limit, 200)); params.add(skip);
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/postulaciones/{id}")
    public ResponseEntity<Map<String, Object>> detallePostulacion(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.datos_adicionales,
                   po.consentimiento_privacidad, po.ip_postulacion, po.fecha_envio,
                   po.fecha_creacion, po.observaciones_admin,
                   u.nombre_completo, u.email, u.telefono, u.fecha_nacimiento,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ?
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        Map<String, Object> post = new HashMap<>(rows.get(0));

        // Documentos con presigned URLs
        List<Map<String, Object>> docs = jdbc.queryForList("""
            SELECT d.id, d.tipo_documento, d.nombre_original, d.mimetype,
                   d.tamano_bytes, d.ruta_minio, d.fecha_creacion,
                   rd.nombre AS requisito_nombre, rd.es_obligatorio
            FROM portal.documentos d
            LEFT JOIN portal.requisitos_documentos rd ON rd.id = d.requisito_id
            WHERE d.postulacion_id = ? AND d.is_active = TRUE
            ORDER BY d.fecha_creacion
            """, id);

        List<Map<String, Object>> docsConUrl = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            Map<String, Object> d = new HashMap<>(doc);
            try { d.put("url_descarga", storage.presignedUrl((String) doc.get("ruta_minio"))); }
            catch (Exception e) { d.put("url_descarga", null); }
            docsConUrl.add(d);
        }
        post.put("documentos", docsConUrl);
        return ResponseEntity.ok(post);
    }

    @Data
    public static class CambioEstadoRequest {
        private String estado;
        private String observaciones;
    }

    @PatchMapping("/postulaciones/{id}/estado")
    public ResponseEntity<Map<String, Object>> cambiarEstado(
            @PathVariable UUID id,
            @RequestBody CambioEstadoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT po.folio, po.estado, u.email, u.nombre_completo
            FROM portal.postulaciones po JOIN portal.usuarios u ON u.id = po.usuario_id
            WHERE po.id = ? AND po.is_active = TRUE
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        jdbc.update("""
            UPDATE portal.postulaciones
            SET estado = ?::portal.estado_postulacion,
                observaciones_admin = COALESCE(?, observaciones_admin),
                usuario_modificacion = ?
            WHERE id = ?
            """, body.getEstado().toUpperCase(), body.getObservaciones(), user.getUsername(), id);

        emailService.enviarCambioEstado(
                (String) rows.get(0).get("email"),
                (String) rows.get(0).get("nombre_completo"),
                (String) rows.get(0).get("folio"),
                body.getEstado(), body.getObservaciones());

        return ResponseEntity.ok(Map.of("ok", true, "nuevo_estado", body.getEstado()));
    }

    @GetMapping("/postulaciones/exportar")
    public ResponseEntity<List<Map<String, Object>>> exportar(
            @RequestParam(name = "convocatoria_id", required = false) UUID convocatoriaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT po.folio, po.estado, po.fecha_envio,
                   u.nombre_completo, u.email, u.telefono,
                   c.titulo AS convocatoria, c.tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.is_active = TRUE
              AND (? IS NULL OR po.convocatoria_id = ?)
            ORDER BY c.titulo, po.estado, u.nombre_completo
            """, convocatoriaId, convocatoriaId));
    }

    // ─────────────────────────────────────────────────────────
    // SOLICITUDES ARCO
    // ─────────────────────────────────────────────────────────

    @GetMapping("/arco")
    public ResponseEntity<List<Map<String, Object>>> listarArco(
            @RequestParam(required = false) String estado,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder("""
            SELECT id, email_solicitante, nombre_solicitante, tipo, estado,
                   descripcion, fecha_limite_respuesta, fecha_resolucion, fecha_creacion
            FROM portal.solicitudes_arco
            """);
        List<Object> params = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append("WHERE estado = ?::portal.estado_solicitud_arco ");
            params.add(estado);
        }
        sql.append("ORDER BY fecha_creacion DESC");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @Data
    public static class RespuestaArcoRequest {
        private String estado;
        private String respuesta;
    }

    @PatchMapping("/arco/{id}")
    public ResponseEntity<Void> responderArco(
            @PathVariable UUID id,
            @RequestBody RespuestaArcoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        jdbc.update("""
            UPDATE portal.solicitudes_arco
            SET estado = ?::portal.estado_solicitud_arco,
                respuesta_admin = ?,
                fecha_resolucion = CASE WHEN ? IN ('RESUELTA','IMPROCEDENTE') THEN NOW() ELSE fecha_resolucion END,
                usuario_modificacion = ?
            WHERE id = ?
            """, body.getEstado(), body.getRespuesta(), body.getEstado(), user.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // ESTADÍSTICAS
    // ─────────────────────────────────────────────────────────

    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> estadisticas(@AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        return ResponseEntity.ok(Map.of(
                "convocatorias_activas", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM portal.convocatorias WHERE is_published=TRUE AND is_active=TRUE AND fecha_cierre_postulacion >= NOW()", Long.class),
                "total_postulaciones", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM portal.postulaciones WHERE is_active=TRUE", Long.class),
                "pendientes_revision", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM portal.postulaciones WHERE estado='ENVIADA' AND is_active=TRUE", Long.class),
                "usuarios_registrados", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM portal.usuarios WHERE is_active=TRUE", Long.class),
                "solicitudes_arco_pendientes", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM portal.solicitudes_arco WHERE estado IN ('RECIBIDA','EN_PROCESO')", Long.class),
                "por_categoria", jdbc.queryForList("""
                    SELECT c.categoria, COUNT(DISTINCT c.id) AS convocatorias,
                           COUNT(po.id) AS postulaciones
                    FROM portal.convocatorias c
                    LEFT JOIN portal.postulaciones po ON po.convocatoria_id = c.id AND po.is_active = TRUE
                    WHERE c.is_active = TRUE
                    GROUP BY c.categoria ORDER BY c.categoria
                    """),
                "por_tipo", jdbc.queryForList("""
                    SELECT c.categoria, c.tipo, COUNT(po.id) AS postulaciones
                    FROM portal.convocatorias c
                    LEFT JOIN portal.postulaciones po ON po.convocatoria_id = c.id AND po.is_active = TRUE
                    WHERE c.is_active = TRUE
                    GROUP BY c.categoria, c.tipo ORDER BY c.categoria, postulaciones DESC
                    """)
        ));
    }

    // ─────────────────────────────────────────────────────────
    // SECCIONES DE CONTENIDO LMS
    // ─────────────────────────────────────────────────────────

    @Data
    public static class SeccionRequest {
        private String tipoSeccion;
        private String titulo;
        private String contenido;
        private Object datos;   // JSONB — Object es serializable por Jackson
        private Integer orden;
    }

    @Data
    public static class ReordenarRequest {
        private List<UUID> ids; // orden deseado: ids[0] = orden 0, ids[1] = orden 1, ...
    }

    @GetMapping("/convocatorias/{convId}/secciones")
    public ResponseEntity<List<Map<String, Object>>> listarSecciones(
            @PathVariable UUID convId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt); // al menos autenticado
        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT id, tipo_seccion, titulo, contenido, datos, orden, is_active,
                   fecha_creacion, fecha_modificacion, usuario_creacion
            FROM portal.secciones_convocatoria
            WHERE convocatoria_id = ? AND is_active = TRUE
            ORDER BY orden, fecha_creacion
            """, convId));
    }

    @PostMapping("/convocatorias/{convId}/secciones")
    public ResponseEntity<Map<String, Object>> crearSeccion(
            @PathVariable UUID convId,
            @RequestBody SeccionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        verifyConvocatoriaExists(convId);

        String datosJson = body.getDatos() != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body.getDatos()).toString()
                : null;

        UUID id = jdbc.queryForObject("""
            INSERT INTO portal.secciones_convocatoria
              (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
            VALUES (?, ?::portal.tipo_seccion, ?, ?, ?::JSONB,
                    COALESCE(?, (SELECT COALESCE(MAX(orden),0)+1 FROM portal.secciones_convocatoria
                                  WHERE convocatoria_id = ?)), ?)
            RETURNING id
            """, UUID.class,
                convId, body.getTipoSeccion().toUpperCase(), body.getTitulo(),
                body.getContenido(), datosJson,
                body.getOrden(), convId,
                user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/convocatorias/{convId}/secciones/{seccionId}")
    public ResponseEntity<Void> actualizarSeccion(
            @PathVariable UUID convId,
            @PathVariable UUID seccionId,
            @RequestBody SeccionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        String datosJson = body.getDatos() != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body.getDatos()).toString()
                : null;

        int upd = jdbc.update("""
            UPDATE portal.secciones_convocatoria
            SET tipo_seccion = ?::portal.tipo_seccion,
                titulo = ?,
                contenido = ?,
                datos = ?::JSONB,
                orden = COALESCE(?, orden),
                usuario_modificacion = ?
            WHERE id = ? AND convocatoria_id = ? AND is_active = TRUE
            """,
                body.getTipoSeccion().toUpperCase(), body.getTitulo(),
                body.getContenido(), datosJson,
                body.getOrden(), user.getUsername(),
                seccionId, convId);

        if (upd == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sección no encontrada");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/convocatorias/{convId}/secciones/reordenar")
    public ResponseEntity<Void> reordenarSecciones(
            @PathVariable UUID convId,
            @RequestBody ReordenarRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        if (body.getIds() == null || body.getIds().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids requeridos");

        for (int i = 0; i < body.getIds().size(); i++) {
            jdbc.update("""
                UPDATE portal.secciones_convocatoria
                SET orden = ?, usuario_modificacion = ?
                WHERE id = ? AND convocatoria_id = ?
                """, i, user.getUsername(), body.getIds().get(i), convId);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/convocatorias/{convId}/secciones/{seccionId}")
    public ResponseEntity<Void> eliminarSeccion(
            @PathVariable UUID convId,
            @PathVariable UUID seccionId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        jdbc.update("""
            UPDATE portal.secciones_convocatoria
            SET is_active = FALSE, usuario_modificacion = ?
            WHERE id = ? AND convocatoria_id = ?
            """, user.getUsername(), seccionId, convId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private void verifyConvocatoriaExists(UUID convId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM portal.convocatorias WHERE id = ? AND is_active = TRUE",
                Integer.class, convId);
        if (count == null || count == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convocatoria no encontrada");
    }

    private void insertarRequisitos(UUID convId, List<RequisitoRequest> reqs, String usuario) {
        for (RequisitoRequest r : reqs) {
            String mimes = r.getTiposMimePermitidos() != null
                    ? "{" + String.join(",", r.getTiposMimePermitidos()) + "}"
                    : "{application/pdf}";
            jdbc.update("""
                INSERT INTO portal.requisitos_documentos
                  (convocatoria_id, nombre, descripcion, es_obligatorio,
                   tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
                VALUES (?,?,?,?,?::TEXT[],?,?,?)
                """,
                    convId, r.getNombre(), r.getDescripcion(), r.isEsObligatorio(),
                    mimes, r.getTamanoMaximoMb() != null ? r.getTamanoMaximoMb() : 5,
                    r.getOrden() != null ? r.getOrden() : 0, usuario);
        }
    }
}

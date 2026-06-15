package mx.ades.modules.portal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Endpoints para postulantes autenticados del portal externo.
 * JWT portal validado manualmente (no pasa por Spring Security OAuth2).
 */
@RestController
@RequestMapping("/api/portal/usuario")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"https://portalnvd.setag.mx", "http://localhost:4201"})
public class PortalUsuarioController {

    private final JdbcTemplate jdbc;
    private final PortalJwtService jwtService;
    private final PortalStorageService storage;
    private final PortalEmailService emailService;

    // ─────────────────────────────────────────────────────────
    // PERFIL
    // ─────────────────────────────────────────────────────────

    @GetMapping("/perfil")
    public ResponseEntity<Map<String, Object>> perfil(HttpServletRequest req) {
        UUID uid = jwtService.resolverUsuarioId(req);
        return ResponseEntity.ok(jdbc.queryForMap("""
            SELECT id, email, nombre_completo, telefono, fecha_nacimiento,
                   is_email_verified, consentimiento_privacidad, consentimiento_version,
                   fecha_ultimo_acceso, fecha_creacion
            FROM portal.usuarios WHERE id = ?
            """, uid));
    }

    @Data
    public static class PerfilRequest {
        private String nombreCompleto;
        private String telefono;
    }

    @PutMapping("/perfil")
    public ResponseEntity<Map<String, Object>> actualizarPerfil(
            HttpServletRequest req, @RequestBody PerfilRequest body) {
        UUID uid = jwtService.resolverUsuarioId(req);
        jdbc.update("""
            UPDATE portal.usuarios
            SET nombre_completo = COALESCE(?, nombre_completo),
                telefono = COALESCE(?, telefono),
                usuario_modificacion = 'portal-usuario'
            WHERE id = ?
            """, body.getNombreCompleto(), body.getTelefono(), uid);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─────────────────────────────────────────────────────────
    // POSTULACIONES
    // ─────────────────────────────────────────────────────────

    @GetMapping("/postulaciones")
    public ResponseEntity<List<Map<String, Object>>> misPostulaciones(HttpServletRequest req) {
        UUID uid = jwtService.resolverUsuarioId(req);
        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.fecha_envio, po.fecha_creacion,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   c.fecha_cierre_postulacion,
                   (SELECT COUNT(*) FROM portal.documentos d WHERE d.postulacion_id = po.id AND d.is_active = TRUE) AS total_documentos
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.usuario_id = ? AND po.is_active = TRUE
            ORDER BY po.fecha_creacion DESC
            """, uid));
    }

    @Data
    public static class NuevaPostulacionRequest {
        private UUID convocatoriaId;
        private boolean consentimientoPrivacidad;
        private Map<String, Object> datosAdicionales;
    }

    @PostMapping("/postulaciones")
    public ResponseEntity<Map<String, Object>> crearPostulacion(
            HttpServletRequest req, @RequestBody NuevaPostulacionRequest body) {
        UUID uid = jwtService.resolverUsuarioId(req);

        if (body.getConvocatoriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "convocatoria_id es requerido");
        }
        if (!body.isConsentimientoPrivacidad()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes aceptar el Aviso de Privacidad para postular");
        }

        // Verificar convocatoria abierta
        List<Map<String, Object>> conv = jdbc.queryForList("""
            SELECT id, tipo, titulo, cupo_maximo, cupo_actual, fecha_cierre_postulacion
            FROM portal.convocatorias
            WHERE id = ? AND is_published = TRUE AND is_active = TRUE
              AND fecha_inicio_postulacion <= NOW() AND fecha_cierre_postulacion >= NOW()
            """, body.getConvocatoriaId());
        if (conv.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La convocatoria no está disponible o ha cerrado");
        }
        Map<String, Object> c = conv.get(0);
        Integer cupoMax = (Integer) c.get("cupo_maximo");
        Integer cupoActual = (Integer) c.get("cupo_actual");
        if (cupoMax != null && cupoActual >= cupoMax) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El cupo de esta convocatoria está lleno");
        }

        // Verificar duplicado
        boolean yaPostulo = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM portal.postulaciones WHERE convocatoria_id = ? AND usuario_id = ? AND is_active = TRUE)",
                Boolean.class, body.getConvocatoriaId(), uid));
        if (yaPostulo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una postulación para esta convocatoria");
        }

        // Generar folio
        String folio = jdbc.queryForObject(
                "SELECT portal.generar_folio(?::portal.tipo_convocatoria)", String.class,
                c.get("tipo").toString());

        String ip = req.getRemoteAddr();
        String datosJson = body.getDatosAdicionales() != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body.getDatosAdicionales()).toString()
                : null;

        UUID postId = jdbc.queryForObject("""
            INSERT INTO portal.postulaciones
              (convocatoria_id, usuario_id, folio, estado, datos_adicionales,
               consentimiento_privacidad, ip_postulacion, usuario_creacion)
            VALUES (?,?,?,'BORRADOR', ?::JSONB, TRUE,?::INET,'portal-usuario')
            RETURNING id
            """, UUID.class,
                body.getConvocatoriaId(), uid, folio, datosJson, ip);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", postId, "folio", folio, "estado", "BORRADOR"));
    }

    @GetMapping("/postulaciones/{id}")
    public ResponseEntity<Map<String, Object>> detallePostulacion(
            HttpServletRequest req, @PathVariable UUID id) {
        UUID uid = jwtService.resolverUsuarioId(req);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT po.id, po.folio, po.estado, po.datos_adicionales, po.fecha_envio,
                   po.fecha_creacion, po.consentimiento_privacidad,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo,
                   c.fecha_cierre_postulacion, c.requisitos_generales
            FROM portal.postulaciones po
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ? AND po.usuario_id = ? AND po.is_active = TRUE
            """, id, uid);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");

        Map<String, Object> post = new HashMap<>(rows.get(0));
        post.put("documentos", jdbc.queryForList("""
            SELECT d.id, d.tipo_documento, d.nombre_original, d.mimetype,
                   d.tamano_bytes, d.fecha_creacion,
                   rd.nombre AS requisito_nombre, rd.es_obligatorio
            FROM portal.documentos d
            LEFT JOIN portal.requisitos_documentos rd ON rd.id = d.requisito_id
            WHERE d.postulacion_id = ? AND d.is_active = TRUE
            ORDER BY d.fecha_creacion
            """, id));
        post.put("requisitos", jdbc.queryForList("""
            SELECT rd.id, rd.nombre, rd.descripcion, rd.es_obligatorio,
                   rd.tipos_mime_permitidos, rd.tamano_maximo_mb, rd.orden,
                   EXISTS(
                     SELECT 1 FROM portal.documentos d
                     WHERE d.postulacion_id = ? AND d.requisito_id = rd.id AND d.is_active = TRUE
                   ) AS cumplido
            FROM portal.requisitos_documentos rd
            JOIN portal.convocatorias c ON c.id = rd.convocatoria_id
            JOIN portal.postulaciones po ON po.id = ?
            WHERE po.convocatoria_id = c.id AND rd.is_active = TRUE
            ORDER BY rd.orden, rd.nombre
            """, id, id));
        return ResponseEntity.ok(post);
    }

    // ─────────────────────────────────────────────────────────
    // DOCUMENTOS
    // ─────────────────────────────────────────────────────────

    @PostMapping("/postulaciones/{id}/documentos")
    public ResponseEntity<Map<String, Object>> subirDocumento(
            HttpServletRequest req,
            @PathVariable UUID id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("tipo_documento") String tipoDocumento,
            @RequestParam(value = "requisito_id", required = false) UUID requisitoId) {

        UUID uid = jwtService.resolverUsuarioId(req);

        // Verificar que la postulación es del usuario y está en BORRADOR
        String estado = jdbc.queryForObject(
                "SELECT estado::TEXT FROM portal.postulaciones WHERE id = ? AND usuario_id = ? AND is_active = TRUE",
                String.class, id, uid);
        if (estado == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        if (!"BORRADOR".equals(estado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Solo se pueden subir documentos a postulaciones en estado BORRADOR");

        PortalStorageService.UploadResult result = storage.subir(id, archivo);

        UUID docId = jdbc.queryForObject("""
            INSERT INTO portal.documentos
              (postulacion_id, requisito_id, tipo_documento, nombre_original,
               mimetype, tamano_bytes, ruta_minio, hash_sha256, usuario_creacion)
            VALUES (?,?,?,?,?,?,?,?,'portal-usuario')
            RETURNING id
            """, UUID.class,
                id, requisitoId, tipoDocumento.trim(), result.nombreOriginal(),
                result.mime(), result.tamanoBytes(), result.ruta(), result.sha256());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", docId,
                "nombre_original", result.nombreOriginal(),
                "tamano_bytes", result.tamanoBytes()));
    }

    @DeleteMapping("/postulaciones/{id}/documentos/{docId}")
    public ResponseEntity<Void> eliminarDocumento(
            HttpServletRequest req, @PathVariable UUID id, @PathVariable UUID docId) {
        UUID uid = jwtService.resolverUsuarioId(req);

        // Verificar BORRADOR
        String estado = jdbc.queryForObject(
                "SELECT estado::TEXT FROM portal.postulaciones WHERE id = ? AND usuario_id = ?",
                String.class, id, uid);
        if (!"BORRADOR".equals(estado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se pueden eliminar documentos de una postulación enviada");

        String ruta = jdbc.queryForObject(
                "SELECT ruta_minio FROM portal.documentos WHERE id = ? AND postulacion_id = ? AND is_active = TRUE",
                String.class, docId, id);
        if (ruta == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado");

        jdbc.update("UPDATE portal.documentos SET is_active = FALSE, usuario_modificacion = 'portal-usuario' WHERE id = ?", docId);
        storage.eliminar(ruta);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/postulaciones/{id}/documentos/{docId}/url")
    public ResponseEntity<Map<String, Object>> urlDocumento(
            HttpServletRequest req, @PathVariable UUID id, @PathVariable UUID docId) {
        UUID uid = jwtService.resolverUsuarioId(req);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT d.ruta_minio, d.nombre_original
            FROM portal.documentos d
            JOIN portal.postulaciones po ON po.id = d.postulacion_id
            WHERE d.id = ? AND d.postulacion_id = ? AND po.usuario_id = ? AND d.is_active = TRUE
            """, docId, id, uid);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado");

        String url = storage.presignedUrl((String) rows.get(0).get("ruta_minio"));
        return ResponseEntity.ok(Map.of(
                "url", url,
                "expira_en_minutos", 15,
                "nombre_original", rows.get(0).get("nombre_original")));
    }

    // ─────────────────────────────────────────────────────────
    // ENVIAR POSTULACIÓN (BORRADOR → ENVIADA)
    // ─────────────────────────────────────────────────────────

    @PostMapping("/postulaciones/{id}/enviar")
    public ResponseEntity<Map<String, Object>> enviarPostulacion(
            HttpServletRequest req, @PathVariable UUID id) {
        UUID uid = jwtService.resolverUsuarioId(req);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT po.estado, po.folio, po.convocatoria_id,
                   u.email, u.nombre_completo,
                   c.titulo AS convocatoria_titulo, c.tipo AS convocatoria_tipo
            FROM portal.postulaciones po
            JOIN portal.usuarios u ON u.id = po.usuario_id
            JOIN portal.convocatorias c ON c.id = po.convocatoria_id
            WHERE po.id = ? AND po.usuario_id = ? AND po.is_active = TRUE
            """, id, uid);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        Map<String, Object> p = rows.get(0);

        if (!"BORRADOR".equals(p.get("estado"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La postulación ya fue enviada");
        }

        // Verificar documentos obligatorios
        List<Map<String, Object>> faltantes = jdbc.queryForList("""
            SELECT rd.nombre
            FROM portal.requisitos_documentos rd
            WHERE rd.convocatoria_id = ?
              AND rd.es_obligatorio = TRUE AND rd.is_active = TRUE
              AND NOT EXISTS (
                SELECT 1 FROM portal.documentos d
                WHERE d.postulacion_id = ? AND d.requisito_id = rd.id AND d.is_active = TRUE
              )
            """, p.get("convocatoria_id"), id);
        if (!faltantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan documentos obligatorios: " +
                            faltantes.stream().map(r -> r.get("nombre").toString()).reduce((a, b) -> a + ", " + b).orElse(""));
        }

        jdbc.update("""
            UPDATE portal.postulaciones
            SET estado = 'ENVIADA', fecha_envio = NOW(), usuario_modificacion = 'portal-usuario'
            WHERE id = ?
            """, id);

        // Incrementar cupo
        jdbc.update("UPDATE portal.convocatorias SET cupo_actual = cupo_actual + 1 WHERE id = ?",
                p.get("convocatoria_id"));

        emailService.enviarConfirmacionPostulacion(
                (String) p.get("email"),
                (String) p.get("nombre_completo"),
                (String) p.get("folio"),
                (String) p.get("convocatoria_titulo"));

        return ResponseEntity.ok(Map.of(
                "mensaje", "Postulación enviada correctamente",
                "folio", p.get("folio"),
                "estado", "ENVIADA"));
    }
}

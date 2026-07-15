package mx.ades.modules.portal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    private final PortalUsuarioService portalSvc;
    private final PortalJwtService jwtService;
    private final PortalStorageService storage;
    private final PortalEmailService emailService;

    // ─────────────────────────────────────────────────────────
    // PERFIL
    // ─────────────────────────────────────────────────────────

    @GetMapping("/perfil")
    public ResponseEntity<Map<String, Object>> perfil(HttpServletRequest req) {
        UUID uid = jwtService.resolverUsuarioId(req);
        return ResponseEntity.ok(portalSvc.perfil(uid));
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
        portalSvc.actualizarPerfil(uid, body.getNombreCompleto(), body.getTelefono());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─────────────────────────────────────────────────────────
    // POSTULACIONES
    // ─────────────────────────────────────────────────────────

    @GetMapping("/postulaciones")
    public ResponseEntity<List<Map<String, Object>>> misPostulaciones(HttpServletRequest req) {
        UUID uid = jwtService.resolverUsuarioId(req);
        return ResponseEntity.ok(portalSvc.misPostulaciones(uid));
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
        List<Map<String, Object>> conv = portalSvc.fetchConvocatoriaAbierta(body.getConvocatoriaId());
        if (conv.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La convocatoria no está disponible o ha cerrado");
        }
        Map<String, Object> c = conv.get(0);
        Integer cupoMax = (Integer) c.get("cupo_maximo");
        Integer cupoActual = (Integer) c.get("cupo_actual");
        if (cupoMax != null && cupoActual >= cupoMax) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El cupo de esta convocatoria está lleno");
        }
        if (portalSvc.yaPostulo(body.getConvocatoriaId(), uid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una postulación para esta convocatoria");
        }
        String folio = portalSvc.generarFolio(c.get("tipo").toString());
        String ip = req.getRemoteAddr();
        String datosJson = body.getDatosAdicionales() != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body.getDatosAdicionales()).toString()
                : null;
        UUID postId = portalSvc.insertarPostulacion(body.getConvocatoriaId(), uid, folio, datosJson, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", postId, "folio", folio, "estado", "BORRADOR"));
    }

    @GetMapping("/postulaciones/{id}")
    public ResponseEntity<Map<String, Object>> detallePostulacion(
            HttpServletRequest req, @PathVariable UUID id) {
        UUID uid = jwtService.resolverUsuarioId(req);
        List<Map<String, Object>> rows = portalSvc.detallePostulacion(id, uid);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        Map<String, Object> post = new HashMap<>(rows.get(0));
        post.put("documentos", portalSvc.documentosPostulacion(id));
        post.put("requisitos", portalSvc.requisitosPostulacion(id));
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
        String estado = portalSvc.fetchEstadoPostulacion(id, uid);
        if (estado == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        if (!"BORRADOR".equals(estado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Solo se pueden subir documentos a postulaciones en estado BORRADOR");

        PortalStorageService.UploadResult result = storage.subir(id, archivo);
        UUID docId = portalSvc.insertarDocumento(id, requisitoId, tipoDocumento.trim(),
                result.nombreOriginal(), result.mime(), result.tamanoBytes(), result.ruta(), result.sha256());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", docId,
                "nombre_original", result.nombreOriginal(),
                "tamano_bytes", result.tamanoBytes()));
    }

    @DeleteMapping("/postulaciones/{id}/documentos/{docId}")
    public ResponseEntity<Void> eliminarDocumento(
            HttpServletRequest req, @PathVariable UUID id, @PathVariable UUID docId) {
        UUID uid = jwtService.resolverUsuarioId(req);
        String estado = portalSvc.fetchEstadoPostulacion(id, uid);
        if (estado == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        if (!"BORRADOR".equals(estado)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se pueden eliminar documentos de una postulación enviada");
        String ruta = portalSvc.fetchRutaDocumento(docId, id);
        if (ruta == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado");
        portalSvc.softDeleteDocumento(docId);
        storage.eliminar(ruta);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/postulaciones/{id}/documentos/{docId}/url")
    public ResponseEntity<Map<String, Object>> urlDocumento(
            HttpServletRequest req, @PathVariable UUID id, @PathVariable UUID docId) {
        UUID uid = jwtService.resolverUsuarioId(req);
        List<Map<String, Object>> rows = portalSvc.fetchDocumentoUrl(docId, id, uid);
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
    @Transactional
    public ResponseEntity<Map<String, Object>> enviarPostulacion(
            HttpServletRequest req, @PathVariable UUID id) {
        UUID uid = jwtService.resolverUsuarioId(req);
        List<Map<String, Object>> rows = portalSvc.fetchPostulacionParaEnvio(id, uid);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada");
        Map<String, Object> p = rows.get(0);
        if (!"BORRADOR".equals(p.get("estado"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La postulación ya fue enviada");
        }
        List<Map<String, Object>> faltantes = portalSvc.fetchDocumentosObligatoriosFaltantes(
                (UUID) p.get("convocatoria_id"), id);
        if (!faltantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan documentos obligatorios: " +
                            faltantes.stream().map(r -> r.get("nombre").toString()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        portalSvc.marcarPostulacionEnviada(id);
        portalSvc.incrementarCupo(p.get("convocatoria_id"));
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

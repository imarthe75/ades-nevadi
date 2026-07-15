package mx.ades.modules.portal;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    private final AdesUserService userService;
    private final PortalAdminService adminSvc;
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
        return ResponseEntity.ok(adminSvc.listarConvocatorias(categoria, tipo, plantelId, limit, skip));
    }

    @Data
    public static class ConvocatoriaRequest {
        private String categoria;
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
        String categoria = body.getCategoria() != null && !body.getCategoria().isBlank()
                ? body.getCategoria().toUpperCase()
                : adminSvc.inferirCategoria(body.getTipo().toUpperCase());
        UUID id = adminSvc.crearConvocatoria(categoria, body.getTipo(), body.getTitulo(),
                body.getDescripcion(), body.getRequisitosGenerales(), body.getPlantelId(),
                body.getNivelEducativoId(), body.getFechaInicioPostulacion(),
                body.getFechaCierrePostulacion(), body.getCupoMaximo(), body.getImagenUrl(),
                body.getAvisoPrivacidadVersion() != null ? body.getAvisoPrivacidadVersion() : "1.0",
                user.getUsername());
        if (body.getRequisitos() != null) {
            adminSvc.insertarRequisitos(id, body.getRequisitos(), user.getUsername());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/convocatorias/{id}/requisitos")
    public ResponseEntity<List<Map<String, Object>>> listarRequisitos(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        // Asimétrico vs. el resto del CRUD de convocatorias (todos exigen requireAdmin):
        // faltaba aquí, dejando el detalle administrativo accesible a cualquier cuenta
        // ADES autenticada bajo la ruta /portal/admin/.
        requireAdmin(userService.resolveUser(jwt));
        return ResponseEntity.ok(adminSvc.listarRequisitos(id));
    }

    @PutMapping("/convocatorias/{id}")
    public ResponseEntity<Void> actualizar(
            @PathVariable UUID id,
            @RequestBody ConvocatoriaRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        String categoria = body.getCategoria() != null && !body.getCategoria().isBlank()
                ? body.getCategoria().toUpperCase()
                : adminSvc.inferirCategoria(body.getTipo().toUpperCase());
        int updated = adminSvc.actualizarConvocatoria(id, categoria, body.getTipo(), body.getTitulo(),
                body.getDescripcion(), body.getRequisitosGenerales(), body.getPlantelId(),
                body.getNivelEducativoId(), body.getFechaInicioPostulacion(),
                body.getFechaCierrePostulacion(), body.getCupoMaximo(), body.getImagenUrl(),
                user.getUsername());
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convocatoria no encontrada");
        if (body.getRequisitos() != null) {
            adminSvc.desactivarRequisitos(id);
            adminSvc.insertarRequisitos(id, body.getRequisitos(), user.getUsername());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/convocatorias/{id}/publicar")
    public ResponseEntity<Map<String, Object>> publicar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        int upd = adminSvc.togglePublicar(id, user.getUsername());
        if (upd == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(value = "/convocatorias/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirImagen(
            @PathVariable UUID id,
            @RequestParam("imagen") MultipartFile imagen,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        adminSvc.verifyConvocatoriaExists(id);
        String url = storage.subirImagenConvocatoria(id, imagen);
        adminSvc.actualizarImagenUrl(id, url, user.getUsername());
        log.info("Imagen de convocatoria {} actualizada: {}", id, url);
        return ResponseEntity.ok(Map.of("imagen_url", url));
    }

    @DeleteMapping("/convocatorias/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        adminSvc.eliminarConvocatoria(id, user.getUsername());
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
        return ResponseEntity.ok(adminSvc.listarPostulaciones(convocatoriaId, estado, q, limit, skip));
    }

    @GetMapping("/postulaciones/{id}")
    public ResponseEntity<Map<String, Object>> detallePostulacion(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        List<Map<String, Object>> rows = adminSvc.detallePostulacion(id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Map<String, Object> post = new HashMap<>(rows.get(0));
        List<Map<String, Object>> docs = adminSvc.documentosPostulacion(id);
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
        List<Map<String, Object>> rows = adminSvc.fetchPostulacionParaCambioEstado(id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        adminSvc.cambiarEstadoPostulacion(id, body.getEstado(), body.getObservaciones(), user.getUsername());
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
        return ResponseEntity.ok(adminSvc.exportarPostulaciones(convocatoriaId));
    }

    // ─────────────────────────────────────────────────────────
    // SOLICITUDES ARCO
    // ─────────────────────────────────────────────────────────

    @GetMapping("/arco")
    public ResponseEntity<List<Map<String, Object>>> listarArco(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        skip = Math.max(skip, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(adminSvc.listarArco(estado, skip, limit));
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
        adminSvc.responderArco(id, body.getEstado(), body.getRespuesta(), user.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // ESTADÍSTICAS
    // ─────────────────────────────────────────────────────────

    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> estadisticas(@AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        return ResponseEntity.ok(adminSvc.estadisticas());
    }

    // ─────────────────────────────────────────────────────────
    // SECCIONES DE CONTENIDO LMS
    // ─────────────────────────────────────────────────────────

    @Data
    public static class SeccionRequest {
        private String tipoSeccion;
        private String titulo;
        private String contenido;
        private Object datos;
        private Integer orden;
    }

    @Data
    public static class ReordenarRequest {
        private List<UUID> ids;
    }

    @GetMapping("/convocatorias/{convId}/secciones")
    public ResponseEntity<List<Map<String, Object>>> listarSecciones(
            @PathVariable UUID convId,
            @AuthenticationPrincipal Jwt jwt) {
        // Igual que listarRequisitos: asimétrico frente a crear/actualizar/reordenar/eliminar
        // secciones (todos con requireAdmin) — faltaba aquí.
        requireAdmin(userService.resolveUser(jwt));
        return ResponseEntity.ok(adminSvc.listarSecciones(convId));
    }

    @PostMapping("/convocatorias/{convId}/secciones")
    public ResponseEntity<Map<String, Object>> crearSeccion(
            @PathVariable UUID convId,
            @RequestBody SeccionRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        adminSvc.verifyConvocatoriaExists(convId);
        String datosJson = body.getDatos() != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body.getDatos()).toString()
                : null;
        UUID id = adminSvc.crearSeccion(convId, body.getTipoSeccion(), body.getTitulo(),
                body.getContenido(), datosJson, body.getOrden(), user.getUsername());
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
        int upd = adminSvc.actualizarSeccion(seccionId, convId, body.getTipoSeccion(), body.getTitulo(),
                body.getContenido(), datosJson, body.getOrden(), user.getUsername());
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
        adminSvc.reordenarSecciones(body.getIds(), convId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/convocatorias/{convId}/secciones/{seccionId}")
    public ResponseEntity<Void> eliminarSeccion(
            @PathVariable UUID convId,
            @PathVariable UUID seccionId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        adminSvc.eliminarSeccion(seccionId, convId, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}

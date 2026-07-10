package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST complementario para entregas de tareas: verificación de plagio,
 * feedback multimedia (audio/video) y descarga de archivos desde MinIO (SeaweedFS S3).
 * <p>
 * Los endpoints CRUD de entregas (listar por alumno/grupo, subir, calificar, excusa)
 * viven en {@link mx.ades.modules.gradebook.EntregasController} (versión hexagonal
 * actual) — NO duplicar esas rutas aquí: Spring lanza "Ambiguous handler methods"
 * en tiempo de request si dos controllers mapean el mismo path+verbo+firma bajo
 * /api/v1/entregas (bug real detectado y corregido 2026-07-03).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/entregas")
@RequiredArgsConstructor
public class TareaEntregaController {

    private final TareaEntregaService service;
    private final AdesUserService userService;

    @PostMapping("/{entrega_id}/plagio-check")
    public ResponseEntity<Map<String, Object>> runPlagioCheck(
            @PathVariable("entrega_id") UUID entregaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(service.checkPlagio(entregaId));
    }

    @PostMapping("/{entrega_id}/feedback-multimedia")
    public ResponseEntity<Map<String, Object>> subirFeedbackMultimedia(
            @PathVariable("entrega_id") UUID entregaId,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "video", required = false) MultipartFile video,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(service.subirFeedbackMultimedia(entregaId, audio, video));
    }

    @GetMapping("/media")
    public ResponseEntity<byte[]> getMedia(@RequestParam("url") String minioUrl) {
        byte[] bytes = service.descargarArchivo(minioUrl);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        
        String contentType = "application/octet-stream";
        if (minioUrl.endsWith(".mp3")) contentType = "audio/mpeg";
        else if (minioUrl.endsWith(".wav")) contentType = "audio/wav";
        else if (minioUrl.endsWith(".mp4")) contentType = "video/mp4";
        else if (minioUrl.endsWith(".webm")) contentType = "video/webm";
        else if (minioUrl.endsWith(".pdf")) contentType = "application/pdf";
        else if (minioUrl.endsWith(".png")) contentType = "image/png";
        else if (minioUrl.endsWith(".jpg") || minioUrl.endsWith(".jpeg")) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}

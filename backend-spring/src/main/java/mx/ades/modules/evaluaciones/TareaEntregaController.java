package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
    private final JdbcTemplate jdbc;

    @PostMapping("/{entrega_id}/plagio-check")
    public ResponseEntity<Map<String, Object>> runPlagioCheck(
            @PathVariable("entrega_id") UUID entregaId,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/BFLA fix (asimetría): esta acción de personal docente (correr detección de
        // plagio sobre la entrega de un alumno) solo llamaba resolveUser, sin verificar
        // asignación docente↔grupo — mismo criterio que
        // EntregasController#calificarEntrega/requireAccesoGrupo.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoIdDeEntrega(entregaId));
        return ResponseEntity.ok(service.checkPlagio(entregaId));
    }

    @PostMapping("/{entrega_id}/feedback-multimedia")
    public ResponseEntity<Map<String, Object>> subirFeedbackMultimedia(
            @PathVariable("entrega_id") UUID entregaId,
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "video", required = false) MultipartFile video,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/BFLA fix (asimetría): mismo hallazgo que runPlagioCheck — subir feedback de
        // audio/video a la entrega de un alumno es acción docente y requiere verificar
        // asignación docente↔grupo.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoIdDeEntrega(entregaId));
        return ResponseEntity.ok(service.subirFeedbackMultimedia(entregaId, audio, video));
    }

    @GetMapping("/media")
    public ResponseEntity<byte[]> getMedia(
            @RequestParam("url") String minioUrl,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (asimetría): este endpoint no llamaba resolveUser en absoluto (a diferencia
        // de sus hermanos de este mismo controller) y descarga archivos/feedback de audio-video
        // de la entrega de un alumno específico — cualquier portador de un JWT válido podía
        // descargar el feedback de CUALQUIER alumno con solo conocer/adivinar la URL de MinIO.
        // Se exige el mismo criterio dual que EntregasController#requireAccesoAlumno/
        // requireAccesoGrupo: personal escolar asignado al grupo, o el propio alumno/tutor.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoMedia(user, minioUrl);
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

    /** Resuelve el grupo_id de la tarea a la que pertenece una entrega — mismo criterio que
     * {@code EntregasController#grupoIdDeEntrega}. */
    private UUID grupoIdDeEntrega(UUID entregaId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT t.grupo_id FROM ades_tareas_entregas te JOIN ades_tareas t ON t.id = te.tarea_id " +
                "WHERE te.id = ? AND te.is_active = TRUE",
                UUID.class, entregaId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega no encontrada");
        return rows.get(0);
    }

    /**
     * Docentes (nivelAcceso 4) solo si están asignados al grupo de la tarea
     * (ades_asignaciones_docentes); admin/director/coordinador (nivelAcceso &le;3), alcance
     * institucional — mismo criterio que {@code EntregasController#requireAccesoGrupo}.
     */
    private void requireAccesoGrupo(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) return;
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_asignaciones_docentes ad " +
                "JOIN ades_profesores p ON p.id = ad.profesor_id " +
                "WHERE ad.grupo_id = ? AND p.persona_id = ? AND ad.is_active = TRUE",
                Long.class, grupoId, user.getPersonaId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No está asignado a este grupo");
        }
    }

    /**
     * Verifica acceso a un archivo de entrega identificado por su URL de MinIO (usado tanto
     * para el archivo original como para feedback de audio/video y el reporte de plagio):
     * personal escolar (nivelAcceso &le;4) solo si está asignado al grupo (o alcance
     * institucional); alumno/padre (nivelAcceso &gt;=5) solo si es tutor activo del alumno
     * dueño de la entrega — mismo criterio dual que {@code EntregasController}.
     */
    private void requireAccesoMedia(AdesUser user, String minioUrl) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT te.estudiante_id, t.grupo_id FROM ades_tareas_entregas te " +
                "JOIN ades_tareas t ON t.id = te.tarea_id " +
                "WHERE te.archivo_url = ? OR te.feedback_audio_url = ? " +
                "   OR te.feedback_video_url = ? OR te.plagio_reporte_url = ?",
                minioUrl, minioUrl, minioUrl, minioUrl);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado");
        UUID estudianteId = (UUID) rows.get(0).get("estudiante_id");
        UUID grupoId = (UUID) rows.get(0).get("grupo_id");

        Integer nivel = user.getNivelAcceso();
        if (nivel != null && nivel <= 4) {
            requireAccesoGrupo(user, grupoId);
            return;
        }
        String email = user.getEmail();
        Integer count = email == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE p.email_personal = ? AND ta.alumno_id = ? AND ta.is_active = TRUE",
                Integer.class, email, estudianteId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este archivo");
        }
    }
}

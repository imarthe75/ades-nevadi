package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.gradebook.query.ActividadesQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.*;

/**
 * Adaptador REST para la gestión de actividades del gradebook.
 * Expone endpoints bajo /api/v1/actividades para listar actividades por grupo/materia/período,
 * crear actividades (con generación automática de slots por alumno), consultar entregas
 * y calificar masivamente un grupo. Requiere JWT válido en todos los endpoints;
 * las mutaciones registran el usuario autor en el audit trail.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/actividades")
@RequiredArgsConstructor
public class ActividadesController {

    private final AdesUserService userService;
    private final ActividadesQueryService queryService;
    private final ActividadesWriteService writeService;

    @Data
    public static class ActividadIn {
        private String titulo;
        private String descripcion;
        private UUID grupoId;
        private UUID materiaId;
        private UUID periodoEvaluacionId;
        private String tipoItem = "tarea";
        private UUID temaId;
        private UUID planTrabajoId;
        private UUID rubricaId;
        private LocalDate fechaAsignacion;
        private LocalDate fechaEntrega;
        private LocalDate fechaExamen;
        private Double puntajeMaximo = 10.0;
        private String instruccionesUrl;
        private Boolean permiteEntregaTarde = false;
    }

    @Data
    public static class CalificarMasivoItem {
        private UUID alumnoId;
        private Double calificacion;
        private String comentario;
    }

    @GetMapping("/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> actividadesDeGrupo(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.actividadesDeGrupo(grupoId, materiaId, periodoId, tipoItem));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(
            @RequestBody ActividadIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        var result = writeService.crearActividad(
                body.getTitulo(), body.getDescripcion(), body.getGrupoId(), body.getMateriaId(),
                body.getPeriodoEvaluacionId(), body.getTipoItem(), body.getTemaId(), body.getPlanTrabajoId(),
                body.getRubricaId(), body.getFechaAsignacion(), body.getFechaEntrega(), body.getFechaExamen(),
                body.getPuntajeMaximo(), body.getInstruccionesUrl(), body.getPermiteEntregaTarde(),
                user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.tareaId().toString(),
                "slots_creados", result.slotsCreados(),
                "message", "Actividad creada y slots generados"
        ));
    }

    @GetMapping("/{actividadId}/entregas")
    public ResponseEntity<List<Map<String, Object>>> entregasDeActividad(
            @PathVariable("actividadId") UUID actividadId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.entregasDeActividad(actividadId));
    }

    @PatchMapping("/{actividadId}/calificar-masivo")
    public ResponseEntity<Map<String, Object>> calificarMasivo(
            @PathVariable("actividadId") UUID actividadId,
            @RequestBody List<CalificarMasivoItem> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        List<Map<String, Object>> itemMaps = items.stream()
                .map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("alumnoId", i.getAlumnoId());
                    m.put("calificacion", i.getCalificacion());
                    m.put("comentario", i.getComentario());
                    return m;
                })
                .toList();

        int actualizados = writeService.calificarMasivo(actividadId, itemMaps, user.getId(), user.getUsername());
        return ResponseEntity.ok(Map.of("actualizados", actualizados));
    }
}

package mx.ades.modules.gradebook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.common.ValidationUtils;
import mx.ades.modules.gradebook.query.ActividadesQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
        @NotBlank(message = "titulo es obligatorio")
        @Size(min = 3, max = 255, message = "titulo debe tener entre 3 y 255 caracteres")
        private String titulo;

        @Size(max = 2000, message = "descripcion máximo 2000 caracteres")
        private String descripcion;

        @NotNull(message = "grupoId es obligatorio")
        private UUID grupoId;

        @NotNull(message = "materiaId es obligatorio")
        private UUID materiaId;

        private UUID periodoEvaluacionId;
        private String tipoItem = "tarea";
        private UUID temaId;
        private UUID planTrabajoId;
        private UUID rubricaId;

        /** Acepta ISO (yyyy-MM-dd) y formato México (dd/MM/yyyy) — ver ValidationUtils.parseFechaFlexible. */
        private String fechaAsignacion;
        private String fechaEntrega;
        private String fechaExamen;

        @NotNull(message = "puntajeMaximo es obligatorio")
        @DecimalMin(value = "0.01", message = "puntajeMaximo mínimo 0.01")
        @DecimalMax(value = "10", message = "puntajeMaximo máximo 10 (escala SEP)")
        private Double puntajeMaximo = 10.0;

        private String instruccionesUrl;
        private Boolean permiteEntregaTarde = false;
    }

    @Data
    public static class CalificarMasivoItem {
        @NotNull(message = "alumnoId es obligatorio")
        private UUID alumnoId;

        @NotNull(message = "calificacion es obligatoria")
        @DecimalMin(value = "0", message = "calificacion mínimo 0")
        @DecimalMax(value = "10", message = "calificacion máximo 10")
        private Double calificacion;

        @Size(max = 500, message = "comentario máximo 500 caracteres")
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
            @RequestBody @Valid ActividadIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        LocalDate fechaAsignacion = body.getFechaAsignacion() != null && !body.getFechaAsignacion().isBlank()
                ? ValidationUtils.parseFechaFlexible(body.getFechaAsignacion(), "fechaAsignacion")
                : LocalDate.now();
        LocalDate fechaEntrega = body.getFechaEntrega() != null && !body.getFechaEntrega().isBlank()
                ? ValidationUtils.parseFechaFlexible(body.getFechaEntrega(), "fechaEntrega")
                : null;
        LocalDate fechaExamen = body.getFechaExamen() != null && !body.getFechaExamen().isBlank()
                ? ValidationUtils.parseFechaFlexible(body.getFechaExamen(), "fechaExamen")
                : null;

        if (fechaEntrega == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechaEntrega es obligatoria");
        if (fechaEntrega.isBefore(fechaAsignacion))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fechaEntrega no puede ser anterior a fechaAsignacion");

        var result = writeService.crearActividad(
                body.getTitulo(), body.getDescripcion(), body.getGrupoId(), body.getMateriaId(),
                body.getPeriodoEvaluacionId(), body.getTipoItem(), body.getTemaId(), body.getPlanTrabajoId(),
                body.getRubricaId(), fechaAsignacion, fechaEntrega, fechaExamen,
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
            @RequestBody @Valid List<@Valid CalificarMasivoItem> items,
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

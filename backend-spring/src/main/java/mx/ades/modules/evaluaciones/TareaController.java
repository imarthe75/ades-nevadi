package mx.ades.modules.evaluaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.model.TipoItem;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CrearActividadUseCase;
import mx.ades.modules.evaluaciones.query.TareaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final CrearActividadUseCase crearActividad;
    private final CalificarMasivoUseCase calificarMasivo;
    private final TareaQueryService query;
    private final AdesUserService userService;

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<Page<Map<String, Object>>> actividadesDeGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem,
            @PageableDefault(size = 20, sort = "fecha_creacion", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(query.actividadesDeGrupoPaginado(grupoId, materiaId, periodoId, tipoItem, pageable));
    }

    /** GET /tareas?grupo_id=...&materia_id=... — alias con query params (formato usado por el frontend) */
    @GetMapping
    public ResponseEntity<Page<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem,
            @PageableDefault(size = 20, sort = "fecha_creacion", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Para no-admins (DOCENTE, ALUMNO, PADRE) requerir grupo_id para evitar volcado cross-plantel
        if (grupoId == null && (user.getNivelAcceso() == null || user.getNivelAcceso() > 1)) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "El parámetro 'grupo_id' es requerido");
        }
        return ResponseEntity.ok(query.actividadesDeGrupoPaginado(grupoId, materiaId, periodoId, tipoItem, pageable));
    }

    /** PATCH /tareas/{id} — actualiza campos editables de la tarea */
    @PatchMapping("/{actividad_id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("actividad_id") UUID actividadId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        // Delegar al query service para actualizar campos simples
        return ResponseEntity.ok(query.actualizarTarea(actividadId, body));
    }

    public record CrearActividadRequest(
            String titulo,
            String descripcion,
            UUID grupoId,
            UUID materiaId,
            UUID temaId,
            UUID periodoEvaluacionId,
            String fechaAsignacion,
            String fechaEntrega,
            BigDecimal puntajeMaximo,
            String tipoItem,
            Boolean permiteEntregaTarde,
            String instruccionesUrl) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(
            @RequestBody CrearActividadRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        TipoItem tipo = body.tipoItem() != null
                ? TipoItem.valueOf(body.tipoItem().toUpperCase())
                : TipoItem.TAREA;

        CrearActividadUseCase.Result result = crearActividad.ejecutar(
                new CrearActividadUseCase.Command(
                        body.titulo(), body.descripcion(),
                        body.grupoId(), body.materiaId(), body.temaId(),
                        body.periodoEvaluacionId(),
                        body.fechaAsignacion() != null ? LocalDate.parse(body.fechaAsignacion()) : LocalDate.now(),
                        body.fechaEntrega()   != null ? LocalDate.parse(body.fechaEntrega())   : LocalDate.now().plusDays(7),
                        body.puntajeMaximo(),
                        tipo,
                        Boolean.TRUE.equals(body.permiteEntregaTarde()),
                        body.instruccionesUrl(),
                        user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.tareaId(),
                "slots_creados", result.slotsCreados(),
                "message", "Actividad creada y slots generados"));
    }

    @GetMapping("/{actividad_id}/entregas")
    public ResponseEntity<Page<Map<String, Object>>> entregasDeActividad(
            @PathVariable("actividad_id") UUID actividadId,
            @PageableDefault(size = 20, sort = "fecha_entrega", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(query.entregasDeActividadPaginado(actividadId, pageable));
    }

    public record CalificarMasivoRequest(List<ItemCalificacion> items) {
    }

    @PatchMapping("/{actividad_id}/calificar-masivo")
    public ResponseEntity<Map<String, Object>> calificarMasivo(
            @PathVariable("actividad_id") UUID actividadId,
            @RequestBody CalificarMasivoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int actualizados = calificarMasivo.ejecutar(
                new CalificarMasivoUseCase.Command(actividadId, request.items(), user.getPersonaId()));

        return ResponseEntity.ok(Map.of("actualizados", actualizados));
    }
}

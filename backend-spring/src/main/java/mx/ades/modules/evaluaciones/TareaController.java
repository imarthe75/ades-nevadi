package mx.ades.modules.evaluaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mx.ades.common.ValidationUtils;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final JdbcTemplate jdbc;

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<Page<Map<String, Object>>> actividadesDeGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem,
            @PageableDefault(size = 20, sort = "fecha_creacion", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/BFLA fix (asimetría): este endpoint no llamaba resolveUser ni verificaba nada
        // — a diferencia de su alias listar() (mismo recurso, vía query params) y de las
        // mutaciones de este mismo controller, que sí exigen requireAccesoGrupoTarea.
        // Cualquier usuario autenticado podía listar las tareas de cualquier grupo del sistema.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupoTarea(user, grupoId);
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
        // BOLA fix (asimetría): cuando se indica grupo_id, este alias no verificaba que el
        // usuario tuviera realmente acceso a ESE grupo (solo exigía que el parámetro no
        // viniera vacío) — un docente podía consultar las tareas de un grupo ajeno con solo
        // conocer su UUID. Mismo criterio que actividadesDeGrupo()/crearActividad().
        if (grupoId != null) {
            requireAccesoGrupoTarea(user, grupoId);
        }
        return ResponseEntity.ok(query.actividadesDeGrupoPaginado(grupoId, materiaId, periodoId, tipoItem, pageable));
    }

    /** PATCH /tareas/{id} — actualiza campos editables de la tarea */
    @PatchMapping("/{actividad_id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("actividad_id") UUID actividadId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Hallazgo de auditoría BOLA/BFLA (Fase 5): sin verificación de nivelAcceso ni de
        // asignación docente↔grupo — cualquier usuario autenticado podía editar cualquier
        // tarea del sistema (fecha de entrega, puntaje máximo, etc.).
        requireAccesoGrupoTarea(user, grupoIdDeTarea(actividadId));
        // Delegar al query service para actualizar campos simples
        return ResponseEntity.ok(query.actualizarTarea(actividadId, body));
    }

    public record CrearActividadRequest(
            @NotBlank(message = "titulo es obligatorio")
            @Size(min = 3, max = 255, message = "titulo debe tener entre 3 y 255 caracteres")
            String titulo,

            @Size(max = 2000, message = "descripcion máximo 2000 caracteres")
            String descripcion,

            @NotNull(message = "grupoId es obligatorio")
            UUID grupoId,

            @NotNull(message = "materiaId es obligatorio")
            UUID materiaId,

            UUID temaId,
            UUID periodoEvaluacionId,
            String fechaAsignacion,
            String fechaEntrega,

            @NotNull(message = "puntajeMaximo es obligatorio")
            @DecimalMin(value = "0.01", message = "puntajeMaximo mínimo 0.01")
            @DecimalMax(value = "10", message = "puntajeMaximo máximo 10 (escala SEP)")
            BigDecimal puntajeMaximo,

            String tipoItem,
            Boolean permiteEntregaTarde,
            String instruccionesUrl) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(
            @RequestBody @Valid CrearActividadRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Hallazgo de auditoría BOLA/BFLA (Fase 5): sin verificación de nivelAcceso ni de
        // asignación docente↔grupo — cualquier usuario autenticado podía crear tareas
        // (con slots de entrega generados) para cualquier grupo del sistema.
        requireAccesoGrupoTarea(user, body.grupoId());

        TipoItem tipo = body.tipoItem() != null
                ? TipoItem.valueOf(body.tipoItem().toUpperCase())
                : TipoItem.TAREA;

        LocalDate fechaAsignacion = body.fechaAsignacion() != null && !body.fechaAsignacion().isBlank()
                ? ValidationUtils.parseFechaFlexible(body.fechaAsignacion(), "fechaAsignacion")
                : LocalDate.now();
        LocalDate fechaEntrega = body.fechaEntrega() != null && !body.fechaEntrega().isBlank()
                ? ValidationUtils.parseFechaFlexible(body.fechaEntrega(), "fechaEntrega")
                : LocalDate.now().plusDays(7);

        if (fechaEntrega.isBefore(fechaAsignacion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fechaEntrega no puede ser anterior a fechaAsignacion");
        }

        CrearActividadUseCase.Result result = crearActividad.ejecutar(
                new CrearActividadUseCase.Command(
                        body.titulo(), body.descripcion(),
                        body.grupoId(), body.materiaId(), body.temaId(),
                        body.periodoEvaluacionId(),
                        fechaAsignacion,
                        fechaEntrega,
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
            @PageableDefault(size = 20, sort = "fecha_entrega", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA/BFLA fix (asimetría): este endpoint no llamaba resolveUser ni verificaba nada
        // — devuelve archivos/comentarios de entregas de alumnos, tan sensible como el
        // endpoint equivalente en gradebook.ActividadesController#entregasDeActividad, que
        // sí exige requireAccesoGrupo. Cualquier usuario autenticado podía leer las entregas
        // de cualquier actividad del sistema.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupoTarea(user, grupoIdDeTarea(actividadId));
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
        // Hallazgo de auditoría BOLA/BFLA (Fase 5, ver EvaluacionController#bulkCalificaciones
        // para el mismo hallazgo en evaluaciones): calificación masiva sin verificar
        // nivelAcceso ni asignación docente↔grupo — activo más sensible del sistema.
        requireAccesoGrupoTarea(user, grupoIdDeTarea(actividadId));

        int actualizados = calificarMasivo.ejecutar(
                new CalificarMasivoUseCase.Command(actividadId, request.items(), user.getPersonaId()));

        return ResponseEntity.ok(Map.of("actualizados", actualizados));
    }

    private UUID grupoIdDeTarea(UUID actividadId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT grupo_id FROM ades_tareas WHERE id = ?::uuid AND is_active = TRUE",
                UUID.class, actividadId.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada");
        return rows.get(0);
    }

    /**
     * Crear/editar tareas y calificar (individual o masivo) es operación de personal
     * escolar (nivelAcceso &le;4: admin/director/coordinador/docente). Admin/Director/
     * Coordinador (nivelAcceso &le;3) tienen alcance institucional; un Docente
     * (nivelAcceso 4) solo puede operar sobre grupos donde esté realmente asignado
     * (tabla {@code ades_asignaciones_docentes}) — previene BOLA (OWASP API1) y BFLA
     * (OWASP API5). Hallazgo de auditoría Fase 5: ninguno de estos controles existía.
     */
    private void requireAccesoGrupoTarea(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) {
            // BOLA fix (2026-07-16): "alcance institucional" nunca verificaba el plantel
            // del grupo — mismo hallazgo replicado en varios controllers de evaluaciones/
            // gradebook. Solo nivelAcceso 0 mantiene alcance libre.
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT gr.plantel_id FROM ades_grupos g " +
                    "JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE g.id = ?", UUID.class, grupoId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
            userService.verificarPlantel(user, plantelRows.get(0), "El grupo no pertenece a su plantel");
            return;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_asignaciones_docentes ad " +
                "JOIN ades_profesores p ON p.id = ad.profesor_id " +
                "WHERE ad.grupo_id = ? AND p.persona_id = ? AND ad.is_active = TRUE",
                Long.class, grupoId, user.getPersonaId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No está asignado a este grupo");
        }
    }
}

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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

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
        // BOLA fix: solo llamaba resolveUser sin verificar nivelAcceso ni asignación
        // docente↔grupo — un docente ajeno al grupo (o cualquier otro usuario) podía
        // listar las actividades/tareas de cualquier grupo del sistema.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(queryService.actividadesDeGrupo(grupoId, materiaId, periodoId, tipoItem));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(
            @RequestBody @Valid ActividadIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Hallazgo de auditoría BOLA/BFLA (Fase 5, mismo hallazgo replicado en
        // TareaController y EvaluacionController): sin verificación de nivelAcceso ni
        // de asignación docente↔grupo — cualquier usuario autenticado podía crear
        // actividades (con slots de entrega generados) para cualquier grupo del sistema.
        requireAccesoGrupo(user, body.getGrupoId());

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
        // BOLA fix: entregas incluye archivos/comentarios de alumnos por actividad; solo
        // llamaba resolveUser sin verificar nivelAcceso ni asignación docente↔grupo.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoIdDeActividad(actividadId));
        return ResponseEntity.ok(queryService.entregasDeActividad(actividadId));
    }

    @PatchMapping("/{actividadId}/calificar-masivo")
    public ResponseEntity<Map<String, Object>> calificarMasivo(
            @PathVariable("actividadId") UUID actividadId,
            @RequestBody @Valid List<@Valid CalificarMasivoItem> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // Hallazgo de auditoría BOLA/BFLA (Fase 5): calificación masiva sin verificar
        // nivelAcceso ni asignación docente↔grupo — activo más sensible del sistema.
        requireAccesoGrupo(user, grupoIdDeActividad(actividadId));

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

    private UUID grupoIdDeActividad(UUID actividadId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT grupo_id FROM ades_tareas WHERE id = ?::uuid AND is_active = TRUE",
                UUID.class, actividadId.toString());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada");
        return rows.get(0);
    }

    /**
     * Crear actividades y calificar (masivo) es operación de personal escolar
     * (nivelAcceso &le;4: admin/director/coordinador/docente). Admin/Director/
     * Coordinador (nivelAcceso &le;3) tienen alcance institucional; un Docente
     * (nivelAcceso 4) solo puede operar sobre grupos donde esté realmente asignado
     * (tabla {@code ades_asignaciones_docentes}) — previene BOLA (OWASP API1) y BFLA
     * (OWASP API5). Hallazgo de auditoría Fase 5: ninguno de estos controles existía.
     */
    private void requireAccesoGrupo(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) {
            // BOLA fix (2026-07-16): "alcance institucional" nunca verificaba el plantel
            // del grupo — Admin_Plantel/Director/Coordinador de un plantel podían crear
            // actividades, ver entregas y calificar masivamente grupos de CUALQUIER
            // plantel. Solo nivelAcceso 0 mantiene alcance libre.
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

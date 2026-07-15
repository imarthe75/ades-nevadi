package mx.ades.modules.gradebook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.RegistrarExcusaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;
import mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort;
import mx.ades.modules.entregas.query.EntregaQueryService;
import mx.ades.modules.evaluaciones.MinioService;
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

import java.util.*;

/**
 * Adaptador REST para el ciclo de vida de entregas de actividades.
 * Expone endpoints bajo /api/v1/entregas para consultar entregas de un alumno
 * o pendientes de un grupo, subir archivos vía multipart/form-data a SeaweedFS/MinIO,
 * calificar una entrega individual y registrar excusas.
 * Requiere JWT válido; las mutaciones exigen usuario autenticado para el audit trail.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/entregas")
@RequiredArgsConstructor
public class EntregasController {

    private final AdesUserService userService;
    private final MinioService minioService;
    private final SubirEntregaUseCase subirEntregaUseCase;
    private final CalificarEntregaUseCase calificarEntregaUseCase;
    private final RegistrarExcusaUseCase registrarExcusaUseCase;
    private final EntregaQueryService queryService;
    private final EntregaRepositoryPort entregaRepositoryPort;
    private final JdbcTemplate jdbc;

    @Data
    public static class CalificarIn {
        @NotNull(message = "calificacion es obligatoria")
        @DecimalMin(value = "0", message = "calificacion mínimo 0")
        @DecimalMax(value = "10", message = "calificacion máximo 10")
        private Double calificacion;

        @Size(max = 500, message = "comentario máximo 500 caracteres")
        private String comentario;
    }

    @GetMapping("/alumno/{alumnoId}")
    public ResponseEntity<List<Map<String, Object>>> entregasDelAlumno(
            @PathVariable("alumnoId") UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "solo_pendientes", defaultValue = "false") boolean soloPendientes,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (mismo criterio que PortalFamiliasController#verificarAccesoAlumno):
        // entregas de un alumno (archivos/comentarios/calificaciones) por path param sin
        // ninguna verificación — cualquier usuario autenticado podía leer las entregas de
        // cualquier alumno, incluidos padres sin relación de tutoría con ese alumno.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(queryService.byAlumno(alumnoId, periodoId, materiaId, soloPendientes));
    }

    @GetMapping("/pendientes/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> pendientesDelGrupo(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix: lista de entregas pendientes de calificar de TODO un grupo; solo
        // llamaba resolveUser sin verificar nivelAcceso ni asignación docente↔grupo.
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, grupoId);
        return ResponseEntity.ok(queryService.pendientesByGrupo(grupoId, materiaId));
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> subirEntrega(
            @RequestParam("tarea_id") UUID tareaId,
            @RequestParam("alumno_id") UUID alumnoId,
            @RequestParam(value = "comentario", required = false) String comentario,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        String archivoUrl = null;
        if (archivo != null && !archivo.isEmpty()) {
            archivoUrl = minioService.uploadFile(tareaId, alumnoId, archivo);
        }

        SubirEntregaUseCase.Command cmd;
        try {
            cmd = new SubirEntregaUseCase.Command(tareaId, alumnoId, comentario, archivoUrl, user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        Map<String, Object> result = subirEntregaUseCase.subir(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PatchMapping("/{entregaId}/calificar")
    public ResponseEntity<Map<String, Object>> calificarEntrega(
            @PathVariable("entregaId") UUID entregaId,
            @RequestBody @Valid CalificarIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        CalificarEntregaUseCase.Command cmd;
        try {
            cmd = new CalificarEntregaUseCase.Command(entregaId, body.getCalificacion(), body.getComentario(),
                    user.getId(), user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        try {
            return ResponseEntity.ok(calificarEntregaUseCase.calificar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{entregaId}/excusa")
    public ResponseEntity<Map<String, Object>> registrarExcusa(
            @PathVariable("entregaId") UUID entregaId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        RegistrarExcusaUseCase.Command cmd;
        try {
            cmd = new RegistrarExcusaUseCase.Command(entregaId, motivo, user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        try {
            return ResponseEntity.ok(registrarExcusaUseCase.registrar(cmd));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** OA-020: reabre una entrega calificada/excusada para permitir una nueva entrega. */
    @PatchMapping("/{entregaId}/reabrir")
    public ResponseEntity<Map<String, Object>> reabrirEntrega(
            @PathVariable("entregaId") UUID entregaId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int rows = entregaRepositoryPort.reabrir(entregaId, motivo, user.getUsername());
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega no encontrada");
        return ResponseEntity.ok(Map.of("id", entregaId.toString(), "estatus_entrega", "PENDIENTE"));
    }

    /**
     * Personal escolar (nivelAcceso &le;4) puede consultar entregas de cualquier alumno de su
     * plantel. Padres/alumnos (nivelAcceso &gt;=5) solo si son tutor activo del alumno — mismo
     * criterio que {@code PortalFamiliasController#verificarAccesoAlumno}, previene IDOR/BOLA.
     */
    private void requireAccesoAlumno(AdesUser user, UUID alumnoId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            if (user.getPlantelId() == null) return;
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, alumnoId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            if (!user.getPlantelId().equals(plantelRows.get(0))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El alumno no pertenece a su plantel");
            }
            return;
        }
        String email = user.getEmail();
        Integer count = email == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE p.email_personal = ? AND ta.alumno_id = ? AND ta.is_active = TRUE",
                Integer.class, email, alumnoId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este alumno");
        }
    }

    /**
     * Docentes (nivelAcceso 4) solo si están asignados al grupo (ades_asignaciones_docentes);
     * admin/director/coordinador (nivelAcceso &le;3), alcance institucional — mismo criterio que
     * {@code ActividadesController#requireAccesoGrupo}.
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
}

package mx.ades.modules.horarios;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador REST para asignaciones docente↔materia↔grupo por ciclo escolar.
 * Es el dato de origen que el generador de horarios (Timefold) usa para saber
 * cuántas lecciones sin horario debe programar. Sin asignaciones aquí, el
 * generador de horarios no tiene nada que programar (ver endpoint
 * /api/v1/horarios/solver/lecciones-sugeridas en HorarioController).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/asignaciones-docentes")
@RequiredArgsConstructor
public class AsignacionDocenteController {

    private final AdesUserService userService;
    private final AsignacionDocenteRepository repository;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<AsignacionDocente>> listar(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = true) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        if (effectivePlantel == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");
        }
        return ResponseEntity.ok(repository.findByPlantelIdAndCicloEscolarId(effectivePlantel, cicloId));
    }

    @PostMapping
    public ResponseEntity<AsignacionDocente> crear(
            @RequestBody AsignacionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        if (body.getGrupoId() == null || body.getMateriaId() == null
                || body.getProfesorId() == null || body.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "grupoId, materiaId, profesorId y cicloEscolarId son requeridos");
        }
        // BOLA fix (asimetría): listar() ya acota por plantel vía getEffectivePlantelId(); esta
        // creación no verificaba que el grupo indicado perteneciera al plantel del usuario —
        // un Coordinador podía crear asignaciones docente↔grupo para OTRO plantel.
        verificarPlantelDeGrupo(user, body.getGrupoId());
        repository.findByGrupoIdAndMateriaIdAndCicloEscolarIdAndIsActiveTrue(
                body.getGrupoId(), body.getMateriaId(), body.getCicloEscolarId())
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una asignación para ese grupo/materia en este ciclo (id=" + existing.getId() + ")");
            });
        AsignacionDocente entity = new AsignacionDocente();
        entity.setGrupoId(body.getGrupoId());
        entity.setMateriaId(body.getMateriaId());
        entity.setProfesorId(body.getProfesorId());
        entity.setCicloEscolarId(body.getCicloEscolarId());
        entity.setIsActive(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(entity));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AsignacionDocente> actualizar(
            @PathVariable UUID id,
            @RequestBody AsignacionPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        AsignacionDocente entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada"));
        // BOLA fix (asimetría): mismo hallazgo que crear() — sin esto un Coordinador podía
        // editar la asignación docente↔grupo de OTRO plantel por solo conocer el id.
        verificarPlantelDeGrupo(user, entity.getGrupoId());
        if (body.getProfesorId() != null) entity.setProfesorId(body.getProfesorId());
        if (body.getIsActive() != null) entity.setIsActive(body.getIsActive());
        return ResponseEntity.ok(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        AsignacionDocente entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada"));
        // BOLA fix (asimetría): mismo hallazgo que crear()/actualizar().
        verificarPlantelDeGrupo(user, entity.getGrupoId());
        entity.setIsActive(false);
        repository.save(entity);
    }

    /** Alta/edición de asignaciones es operación de coordinación académica (nivelAcceso &le;3). */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    /**
     * Mismo criterio de scoping que {@code AdesUserService#getEffectivePlantelId} (usado en
     * listar()): usuarios con nivelAcceso &gt; 1 quedan acotados a su propio plantel.
     */
    private void verificarPlantelDeGrupo(AdesUser user, UUID grupoId) {
        UUID plantelDeGrupo;
        try {
            plantelDeGrupo = jdbc.queryForObject(
                    "SELECT gr.plantel_id FROM ades_grupos g JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE g.id = ?",
                    UUID.class, grupoId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        }
        userService.verificarPlantel(user, plantelDeGrupo, "El grupo no pertenece a su plantel");
    }

    @Data
    public static class AsignacionPayload {
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID cicloEscolarId;
        private Boolean isActive;
    }
}

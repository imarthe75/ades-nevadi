package mx.ades.modules.planes_estudio;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService;
import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;
import mx.ades.modules.planes_estudio.query.PlanesEstudioQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Adaptador REST para la gestión de planes de estudio por grado y ciclo escolar.
 * Expone endpoints bajo /api/v1/planes-estudio para listar planes con filtros opcionales
 * (ciclo, grado, nivel), asignar una materia a un grado+ciclo (use case hexagonal),
 * actualizar campos parciales (PATCH) y eliminar una asignación.
 * Cubre tanto el plan NEM SEP (primaria/secundaria) como el CBU UAEMEX (preparatoria).
 * Todos los endpoints mutantes exigen Admin Plantel o superior ({@code requireNivel}).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/planes-estudio")
@RequiredArgsConstructor
public class PlanesEstudioController {

    private final PlanesEstudioQueryService queryService;
    private final AsignarMateriaUseCase asignarMateriaUseCase;
    private final PlanEstudioApplicationService planEstudioService;
    private final PlanAltQueryService planAltQueryService;
    private final PlanAltWriteService planAltWriteService;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final int NIVEL_ADMIN_PLANTEL = 2;
    private static final int NIVEL_COORD_ACADEMICO = 3;

    private void requireNivel(AdesUser user, int maxNivel) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > maxNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    /**
     * BOLA fix (2026-07-16): las mutaciones de plan de estudio (materia×grado×ciclo)
     * solo verificaban nivelAcceso — sin esto, Admin_Plantel (nivel &le;2) de un plantel
     * podía crear/editar/publicar/archivar/eliminar el plan de estudio de un grado de
     * CUALQUIER plantel. Solo nivelAcceso 0 mantiene alcance libre.
     */
    private void verificarAccesoGrado(AdesUser user, UUID gradoId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_grados WHERE id = ?", UUID.class, gradoId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grado no encontrado");
        userService.verificarPlantel(user, rows.get(0), "El grado no pertenece a su plantel");
    }

    private UUID gradoDePlan(UUID planId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT grado_id FROM ades_materias_plan WHERE id = ?", UUID.class, planId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado");
        return rows.get(0);
    }

    private void verificarAccesoEstudiante(AdesUser user, UUID estudianteId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, estudianteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        userService.verificarPlantel(user, rows.get(0), "El alumno no pertenece a su plantel");
    }

    private void verificarAccesoPlanAlt(AdesUser user, UUID planAltId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT estudiante_id, grupo_id FROM ades_planes_estudio_alt WHERE id = ?", planAltId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan alternativo no encontrado");
        UUID estudianteId = (UUID) rows.get(0).get("estudiante_id");
        UUID grupoId = (UUID) rows.get(0).get("grupo_id");
        if (estudianteId != null) verificarAccesoEstudiante(user, estudianteId);
        else if (grupoId != null) userService.verificarAccesoGrupo(user, grupoId);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grado_id", required = false) UUID gradoId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId) {
        return ResponseEntity.ok(queryService.listar(cicloId, gradoId, nivelId));
    }

    @PostMapping
    @CacheEvict(value = "catalogos", allEntries = true)
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_ADMIN_PLANTEL);
        try {
            UUID materiaId = UUID.fromString((String) body.get("materia_id"));
            UUID gradoId = UUID.fromString((String) body.get("grado_id"));
            UUID cicloId = UUID.fromString((String) body.get("ciclo_escolar_id"));
            verificarAccesoGrado(user, gradoId);
            Number horasSemana = body.get("horas_semana") instanceof Number ? (Number) body.get("horas_semana") : 0;
            Boolean esObligatoria = body.get("es_obligatoria") instanceof Boolean ? (Boolean) body.get("es_obligatoria") : true;

            UUID id = asignarMateriaUseCase.asignar(
                new AsignarMateriaUseCase.Command(materiaId, gradoId, cicloId, horasSemana.doubleValue(), esObligatoria));
            return ResponseEntity.status(HttpStatus.CREATED).body(planEstudioService.detalle(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La materia ya está asignada a este grado y ciclo: " + e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    @CacheEvict(value = "catalogos", allEntries = true)
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_ADMIN_PLANTEL);
        verificarAccesoGrado(user, gradoDePlan(id));
        planEstudioService.patch(id, body);
        return ResponseEntity.ok(Map.of("id", id.toString(), "updated", true));
    }

    /** AC-015: publica una versión de plan de estudio (visible en vistas operativas). Admin Plantel o superior. */
    @PatchMapping("/{id}/publicar")
    @CacheEvict(value = "catalogos", allEntries = true)
    public ResponseEntity<Map<String, Object>> publicar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_ADMIN_PLANTEL);
        verificarAccesoGrado(user, gradoDePlan(id));
        planEstudioService.publicar(id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "estado_publicacion", "PUBLICADO"));
    }

    /** AC-015: archiva una versión de plan de estudio (histórico, ya no operativo). Admin Plantel o superior. */
    @PatchMapping("/{id}/archivar")
    @CacheEvict(value = "catalogos", allEntries = true)
    public ResponseEntity<Map<String, Object>> archivar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_ADMIN_PLANTEL);
        verificarAccesoGrado(user, gradoDePlan(id));
        planEstudioService.archivar(id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "estado_publicacion", "ARCHIVADO"));
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "catalogos", allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_ADMIN_PLANTEL);
        verificarAccesoGrado(user, gradoDePlan(id));
        try {
            planEstudioService.eliminar(id);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    // ── AC-014: Planes alternativos/reducidos (NEE) ───────────────────────────

    /**
     * BOLA fix (auditoría 2026-07-15): planes NEE (necesidades educativas especiales)
     * son datos sensibles del alumno (diagnóstico/motivo de la reducción curricular);
     * antes no llamaba a resolveUser() en absoluto — cualquier cuenta ADES autenticada,
     * incluidos alumnos/padres, podía listarlos de cualquier estudiante o grupo por id.
     * Se exige personal escolar (nivelAcceso &le;4: admin/director/coordinador/docente),
     * igual que el resto de operaciones de planeación curricular en este módulo.
     */
    private void requireStaff(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    @GetMapping("/alternativos")
    public ResponseEntity<List<Map<String, Object>>> listarAlternativos(
            @RequestParam(name = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        if (estudianteId != null) {
            verificarAccesoEstudiante(user, estudianteId);
            return ResponseEntity.ok(planAltQueryService.listarPorEstudiante(estudianteId));
        }
        if (grupoId != null) {
            userService.verificarAccesoGrupo(user, grupoId);
            return ResponseEntity.ok(planAltQueryService.listarPorGrupo(grupoId));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere estudiante_id o grupo_id");
    }

    @GetMapping("/alternativos/{id}/materias")
    public ResponseEntity<List<Map<String, Object>>> materiasAlternativo(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        verificarAccesoPlanAlt(user, id);
        return ResponseEntity.ok(planAltQueryService.materias(id));
    }

    @PostMapping("/alternativos")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> crearAlternativo(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_COORD_ACADEMICO);

        UUID estudianteId = body.get("estudiante_id") != null ? UUID.fromString((String) body.get("estudiante_id")) : null;
        UUID grupoId = body.get("grupo_id") != null ? UUID.fromString((String) body.get("grupo_id")) : null;
        String motivo = (String) body.get("motivo");
        List<Map<String, Object>> materias = (List<Map<String, Object>>) body.getOrDefault("materias", List.of());

        if (motivo == null || motivo.isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El motivo es obligatorio");
        if ((estudianteId == null) == (grupoId == null))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Debe especificar exactamente uno: estudiante_id o grupo_id");
        if (estudianteId != null) verificarAccesoEstudiante(user, estudianteId);
        else userService.verificarAccesoGrupo(user, grupoId);

        UUID id = planAltWriteService.crear(estudianteId, grupoId, motivo, materias);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString()));
    }

    @DeleteMapping("/alternativos/{id}")
    public ResponseEntity<Void> eliminarAlternativo(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, NIVEL_COORD_ACADEMICO);
        verificarAccesoPlanAlt(user, id);
        planAltWriteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

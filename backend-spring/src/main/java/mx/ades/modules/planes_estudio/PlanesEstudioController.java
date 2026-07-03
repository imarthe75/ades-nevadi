package mx.ades.modules.planes_estudio;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService;
import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;
import mx.ades.modules.planes_estudio.query.PlanesEstudioQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Adaptador REST para la gestión de planes de estudio por grado y ciclo escolar.
 * Expone endpoints bajo /api/v1/planes-estudio para listar planes con filtros opcionales
 * (ciclo, grado, nivel), asignar una materia a un grado+ciclo (use case hexagonal),
 * actualizar campos parciales (PATCH) y eliminar una asignación.
 * Cubre tanto el plan NEM SEP (primaria/secundaria) como el CBU UAEMEX (preparatoria).
 * No requiere JWT propio en los endpoints de lectura/escritura (depende del security global).
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

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "grado_id", required = false) UUID gradoId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId) {
        return ResponseEntity.ok(queryService.listar(cicloId, gradoId, nivelId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            UUID materiaId = UUID.fromString((String) body.get("materia_id"));
            UUID gradoId = UUID.fromString((String) body.get("grado_id"));
            UUID cicloId = UUID.fromString((String) body.get("ciclo_escolar_id"));
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
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body) {
        planEstudioService.patch(id, body);
        return ResponseEntity.ok(Map.of("id", id.toString(), "updated", true));
    }

    /** AC-015: publica una versión de plan de estudio (visible en vistas operativas). */
    @PatchMapping("/{id}/publicar")
    public ResponseEntity<Map<String, Object>> publicar(@PathVariable("id") UUID id) {
        planEstudioService.publicar(id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "estado_publicacion", "PUBLICADO"));
    }

    /** AC-015: archiva una versión de plan de estudio (histórico, ya no operativo). */
    @PatchMapping("/{id}/archivar")
    public ResponseEntity<Map<String, Object>> archivar(@PathVariable("id") UUID id) {
        planEstudioService.archivar(id);
        return ResponseEntity.ok(Map.of("id", id.toString(), "estado_publicacion", "ARCHIVADO"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        try {
            planEstudioService.eliminar(id);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    // ── AC-014: Planes alternativos/reducidos (NEE) ───────────────────────────

    @GetMapping("/alternativos")
    public ResponseEntity<List<Map<String, Object>>> listarAlternativos(
            @RequestParam(name = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId) {
        if (estudianteId != null) return ResponseEntity.ok(planAltQueryService.listarPorEstudiante(estudianteId));
        if (grupoId != null) return ResponseEntity.ok(planAltQueryService.listarPorGrupo(grupoId));
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere estudiante_id o grupo_id");
    }

    @GetMapping("/alternativos/{id}/materias")
    public ResponseEntity<List<Map<String, Object>>> materiasAlternativo(@PathVariable UUID id) {
        return ResponseEntity.ok(planAltQueryService.materias(id));
    }

    @PostMapping("/alternativos")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> crearAlternativo(@RequestBody Map<String, Object> body) {
        UUID estudianteId = body.get("estudiante_id") != null ? UUID.fromString((String) body.get("estudiante_id")) : null;
        UUID grupoId = body.get("grupo_id") != null ? UUID.fromString((String) body.get("grupo_id")) : null;
        String motivo = (String) body.get("motivo");
        List<Map<String, Object>> materias = (List<Map<String, Object>>) body.getOrDefault("materias", List.of());

        if (motivo == null || motivo.isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El motivo es obligatorio");
        if ((estudianteId == null) == (grupoId == null))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Debe especificar exactamente uno: estudiante_id o grupo_id");

        UUID id = planAltWriteService.crear(estudianteId, grupoId, motivo, materias);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString()));
    }

    @DeleteMapping("/alternativos/{id}")
    public ResponseEntity<Void> eliminarAlternativo(@PathVariable UUID id) {
        planAltWriteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

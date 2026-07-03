package mx.ades.modules.planteles;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planteles.domain.port.in.ActualizarPlantelUseCase;
import mx.ades.modules.planteles.domain.port.in.CrearPlantelUseCase;
import mx.ades.modules.planteles.domain.port.out.PlantelRepositoryPort;
import mx.ades.modules.planteles.query.PlantelQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el catálogo de planteles del Instituto Nevadi.
 * Expone endpoints bajo /api/v1/planteles para listar todos los planteles,
 * obtener el detalle de un plantel por UUID, consultar estadísticas de ocupación
 * y niveles educativos asociados a un plantel, crear (restringido a ROLE_ADMIN)
 * y actualizar datos de un plantel (nombre, clave CT, estatus).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/planteles")
@RequiredArgsConstructor
public class PlantelController {

    private final CrearPlantelUseCase crearUseCase;
    private final ActualizarPlantelUseCase actualizarUseCase;
    private final PlantelRepositoryPort repositoryPort;
    private final PlantelQueryService queryService;
    private final PlantelClaveWriteService claveWriteService;

    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> stats() {
        return ResponseEntity.ok(queryService.stats());
    }

    @GetMapping("/{id}/claves")
    public ResponseEntity<List<Map<String, Object>>> claves(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.clavesPorPlantel(id));
    }

    @PatchMapping("/{id}/claves/{nivelEducativoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> actualizarClave(
            @PathVariable UUID id, @PathVariable UUID nivelEducativoId,
            @RequestBody Map<String, Object> body) {
        claveWriteService.actualizar(id, nivelEducativoId, body);
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @GetMapping("/{id}/niveles")
    public ResponseEntity<List<Map<String, Object>>> nivelesPorPlantel(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.nivelesPorPlantel(id));
    }

    @GetMapping
    public ResponseEntity<List<Plantel>> list() {
        return ResponseEntity.ok(repositoryPort.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plantel> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado")));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        CrearPlantelUseCase.Command cmd = new CrearPlantelUseCase.Command(
                (String) body.get("nombre_plantel"),
                body.get("escuela_id") != null ? UUID.fromString(body.get("escuela_id").toString()) : null,
                (String) body.get("clave_ct"),
                body.get("estatus_id") != null ? UUID.fromString(body.get("estatus_id").toString()) : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(crearUseCase.crear(cmd));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
        ActualizarPlantelUseCase.Command cmd = new ActualizarPlantelUseCase.Command(
                id,
                (String) body.get("nombre_plantel"),
                body.get("escuela_id") != null ? UUID.fromString(body.get("escuela_id").toString()) : null,
                (String) body.get("clave_ct"),
                body.get("estatus_id") != null ? UUID.fromString(body.get("estatus_id").toString()) : null,
                body.get("is_active") != null ? Boolean.valueOf(body.get("is_active").toString()) : null
        );
        return ResponseEntity.ok(actualizarUseCase.actualizar(cmd));
    }
}

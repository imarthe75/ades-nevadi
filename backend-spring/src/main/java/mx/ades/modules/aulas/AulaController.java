package mx.ades.modules.aulas;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.aulas.domain.port.in.ActualizarAulaUseCase;
import mx.ades.modules.aulas.domain.port.in.CrearAulaUseCase;
import mx.ades.modules.aulas.domain.port.out.AulaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final CrearAulaUseCase crearUseCase;
    private final ActualizarAulaUseCase actualizarUseCase;
    private final AulaRepositoryPort repositoryPort;

    @GetMapping
    public ResponseEntity<List<Aula>> list(@RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        return ResponseEntity.ok(plantelId != null
                ? repositoryPort.findByPlantelId(plantelId)
                : repositoryPort.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Aula> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada")));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        CrearAulaUseCase.Command cmd = new CrearAulaUseCase.Command(
                (String) body.get("nombre_aula"),
                body.get("plantel_id") != null ? UUID.fromString(body.get("plantel_id").toString()) : null,
                (String) body.get("tipo_aula"),
                body.get("capacidad_alumnos") != null ? ((Number) body.get("capacidad_alumnos")).intValue() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(crearUseCase.crear(cmd));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
        ActualizarAulaUseCase.Command cmd = new ActualizarAulaUseCase.Command(
                id,
                (String) body.get("nombre_aula"),
                body.get("plantel_id") != null ? UUID.fromString(body.get("plantel_id").toString()) : null,
                (String) body.get("tipo_aula"),
                body.get("capacidad_alumnos") != null ? ((Number) body.get("capacidad_alumnos")).intValue() : null,
                body.get("is_active") != null ? Boolean.valueOf(body.get("is_active").toString()) : null
        );
        return ResponseEntity.ok(actualizarUseCase.actualizar(cmd));
    }
}

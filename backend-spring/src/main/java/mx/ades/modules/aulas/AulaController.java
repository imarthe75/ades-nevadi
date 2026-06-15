package mx.ades.modules.aulas;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaRepository repository;

    @GetMapping
    public ResponseEntity<List<Aula>> list(@RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        if (plantelId != null) {
            return ResponseEntity.ok(repository.findByPlantelId(plantelId));
        }
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Aula> get(@PathVariable("id") UUID id) {
        Aula aula = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada"));
        return ResponseEntity.ok(aula);
    }

    @PostMapping
    public ResponseEntity<Aula> create(@RequestBody Aula aula) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(aula));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Aula> update(@PathVariable("id") UUID id, @RequestBody Aula update) {
        Aula aula = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada"));

        aula.setNombreAula(update.getNombreAula());
        aula.setPlantelId(update.getPlantelId());
        aula.setTipoAula(update.getTipoAula());
        aula.setCapacidadAlumnos(update.getCapacidadAlumnos());
        aula.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(aula));
    }
}

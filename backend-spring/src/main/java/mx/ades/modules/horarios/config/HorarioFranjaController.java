package mx.ades.modules.horarios.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/horario-franjas")
@RequiredArgsConstructor
public class HorarioFranjaController {

    private final HorarioFranjaRepository repository;

    @GetMapping
    public ResponseEntity<List<HorarioFranja>> listAll(
            @RequestParam(required = false) UUID nivelEducativoId,
            @RequestParam(required = false) UUID plantelId,
            @RequestParam(required = false) UUID cicloId) {
        
        List<HorarioFranja> result;
        if (nivelEducativoId != null && plantelId != null && cicloId != null) {
            result = repository.findFranjasAplicables(plantelId, cicloId, nivelEducativoId);
        } else if (nivelEducativoId != null) {
            result = repository.findByNivelEducativoIdOrderByDiaSemanaAscHoraInicioAsc(nivelEducativoId);
        } else if (plantelId != null) {
            result = repository.findByPlantelIdOrderByDiaSemanaAscHoraInicioAsc(plantelId);
        } else {
            result = repository.findAll();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<HorarioFranja> create(@RequestBody HorarioFranja entity) {
        return ResponseEntity.ok(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HorarioFranja> update(@PathVariable UUID id, @RequestBody HorarioFranja entity) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        entity.setId(id);
        return ResponseEntity.ok(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}

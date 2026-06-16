package mx.ades.modules.planteles;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planteles.query.PlantelQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/planteles")
@RequiredArgsConstructor
public class PlantelController {

    private final PlantelRepository repository;
    private final PlantelQueryService queryService;

    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> stats() {
        return ResponseEntity.ok(queryService.stats());
    }

    @GetMapping("/{id}/niveles")
    public ResponseEntity<List<Map<String, Object>>> nivelesPorPlantel(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.nivelesPorPlantel(id));
    }

    @GetMapping
    public ResponseEntity<List<Plantel>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plantel> get(@PathVariable("id") UUID id) {
        Plantel plantel = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));
        return ResponseEntity.ok(plantel);
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Plantel> create(@RequestBody Plantel plantel) {
        // ID is auto-assigned to UUID v7 in entity constructor
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(plantel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Plantel> update(@PathVariable("id") UUID id, @RequestBody Plantel update) {
        Plantel plantel = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));

        plantel.setNombrePlantel(update.getNombrePlantel());
        plantel.setEscuelaId(update.getEscuelaId());
        plantel.setClaveCt(update.getClaveCt());
        plantel.setEstatusId(update.getEstatusId());
        plantel.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(plantel));
    }
}

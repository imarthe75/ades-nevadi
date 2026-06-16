package mx.ades.modules.materias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.materias.query.MateriaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materias")
@RequiredArgsConstructor
public class MateriaController {

    private final MateriaRepository repository;
    private final MateriaQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "incluir_inactivas", required = false, defaultValue = "false") boolean incluirInactivas) {
        return ResponseEntity.ok(queryService.listar(nivelEducativoId, grupoId, tipo, incluirInactivas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Materia> get(@PathVariable("id") UUID id) {
        Materia mat = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada"));
        return ResponseEntity.ok(mat);
    }

    @PostMapping
    public ResponseEntity<Materia> create(@RequestBody Materia mat) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(mat));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Materia> update(@PathVariable("id") UUID id, @RequestBody Materia update) {
        Materia mat = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia no encontrada"));

        mat.setNombreMateria(update.getNombreMateria());
        mat.setClaveMateria(update.getClaveMateria());
        mat.setNivelEducativoId(update.getNivelEducativoId());
        mat.setHorasSemana(update.getHorasSemana());
        mat.setEsIngles(update.getEsIngles());
        mat.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(mat));
    }
}

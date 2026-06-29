package mx.ades.modules.horarios.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/horario-indisponibilidad")
@RequiredArgsConstructor
public class HorarioIndisponibilidadController {

    private final HorarioIndisponibilidadRepository repository;

    @GetMapping
    public ResponseEntity<List<HorarioIndisponibilidad>> getIndisponibilidad(
            @RequestParam UUID profesorId,
            @RequestParam UUID cicloEscolarId) {
        return ResponseEntity.ok(repository.findByProfesorIdAndCicloEscolarId(profesorId, cicloEscolarId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> saveIndisponibilidad(
            @RequestParam UUID profesorId,
            @RequestParam UUID cicloEscolarId,
            @RequestBody List<HorarioIndisponibilidad> indisponibilidades) {
        
        List<HorarioIndisponibilidad> existentes = repository.findByProfesorIdAndCicloEscolarId(profesorId, cicloEscolarId);
        repository.deleteAll(existentes);
        
        for (HorarioIndisponibilidad ind : indisponibilidades) {
            ind.setId(null); // Para que se generen nuevos UUIDs
            ind.setProfesorId(profesorId);
            ind.setCicloEscolarId(cicloEscolarId);
        }
        repository.saveAll(indisponibilidades);
        
        return ResponseEntity.ok().build();
    }
}

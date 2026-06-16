package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.alumnos.query.AlumnoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import mx.ades.shared.persona.PersonaUpdateHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final EstudianteRepository repository;
    private final AdesUserService userService;
    private final AlumnoQueryService query;
    private final PersonaUpdateHelper personaHelper;
    private final AlumnoComplementariosService complementariosService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(query.obtener(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        UUID personaId = query.resolverPersonaId(id);

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        personaHelper.actualizar(personaId, per);

        @SuppressWarnings("unchecked")
        Map<String, Object> comp = (Map<String, Object>) body.get("complementarios");
        complementariosService.actualizar(id, comp);

        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping
    public ResponseEntity<Estudiante> create(@RequestBody Estudiante est) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(est));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estudiante> update(@PathVariable UUID id, @RequestBody Estudiante update) {
        Estudiante est = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
        est.setMatricula(update.getMatricula());
        est.setPersonaId(update.getPersonaId());
        est.setPlantelId(update.getPlantelId());
        est.setEstatusId(update.getEstatusId());
        est.setFechaIngreso(update.getFechaIngreso());
        est.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repository.save(est));
    }
}

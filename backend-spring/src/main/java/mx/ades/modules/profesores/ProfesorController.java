package mx.ades.modules.profesores;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.profesores.query.ProfesorQueryService;
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
@RequestMapping("/api/v1/profesores")
@RequiredArgsConstructor
public class ProfesorController {

    private final ProfesorRepository repository;
    private final AdesUserService userService;
    private final ProfesorQueryService query;
    private final PersonaUpdateHelper personaHelper;
    private final ProfesorLaboralesService laboralesService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel, buscar));
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
        personaHelper.actualizarBasico(personaId, per);

        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        laboralesService.actualizar(id, lab);

        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping
    public ResponseEntity<Profesor> create(@RequestBody Profesor prof) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(prof));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Profesor> update(@PathVariable UUID id, @RequestBody Profesor update) {
        Profesor prof = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado"));
        prof.setNumeroEmpleado(update.getNumeroEmpleado());
        prof.setPersonaId(update.getPersonaId());
        prof.setPlantelId(update.getPlantelId());
        prof.setEstatusId(update.getEstatusId());
        prof.setTipoContrato(update.getTipoContrato());
        prof.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repository.save(prof));
    }
}

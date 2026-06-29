package mx.ades.modules.horarios.config;

import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/horarios/reglas")
public class HorarioReglaController {

    private final HorarioReglaRepository reglaRepository;
    private final AdesUserService userService;

    public HorarioReglaController(HorarioReglaRepository reglaRepository, AdesUserService userService) {
        this.reglaRepository = reglaRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<HorarioRegla>> listar(
            @RequestParam(required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        if (cicloId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cicloId es requerido");
        }
        UUID reqCiclo = cicloId;
        UUID plantelId = user.getPlantelId();

        List<HorarioRegla> reglas;
        if (plantelId != null) {
            reglas = reglaRepository.findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(plantelId, reqCiclo);
        } else {
            reglas = reglaRepository.findAll();
        }
        return ResponseEntity.ok(reglas);
    }

    @PostMapping
    public ResponseEntity<HorarioRegla> crear(
            @RequestBody HorarioRegla payload,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        
        if (payload.getPlantelId() == null) {
            if (user.getPlantelId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plantel_id es requerido");
            }
            payload.setPlantelId(user.getPlantelId());
        }
        if (payload.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ciclo_escolar_id es requerido");
        }
        
        HorarioRegla guardada = reglaRepository.save(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        reglaRepository.deleteById(id);
    }
}

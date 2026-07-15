package mx.ades.modules.horarios.config;

import mx.ades.security.AdesUser;
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
        requireStaff(user);

        // BFLA/BOLA: solo Admin Global (nivelAcceso 0) puede fijar un plantel_id distinto
        // al propio; el resto (Director/Coordinador nivelAcceso 1-3) queda forzado a su
        // propio plantel, sin importar lo que envíe el body — evita que un admin de
        // Plantel A cree reglas institucionales a nombre de Plantel B.
        if (user.getNivelAcceso() != 0) {
            payload.setPlantelId(user.getPlantelId());
        }
        if (payload.getPlantelId() == null) {
            if (user.getPlantelId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plantel_id es requerido");
            }
            payload.setPlantelId(user.getPlantelId());
        }
        if (payload.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ciclo_escolar_id es requerido");
        }
        if (payload.getTipo() == null || payload.getTipo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo es requerido");
        }
        // params es NOT NULL en BD (ades_horario_regla, con default '{}'::jsonb, pero Hibernate
        // envía NULL explícito si el campo llega vacío en el JSON, lo que ignora el default y
        // produce DataIntegrityViolationException -> 409 engañoso en vez de un 400 claro).
        if (payload.getParams() == null) {
            payload.setParams(java.util.Map.of());
        }

        HorarioRegla guardada = reglaRepository.save(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        requireStaff(user);
        HorarioRegla regla = reglaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla de horario no encontrada"));
        // BOLA: un Director/Coordinador (nivelAcceso 1-3) no puede borrar reglas de otro
        // plantel; solo Admin Global (nivelAcceso 0) tiene alcance institucional completo.
        if (user.getNivelAcceso() != 0
                && regla.getPlantelId() != null
                && !regla.getPlantelId().equals(user.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para eliminar reglas de otro plantel");
        }
        reglaRepository.deleteById(id);
    }

    /**
     * Configurar reglas de horario institucionales (usadas por todos los planteles/
     * grupos) es operación de Coordinador o superior (nivelAcceso &le;3) — previene
     * BFLA (OWASP API5), replicando el mismo criterio que {@code HorarioFranjaController}.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}

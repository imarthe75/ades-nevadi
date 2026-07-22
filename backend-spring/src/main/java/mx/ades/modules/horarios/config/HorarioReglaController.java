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
    private final HorarioTipoReglaRepository tipoReglaRepository;
    private final AdesUserService userService;

    public HorarioReglaController(HorarioReglaRepository reglaRepository,
                                  HorarioTipoReglaRepository tipoReglaRepository,
                                  AdesUserService userService) {
        this.reglaRepository = reglaRepository;
        this.tipoReglaRepository = tipoReglaRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<HorarioRegla>> listar(
            @RequestParam(required = false) UUID cicloId,
            @RequestParam(required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        // BOLA / RBAC: Si el usuario no es Admin Global (nivelAcceso != 0), forzar su plantel asignado.
        // Si el usuario es Admin Global (nivelAcceso == 0), usar el plantelId pasado por parámetro si está presente.
        UUID targetPlantelId = (user.getNivelAcceso() != 0) ? user.getPlantelId() : (plantelId != null ? plantelId : user.getPlantelId());

        List<HorarioRegla> reglas;
        if (targetPlantelId != null && cicloId != null) {
            reglas = reglaRepository.findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(targetPlantelId, cicloId);
        } else if (targetPlantelId != null) {
            reglas = reglaRepository.findByPlantelIdAndActivaTrueAndIsActiveTrue(targetPlantelId);
        } else if (cicloId != null) {
            reglas = reglaRepository.findByCicloEscolarIdAndActivaTrueAndIsActiveTrue(cicloId);
        } else {
            reglas = reglaRepository.findByActivaTrueAndIsActiveTrue();
        }
        return ResponseEntity.ok(reglas);
    }

    /**
     * Catálogo de tipos de regla que el motor realmente interpreta (tabla
     * ades_horario_tipo_regla). Lo consume el frontend para marcar cada regla como
     * "soportada por el motor" y para guiar al asistente IA (evita que proponga
     * tipos inexistentes). Lectura abierta a cualquier usuario autenticado.
     */
    @GetMapping("/tipos")
    public ResponseEntity<List<HorarioTipoRegla>> tipos(@AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(tipoReglaRepository.findByIsActiveTrueOrderByOrdenAsc());
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
        // El motor solo interpreta los tipos del catálogo ades_horario_tipo_regla;
        // cualquier otro se guardaría pero el solver lo ignoraría en silencio (regla
        // "fantasma" que el coordinador cree activa). Rechazar con un mensaje claro.
        if (!tipoReglaRepository.existsByCodigoAndIsActiveTrue(payload.getTipo())) {
            String soportados = tipoReglaRepository.findByIsActiveTrueOrderByOrdenAsc()
                    .stream().map(HorarioTipoRegla::getCodigo).collect(java.util.stream.Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "El tipo de regla '" + payload.getTipo() + "' no está implementado en el motor de horarios. "
                + "Tipos soportados: " + soportados);
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

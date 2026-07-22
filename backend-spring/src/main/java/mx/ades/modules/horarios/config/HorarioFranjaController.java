package mx.ades.modules.horarios.config;

import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/horario-franjas")
@RequiredArgsConstructor
public class HorarioFranjaController {

    private final HorarioFranjaRepository repository;
    private final HorarioTurnoRepository turnoRepository;
    private final AdesUserService userService;

    /**
     * Catálogo de turnos escolares posibles (tabla ades_horario_turno). Lo consume el
     * diálogo de Franjas Horarias (Administración) para poblar el select de turno en vez
     * de un input de texto libre. Lectura abierta a cualquier usuario autenticado (mismo
     * criterio que listAll: catálogo institucional consultable por staff/UI).
     */
    @GetMapping("/turnos")
    public ResponseEntity<List<HorarioTurno>> turnos(@AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(turnoRepository.findByIsActiveTrueOrderByOrdenAsc());
    }

    @GetMapping
    public ResponseEntity<List<HorarioFranja>> listAll(
            @RequestParam(required = false) UUID nivelEducativoId,
            @RequestParam(required = false) UUID plantelId,
            @RequestParam(required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (asimetría): este endpoint no llamaba resolveUser en absoluto — a
        // diferencia de create()/update()/delete() de este mismo controller, que sí exigen
        // requireStaff. No se restringe a staff porque las franjas horarias institucionales
        // son consultadas también por docentes/UI de disponibilidad; se exige al menos que el
        // usuario esté registrado en ADES (mismo criterio que AsignacionDocenteController/
        // HorarioReglaController: lectura abierta a autenticados, escritura restringida).
        userService.resolveUser(jwt);

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
    public ResponseEntity<HorarioFranja> create(
            @RequestBody HorarioFranja entity,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        // BOLA fix (2026-07-16): plantelId viaja en el body sin validar contra el
        // plantel del usuario — un coordinador de un plantel podía crear/mover franjas
        // horarias de OTRO plantel (mass assignment del campo plantelId).
        userService.verificarPlantel(user, entity.getPlantelId(), "No puede crear franjas de otro plantel");
        validarCamposObligatorios(entity);
        return ResponseEntity.ok(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HorarioFranja> update(
            @PathVariable UUID id,
            @RequestBody HorarioFranja entity,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        HorarioFranja existente = repository.findById(id).orElse(null);
        if (existente == null) return ResponseEntity.notFound().build();
        userService.verificarPlantel(user, existente.getPlantelId(), "La franja no pertenece a su plantel");
        userService.verificarPlantel(user, entity.getPlantelId(), "No puede mover la franja a otro plantel");
        validarCamposObligatorios(entity);
        entity.setId(id);
        return ResponseEntity.ok(repository.save(entity));
    }

    /**
     * {@code HorarioFranja} es una entidad JPA — no se le agregan anotaciones de Bean
     * Validation directamente (riesgo sobre la capa de persistencia/Hibernate). Se
     * valida manualmente antes de guardar, ya que las columnas correspondientes son
     * {@code NOT NULL} en BD (ades_horario_franjas) y de otro modo el error solo se
     * vería hasta el {@code DataIntegrityViolationException} en el insert.
     */
    private void validarCamposObligatorios(HorarioFranja entity) {
        if (entity.getCicloEscolarId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cicloEscolarId es obligatorio");
        if (entity.getDiaSemana() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diaSemana es obligatorio");
        if (entity.getHoraInicio() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horaInicio es obligatorio");
        if (entity.getHoraFin() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horaFin es obligatorio");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        HorarioFranja existente = repository.findById(id).orElse(null);
        if (existente == null) return ResponseEntity.notFound().build();
        userService.verificarPlantel(user, existente.getPlantelId(), "La franja no pertenece a su plantel");
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Configurar franjas horarias institucionales (usadas por todos los planteles/
     * grupos) es operación de Coordinador o superior (nivelAcceso &le;3) — previene
     * BFLA (OWASP API5) sobre la configuración de horarios de todo el plantel.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}

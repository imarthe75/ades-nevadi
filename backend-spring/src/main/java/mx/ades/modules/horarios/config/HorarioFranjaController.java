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
    private final AdesUserService userService;

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
        requireStaff(userService.resolveUser(jwt));
        validarCamposObligatorios(entity);
        return ResponseEntity.ok(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HorarioFranja> update(
            @PathVariable UUID id,
            @RequestBody HorarioFranja entity,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
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
        requireStaff(userService.resolveUser(jwt));
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
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

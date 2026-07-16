package mx.ades.modules.horarios.config;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/horario-indisponibilidad")
@RequiredArgsConstructor
public class HorarioIndisponibilidadController {

    private final HorarioIndisponibilidadRepository repository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<HorarioIndisponibilidad>> getIndisponibilidad(
            @RequestParam UUID profesorId,
            @RequestParam UUID cicloEscolarId,
            @AuthenticationPrincipal Jwt jwt) {
        // Hallazgo de auditoría BOLA (Fase 5): este endpoint no llamaba resolveUser ni
        // aplicaba ningún control — cualquier portador de un JWT válido podía consultar
        // la indisponibilidad (horario personal) de cualquier profesor pasando un
        // profesorId arbitrario. Se alinea con el mismo criterio ya usado en
        // saveIndisponibilidad() de este archivo (personal escolar, nivelAcceso &le;4).
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        verificarAccesoProfesor(user, profesorId);
        return ResponseEntity.ok(repository.findByProfesorIdAndCicloEscolarId(profesorId, cicloEscolarId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> saveIndisponibilidad(
            @RequestParam UUID profesorId,
            @RequestParam UUID cicloEscolarId,
            @RequestBody List<HorarioIndisponibilidad> indisponibilidades,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        verificarAccesoProfesor(user, profesorId);

        // franja_id es NOT NULL en BD (ades_horario_indisponibilidad) y no tiene default;
        // sin este chequeo un ítem sin franjaId cae en DataIntegrityViolationException ->
        // 409 "duplicado o referencia inválida" engañoso en vez de un 400 claro.
        for (HorarioIndisponibilidad ind : indisponibilidades) {
            if (ind.getFranjaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "franjaId es obligatorio en cada ítem de indisponibilidad");
            }
        }

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

    /**
     * Sobrescribir la indisponibilidad de un profesor (reemplaza todos sus
     * registros para el ciclo) es operación de personal escolar (nivelAcceso
     * &le;4: admin/director/coordinador/docente) — previene BOLA (OWASP API1)
     * sobre el horario de otros profesores.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    /**
     * BOLA fix (2026-07-16, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
     * #1 — HorarioIndisponibilidadController): requireStaff() solo validaba
     * nivelAcceso — un docente de un plantel podía leer y, más grave,
     * SOBRESCRIBIR COMPLETAMENTE (borra+reinserta en saveIndisponibilidad) la
     * disponibilidad de un profesor de OTRO plantel solo conociendo su UUID.
     */
    private void verificarAccesoProfesor(AdesUser user, UUID profesorId) {
        List<UUID> plantelRows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_profesores WHERE id = ?", UUID.class, profesorId);
        if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        userService.verificarPlantel(user, plantelRows.get(0), "El profesor no pertenece a su plantel");
    }
}

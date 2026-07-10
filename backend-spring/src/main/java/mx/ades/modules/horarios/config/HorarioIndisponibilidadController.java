package mx.ades.modules.horarios.config;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @RequestBody List<HorarioIndisponibilidad> indisponibilidades,
            @AuthenticationPrincipal Jwt jwt) {

        requireStaff(userService.resolveUser(jwt));

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
}

package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.alumnos.Estudiante;
import mx.ades.modules.alumnos.domain.port.in.ActualizarAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.in.CrearAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.out.AlumnoRepositoryPort;
import mx.ades.modules.alumnos.query.AlumnoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
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

    private final AdesUserService         userService;
    private final AlumnoQueryService      query;
    private final CrearAlumnoUseCase      crear;
    private final ActualizarAlumnoUseCase actualizar;
    private final AlumnoRepositoryPort    repositoryPort;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "nivel_id",   required = false) UUID nivelId,
            @RequestParam(name = "grado_id",   required = false) UUID gradoId,
            @RequestParam(name = "grupo_id",   required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel, nivelId, gradoId, grupoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(query.obtener(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody CrearAlumnoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelId = req.plantel_id() != null ? req.plantel_id() : user.getPlantelId();
        var cmd = new CrearAlumnoUseCase.Command(
                req.nombre(),
                req.apellido_paterno(),
                req.apellido_materno(),
                req.curp(),
                plantelId,
                user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(crear.crear(cmd));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        // Optimistic locking: si el cliente envía rowVersion, verificar antes de modificar
        Object rv = body.get("rowVersion");
        if (rv != null) {
            Integer clientVersion = rv instanceof Number n ? n.intValue() : null;
            if (clientVersion != null) {
                Estudiante current = repositoryPort.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
                if (!clientVersion.equals(current.getRowVersion())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El registro fue modificado. Versión enviada: " + clientVersion +
                        ", actual: " + current.getRowVersion() + ". Recarga y vuelve a intentarlo.");
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> per  = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> comp = (Map<String, Object>) body.get("complementarios");
        return ResponseEntity.ok(actualizar.actualizar(
                new ActualizarAlumnoUseCase.Command(id, per, comp)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estudiante> update(
            @PathVariable UUID id,
            @RequestBody Estudiante update) {
        Estudiante est = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
        est.setMatricula(update.getMatricula());
        est.setPersonaId(update.getPersonaId());
        est.setPlantelId(update.getPlantelId());
        est.setEstatusId(update.getEstatusId());
        est.setFechaIngreso(update.getFechaIngreso());
        est.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repositoryPort.save(est));
    }
}

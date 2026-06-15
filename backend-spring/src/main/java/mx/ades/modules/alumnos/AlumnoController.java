package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.alumnos.query.AlumnoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import mx.ades.shared.persona.PersonaUpdateHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;
    private final AlumnoQueryService query;
    private final PersonaUpdateHelper personaHelper;

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
        if (comp != null) {
            Object lengInd = comp.get("lengua_indigena_id");
            Object nivIng  = comp.get("nivel_ingles_id");
            jdbc.update("""
                UPDATE ades_estudiantes
                   SET nss                   = ?,
                       discapacidad          = ?,
                       escuela_procedencia   = ?,
                       clave_ct_procedencia  = ?,
                       promedio_procedencia  = ?,
                       beca_tipo             = ?,
                       beca_monto            = ?,
                       nivel_socioeconomico  = ?,
                       etnia                 = ?,
                       lengua_indigena_id    = ?::uuid,
                       nivel_ingles_id       = ?::uuid
                 WHERE id = ?
                """,
                    comp.get("nss"), comp.get("discapacidad"), comp.get("escuela_procedencia"),
                    comp.get("clave_ct_procedencia"),
                    comp.get("promedio_procedencia") != null ? Double.parseDouble(comp.get("promedio_procedencia").toString()) : null,
                    comp.get("beca_tipo"),
                    comp.get("beca_monto") != null ? Double.parseDouble(comp.get("beca_monto").toString()) : null,
                    comp.get("nivel_socioeconomico"), comp.get("etnia"),
                    lengInd != null ? lengInd.toString() : null,
                    nivIng  != null ? nivIng.toString()  : null,
                    id);
        }

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

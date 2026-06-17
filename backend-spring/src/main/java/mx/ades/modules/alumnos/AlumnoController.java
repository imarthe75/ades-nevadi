package mx.ades.modules.alumnos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.admin.AdminWriteService;
import mx.ades.modules.admin.query.AdminQueryService;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final EstudianteRepository repository;
    private final AdesUserService userService;
    private final AlumnoQueryService query;
    private final PersonaUpdateHelper personaHelper;
    private final AlumnoComplementariosService complementariosService;
    private final AdminWriteService adminWrite;
    private final AdminQueryService adminQuery;
    private final JdbcTemplate jdbc;

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
        complementariosService.actualizar(id, comp);

        return ResponseEntity.ok(Map.of("updated", true));
    }

    /**
     * Alta rápida de alumno desde el formulario Angular.
     * Crea la Persona y el Estudiante en una sola llamada.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody CrearAlumnoRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        // Validaciones básicas
        if (req.nombre() == null || req.nombre().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El nombre es requerido");
        if (req.apellido_paterno() == null || req.apellido_paterno().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El apellido paterno es requerido");
        if (req.curp() == null || req.curp().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La CURP es requerida");
        if (req.curp().length() != 18)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La CURP debe tener exactamente 18 caracteres");

        // Verificar CURP duplicada
        if (adminQuery.curpExiste(req.curp()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un registro con esa CURP");

        // Resolver plantel: del request (admin eligiendo plantel), luego del usuario, luego primer plantel disponible
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelId = req.plantel_id() != null
                ? req.plantel_id()
                : user.getPlantelId();
        if (plantelId == null) {
            // Admin global sin plantel explícito → usar el primer plantel activo
            plantelId = jdbc.queryForObject(
                    "SELECT id FROM ades_planteles WHERE is_active = true ORDER BY fecha_creacion LIMIT 1",
                    UUID.class);
            if (plantelId == null)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No hay planteles activos en el sistema");
        }

        // Crear la Persona
        UUID personaId = adminWrite.insertPersona(
                req.nombre().trim(),
                req.apellido_paterno().trim(),
                req.apellido_materno() != null ? req.apellido_materno().trim() : null,
                req.curp().toUpperCase().trim(),
                null,
                null
        );

        // Generar matrícula única
        Integer seq = jdbc.queryForObject(
            "SELECT COALESCE(MAX(CAST(REGEXP_REPLACE(matricula, '[^0-9]', '', 'g') AS BIGINT)), 0)::int + 1 " +
            "FROM ades_estudiantes WHERE matricula ~ '^MAT-[0-9]+$'",
            Integer.class);
        String matricula = String.format("MAT-%06d", seq == null ? 1 : seq);

        // Crear el Estudiante
        Estudiante est = new Estudiante();
        est.setPersonaId(personaId);
        est.setPlantelId(plantelId);
        est.setMatricula(matricula);
        est.setFechaIngreso(LocalDate.now());
        est.setIsActive(true);
        Estudiante saved = repository.save(est);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id",         saved.getId(),
            "matricula",  saved.getMatricula(),
            "persona_id", personaId,
            "plantel_id", plantelId
        ));
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

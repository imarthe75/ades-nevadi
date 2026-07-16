package mx.ades.modules.condiciones;

import lombok.RequiredArgsConstructor;
import mx.ades.common.ValidationUtils;
import mx.ades.modules.condiciones.domain.model.TipoCondicion;
import mx.ades.modules.condiciones.domain.port.in.ActualizarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.EliminarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.RegistrarCondicionUseCase;
import mx.ades.modules.condiciones.query.CondicionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para el registro de condiciones crónicas de salud de alumnos.
 * Expone endpoints bajo /api/v1/condiciones-cronicas para registrar, actualizar,
 * consultar y eliminar condiciones crónicas (alergias, medicación, médico responsable).
 * Los datos son PII sensibles de salud bajo LFPDPPP — acceso restringido por
 * nivelAcceso verificado en los use cases. El endpoint /alumno/{alumnoId}/alerta
 * devuelve información de emergencia médica del alumno. El tipo de condición
 * es validado mediante el enum {@code TipoCondicion}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/condiciones-cronicas")
@RequiredArgsConstructor
public class CondicionCronicaController {

    private final AdesUserService            userService;
    private final RegistrarCondicionUseCase  registrarCondicion;
    private final ActualizarCondicionUseCase actualizarCondicion;
    private final EliminarCondicionUseCase   eliminarCondicion;
    private final CondicionQueryService      query;
    private final JdbcTemplate               jdbc;

    /**
     * BOLA fix: las condiciones crónicas (alergias, medicación, discapacidad) son PII
     * de salud sensible bajo LFPDPPP. Antes de este chequeo, listar()/alertaEmergencia()/
     * obtener() solo llamaban a resolveUser() sin verificar que el usuario tuviera
     * relación con el alumno — cualquier cuenta autenticada (incluido un alumno/padre,
     * nivelAcceso &gt;=5) podía consultar la ficha médica de CUALQUIER alumno del sistema.
     * Personal escolar (nivelAcceso &le;4) conserva alcance institucional; alumnos/padres
     * solo pueden consultar la de sí mismos o de un alumno del que son tutor activo
     * (mismo criterio que BoletasController#verificarAccesoAlumno).
     */
    private void verificarAccesoAlumno(AdesUser user, UUID alumnoId) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso != null && nivelAcceso <= 4) {
            // BOLA fix: "alcance institucional" no significa cross-plantel — personal
            // escolar sigue acotado a su propio plantel (mismo criterio que
            // BadgeController#requireAccesoAlumno).
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, alumnoId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
            userService.verificarPlantel(user, plantelRows.get(0), "El alumno no pertenece a su plantel");
            return;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes e WHERE e.id = ? AND (" +
                "  e.persona_id = ? OR EXISTS (" +
                "    SELECT 1 FROM ades_tutores_alumnos ta JOIN ades_personas p ON p.id = ta.persona_id " +
                "    WHERE ta.alumno_id = e.id AND ta.is_active = TRUE AND p.email_personal = ?" +
                "  )" +
                ")", Integer.class, alumnoId, user.getPersonaId(), user.getEmail());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a la información de este alumno");
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "alumno_id",       required = false) UUID alumnoId,
            @RequestParam(value = "tipo_condicion",  required = false) String tipoCondicion,
            @RequestParam(value = "solo_activas", defaultValue = "true") boolean soloActivas,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (alumnoId != null) {
            verificarAccesoAlumno(user, alumnoId);
        } else if (user.getNivelAcceso() != null && user.getNivelAcceso() > 4) {
            // Sin alumno_id, el listado devolvía la ficha médica de TODOS los alumnos.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debe especificar alumno_id");
        }
        return ResponseEntity.ok(query.list(alumnoId, tipoCondicion, soloActivas));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;

        validarCondicionCronica(body);

        var cmd = new RegistrarCondicionUseCase.Command(
                body.getAlumnoId(),
                TipoCondicion.of(body.getTipoCondicion()),
                body.getDescripcion(),
                body.getMedicacionNombre(),
                body.getDosis(),
                body.getFrecuencia(),
                body.getAlergias(),
                body.getMedicoResponsable(),
                body.getTelefonoMedico(),
                nivel
        );
        UUID id = registrarCondicion.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/alumno/{alumnoId}/alerta")
    public ResponseEntity<List<Map<String, Object>>> alertaEmergencia(
            @PathVariable("alumnoId") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(query.alertaEmergencia(alumnoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondicionCronica> obtener(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        CondicionCronica cc = query.findById(id);
        verificarAccesoAlumno(user, cc.getAlumnoId());
        return ResponseEntity.ok(cc);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody CondicionCronica body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;

        validarCondicionCronica(body);

        var cmd = new ActualizarCondicionUseCase.Command(
                id,
                body.getDescripcion(),
                body.getMedicacionNombre(),
                body.getDosis(),
                body.getFrecuencia(),
                body.getAlergias(),
                body.getMedicoResponsable(),
                body.getTelefonoMedico(),
                body.getActiva(),
                nivel
        );
        actualizarCondicion.actualizar(cmd);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        eliminarCondicion.eliminar(id, nivel);
    }

    private void validarCondicionCronica(CondicionCronica body) {
        if (body.getAlumnoId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "alumno_id es obligatorio.");
        }
        if (body.getTipoCondicion() == null || body.getTipoCondicion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_condicion es obligatorio.");
        }
        if (body.getDescripcion() == null || body.getDescripcion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "descripcion es obligatoria.");
        }
        if (body.getTelefonoMedico() != null && !body.getTelefonoMedico().isBlank()) {
            ValidationUtils.validarTelefono(body.getTelefonoMedico());
        }
        if (body.getDosis() != null && !body.getDosis().isBlank()) {
            if (!body.getDosis().matches("^\\d+(\\.\\d+)?(mg|g|ml|mcg|IU)$")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "dosis debe tener formato válido: número + unidad (mg, g, ml, mcg, IU). Ej: 500mg, 2.5ml");
            }
        }
        if (body.getFrecuencia() != null && !body.getFrecuencia().isBlank()) {
            if (!body.getFrecuencia().matches("^Cada \\d+ (horas|minutos|días)$")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "frecuencia debe estar en formato: 'Cada X horas/minutos/días'. Ej: 'Cada 8 horas'");
            }
        }
    }
}

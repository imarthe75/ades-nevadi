package mx.ades.modules.profesores;

import lombok.RequiredArgsConstructor;
import mx.ades.common.ValidationUtils;
import mx.ades.modules.admin.AdminWriteService;
import mx.ades.modules.profesores.domain.port.in.ActualizarProfesorUseCase;
import mx.ades.modules.profesores.domain.port.in.CrearProfesorUseCase;
import mx.ades.modules.profesores.domain.port.out.ProfesorRepositoryPort;
import mx.ades.modules.profesores.query.ProfesorQueryService;
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

/**
 * Adaptador REST para la gestión del personal docente.
 * Expone endpoints bajo /api/v1/profesores para listar profesores con filtros
 * (plantel, nivel, grado, grupo, búsqueda textual) con scoping automático por plantel,
 * obtener el detalle de un profesor, crear un nuevo registro docente, actualizar
 * datos parciales (persona + laborales) y reemplazar el registro completo.
 * Requiere JWT válido en los endpoints que modifican datos; el listado usa
 * {@code getEffectivePlantelId} para evitar acceso cross-plantel.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/profesores")
@RequiredArgsConstructor
public class ProfesorController {

    private final AdesUserService          userService;
    private final ProfesorQueryService     query;
    private final CrearProfesorUseCase     crear;
    private final ActualizarProfesorUseCase actualizar;
    private final ProfesorRepositoryPort   repositoryPort;
    private final AdminWriteService        adminWrite;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "nivel_id",   required = false) UUID nivelId,
            @RequestParam(name = "grado_id",   required = false) UUID gradoId,
            @RequestParam(name = "grupo_id",   required = false) UUID grupoId,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel, nivelId, gradoId, grupoId, buscar));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (auditoría 2026-07-15): el detalle incluye PII sensible del docente
        // (CURP, RFC, teléfono, email personal, fecha de nacimiento); antes no llamaba
        // a resolveUser() en absoluto — cualquier cuenta ADES autenticada (incl.
        // alumnos/padres) podía leer el expediente completo de cualquier profesor por id.
        AdesUser user = userService.resolveUser(jwt);
        Map<String, Object> profesor = query.obtener(id);
        // (Corregido 2026-07-16 — decisión explícita del usuario: umbral original `>1`
        // dejaba a ADMIN_PLANTEL sin restricción; ver AdesUserService#getEffectivePlantelId.)
        userService.verificarPlantel(user, (UUID) profesor.get("plantel_id"), "No puede consultar un profesor de otro plantel");
        return ResponseEntity.ok(profesor);
    }

    /**
     * Alta de profesor desde cero (persona nueva) — construido 2026-07-20. Antes este
     * endpoint deserializaba el body directo como la entidad JPA {@link Profesor}, que
     * exige un {@code persona_id} de una persona YA EXISTENTE; el formulario "Nuevo
     * profesor" del frontend nunca lo enviaba (no ofrece selección de persona, solo
     * captura nombre/apellidos/CURP inline), así que el alta fallaba el 100% de las
     * veces con "persona_id es requerido". Corregido con {@link CrearProfesorRequest}
     * (mismo patrón que {@code CrearAlumnoRequest}): crea la persona primero
     * ({@link AdminWriteService#insertPersona}) y luego el profesor referenciándola —
     * simétrico con el alta de alumno, que ya funciona así.
     */
    @PostMapping
    public ResponseEntity<Profesor> create(@RequestBody CrearProfesorRequest req, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        ValidationUtils.validarNombrePersona(req.nombre(), "El nombre");
        ValidationUtils.validarNombrePersona(req.apellidoPaterno(), "El apellido paterno");
        if (req.apellidoMaterno() != null && !req.apellidoMaterno().isBlank()) {
            ValidationUtils.validarNombrePersona(req.apellidoMaterno(), "El apellido materno");
        }
        ValidationUtils.validarCURP(req.curp());
        if (req.plantel_id() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es obligatorio");
        }
        UUID personaId = adminWrite.insertPersona(
                req.nombre().trim(),
                req.apellidoPaterno().trim(),
                req.apellidoMaterno() != null ? req.apellidoMaterno().trim() : null,
                req.curp().toUpperCase().trim(),
                null, null);
        var cmd = new CrearProfesorUseCase.Command(
                personaId,
                req.plantel_id(),
                req.numero_empleado(),
                req.tipo_contrato());
        return ResponseEntity.status(HttpStatus.CREATED).body(crear.crear(cmd));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        // BOLA fix: get() ya verifica que el profesor pertenezca al plantel del usuario
        // (auditoría 2026-07-15) pero patch() (misma PII: CURP/RFC/teléfono/email) no
        // tenía ese chequeo — un Director/Coordinador de un plantel podía editar el
        // expediente de un profesor de OTRO plantel con solo el UUID.
        requireMismoPlantel(user, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        return ResponseEntity.ok(actualizar.actualizar(
                new ActualizarProfesorUseCase.Command(id, per, lab)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Profesor> update(
            @PathVariable UUID id,
            @RequestBody Profesor update,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        // BOLA fix: mismo hallazgo que patch() — sin este chequeo un Director/Coordinador
        // podía reemplazar por completo el registro (incluido reasignar plantelId) de un
        // profesor de otro plantel.
        requireMismoPlantel(user, id);
        Profesor prof = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado"));
        prof.setNumeroEmpleado(update.getNumeroEmpleado());
        prof.setPersonaId(update.getPersonaId());
        prof.setPlantelId(update.getPlantelId());
        prof.setEstatusId(update.getEstatusId());
        prof.setTipoContrato(update.getTipoContrato());
        prof.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repositoryPort.save(prof));
    }

    /**
     * BOLA fix: helper compartido por patch()/update() con el mismo criterio que get()
     * (vía {@code AdesUserService#getEffectivePlantelId}/{@code verificarPlantel}) —
     * solo ADMIN_GLOBAL (nivelAcceso 0) mantiene alcance institucional real; el resto,
     * incluido ADMIN_PLANTEL, solo puede mutar profesores de su propio plantel.
     * (Corregido 2026-07-16 — decisión explícita del usuario: ver
     * AdesUserService#getEffectivePlantelId.)
     */
    private void requireMismoPlantel(AdesUser user, UUID id) {
        Map<String, Object> profesor;
        try {
            profesor = query.obtener(id);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado");
        }
        userService.verificarPlantel(user, (UUID) profesor.get("plantel_id"), "No puede modificar un profesor de otro plantel");
    }

    /**
     * Alta/edición del expediente docente es operación de personal escolar
     * (nivelAcceso &le;4). Padres/alumnos (nivelAcceso &gt;=5) no tienen razón
     * para modificar registros de personal — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}

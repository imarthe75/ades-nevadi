package mx.ades.modules.asistencias;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.asistencias.query.ClaseQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de clases (sesiones de asistencia de alumnos).
 * Expone endpoints bajo /api/v1/clases para listar, consultar, crear y actualizar
 * registros de clase, filtrables por grupo, materia, profesor y rango de fechas.
 * Incluye un endpoint auxiliar para obtener los alumnos esperados en una clase.
 * Las clases son la unidad sobre la que se registran asistencias PRESENTE/AUSENTE/TARDE.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/clases")
@RequiredArgsConstructor
public class ClaseController {

    private final ClaseService service;
    private final ClaseQueryService queryService;
    private final AdesUserService userService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "profesor_id", required = false) UUID profesorId,
            @RequestParam(value = "fecha_desde", required = false) LocalDate fechaDesde,
            @RequestParam(value = "fecha_hasta", required = false) LocalDate fechaHasta,
            @RequestParam(value = "estatus", required = false) String estatus,
            @AuthenticationPrincipal Jwt jwt) {
        // Antes no se llamaba a resolveUser en absoluto: cualquier cuenta autenticada
        // (incluidos alumnos/padres) podía listar TODAS las clases del sistema, de
        // cualquier plantel, sin ningún scoping (BOLA, OWASP API1).
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        UUID plantelId = (user.getNivelAcceso() != null && user.getNivelAcceso() > 1) ? user.getPlantelId() : null;
        return ResponseEntity.ok(queryService.listar(grupoId, materiaId, profesorId, fechaDesde, fechaHasta, estatus, plantelId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        Map<String, Object> result = queryService.obtener(id);
        verificarPlantelDeClase(user, result);
        return ResponseEntity.ok(result);
    }

    /** No-admins acotados a su propio plantel — evita leer una clase de otro plantel por id. */
    private void verificarPlantelDeClase(AdesUser user, Map<String, Object> clase) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            Object plantelId = clase.get("plantel_id");
            if (plantelId != null && !plantelId.equals(user.getPlantelId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede consultar una clase de otro plantel");
            }
        }
    }

    @PostMapping
    public ResponseEntity<Clase> crear(@RequestBody Clase clase, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(clase));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Clase> actualizar(
            @PathVariable("id") UUID id,
            @RequestBody Clase clase,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        return ResponseEntity.ok(service.actualizar(id, clase));
    }

    @GetMapping("/{id}/alumnos-esperados")
    public ResponseEntity<List<Map<String, Object>>> alumnosEsperados(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);
        verificarPlantelDeClase(user, queryService.obtener(id));
        return ResponseEntity.ok(service.alumnosEsperados(id));
    }

    /**
     * Crear/editar sesiones de clase (unidad sobre la que se registra asistencia)
     * es operación de personal escolar (nivelAcceso &le;4) — previene BFLA
     * (OWASP API5) sobre los registros de asistencia del grupo.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}

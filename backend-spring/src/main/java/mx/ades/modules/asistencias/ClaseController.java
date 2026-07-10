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
 * Las clases son la unidad sobre la que se registran asistencias PRESENTE/FALTA/TARDANZA.
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
            @RequestParam(value = "estatus", required = false) String estatus) {
        return ResponseEntity.ok(queryService.listar(grupoId, materiaId, profesorId, fechaDesde, fechaHasta, estatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(queryService.obtener(id));
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
    public ResponseEntity<List<Map<String, Object>>> alumnosEsperados(@PathVariable("id") UUID id) {
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

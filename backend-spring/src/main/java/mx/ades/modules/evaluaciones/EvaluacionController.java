package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import mx.ades.modules.evaluaciones.query.EvaluacionQueryService;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para la gestión de evaluaciones (exámenes, tareas, proyectos).
 * Expone endpoints bajo /api/v1/evaluaciones para listar, crear, actualizar y
 * consultar evaluaciones por grupo. El endpoint /{id}/calificaciones/bulk permite
 * registrar calificaciones masivas de alumnos en una sola operación via
 * {@code CalificarEvaluacionMasivoUseCase}. Las evaluaciones tienen tipo, puntaje
 * máximo, periodo y fecha. El endpoint GET /{id}/calificaciones retorna las
 * calificaciones individuales con scoping por grupo. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/evaluaciones")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionRepository repository;
    private final AdesUserService userService;
    private final CalificarEvaluacionMasivoUseCase calificarEvaluacionMasivo;
    private final EvaluacionQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, cicloId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Evaluacion> get(@PathVariable("id") UUID id) {
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));
        return ResponseEntity.ok(eval);
    }

    @GetMapping("/{id}/calificaciones")
    public ResponseEntity<List<Map<String, Object>>> calificaciones(
            @PathVariable("id") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        UUID grupoId = queryService.grupoIdDeEvaluacion(evalId);
        return ResponseEntity.ok(queryService.calificacionesPorEvaluacion(evalId, grupoId));
    }

    @PostMapping("/{id}/calificaciones/bulk")
    public ResponseEntity<Map<String, Object>> bulkCalificaciones(
            @PathVariable("id") UUID evalId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);

        Object cicloIdObj = body.get("ciclo_id");
        if (cicloIdObj != null) {
            UUID cicloId = UUID.fromString(cicloIdObj.toString());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawCalifs = (List<Map<String, Object>>) body.get("calificaciones");
        if (rawCalifs == null || rawCalifs.isEmpty()) {
            return ResponseEntity.ok(Map.of("updated", 0));
        }

        List<CalificarEvaluacionMasivoUseCase.EntradaCalificacion> entradas = new ArrayList<>();
        for (Map<String, Object> c : rawCalifs) {
            Object calVal = c.get("calificacion");
            if (calVal == null) continue;
            entradas.add(new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(
                    UUID.fromString(c.get("estudiante_id").toString()),
                    Double.parseDouble(calVal.toString()),
                    (String) c.get("comentarios")));
        }

        int updated = calificarEvaluacionMasivo.ejecutar(
                new CalificarEvaluacionMasivoUseCase.Command(evalId, entradas, user.getUsername()));

        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping
    public ResponseEntity<Evaluacion> create(
            @RequestBody Evaluacion evaluacion,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (evaluacion.getNombreEvaluacion() == null || evaluacion.getNombreEvaluacion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nombre_evaluacion es obligatorio");
        }
        if (evaluacion.getGrupoId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "grupo_id es obligatorio");
        }
        if (evaluacion.getFechaEvaluacion() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fecha_evaluacion es obligatoria");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(evaluacion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Evaluacion> update(
            @PathVariable("id") UUID id,
            @RequestBody Evaluacion update,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));

        eval.setNombreEvaluacion(update.getNombreEvaluacion());
        eval.setDescripcion(update.getDescripcion());
        eval.setGrupoId(update.getGrupoId());
        eval.setMateriaId(update.getMateriaId());
        eval.setPeriodoEvaluacionId(update.getPeriodoEvaluacionId());
        eval.setFechaEvaluacion(update.getFechaEvaluacion());
        eval.setTipoEvaluacion(update.getTipoEvaluacion());
        eval.setPuntajeMaximo(update.getPuntajeMaximo());
        eval.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(eval));
    }
}

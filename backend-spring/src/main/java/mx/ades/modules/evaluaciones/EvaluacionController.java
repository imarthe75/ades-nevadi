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

@RestController
@RequestMapping("/api/v1/evaluaciones")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionRepository repository;
    private final AdesUserService userService;
    private final CalificarEvaluacionMasivoUseCase calificarMasivoUseCase;
    private final EvaluacionQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId));
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

        int updated = calificarMasivoUseCase.ejecutar(
                new CalificarEvaluacionMasivoUseCase.Command(evalId, entradas, user.getUsername()));

        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping
    public ResponseEntity<Evaluacion> create(@RequestBody Evaluacion evaluacion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(evaluacion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Evaluacion> update(
            @PathVariable("id") UUID id,
            @RequestBody Evaluacion update) {
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

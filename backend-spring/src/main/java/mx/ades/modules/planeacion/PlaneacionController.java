package mx.ades.modules.planeacion;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/planeacion")
@RequiredArgsConstructor
public class PlaneacionController {

    private final PlaneacionService planeacionService;

    @GetMapping("/temas")
    public ResponseEntity<List<Map<String, Object>>> temasConEstado(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(planeacionService.getTemasConEstado(grupoId, materiaId));
    }

    @GetMapping("/cobertura/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> coberturaGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(planeacionService.getCoberturaGrupo(grupoId));
    }

    @GetMapping("/clases")
    public ResponseEntity<List<Map<String, Object>>> listarPlaneacion(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(planeacionService.getListarPlaneacion(grupoId, materiaId));
    }

    public record PlaneacionCreateRequest(
            UUID grupo_id,
            UUID tema_id,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_planeada,
            String descripcion_actividades,
            String recursos_didacticos
    ) {}

    @PostMapping("/clases")
    public ResponseEntity<Map<String, Object>> crearPlaneacion(
            @RequestBody PlaneacionCreateRequest body) {
        Map<String, Object> res = planeacionService.crearPlaneacion(
                body.grupo_id(),
                body.tema_id(),
                body.fecha_planeada(),
                body.descripcion_actividades(),
                body.recursos_didacticos()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    public record CompletarAvanceRequest(
            UUID clase_id,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_ejecucion,
            String comentarios_profesor
    ) {}

    @PostMapping("/clases/{planeacion_id}/completar")
    public ResponseEntity<Map<String, Object>> completarTema(
            @PathVariable("planeacion_id") UUID planeacionId,
            @RequestBody CompletarAvanceRequest body) {
        Map<String, Object> res = planeacionService.completarTema(
                planeacionId,
                body.clase_id(),
                body.fecha_ejecucion(),
                body.comentarios_profesor()
        );
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/clases/{planeacion_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPlaneacion(@PathVariable("planeacion_id") UUID planeacionId) {
        planeacionService.eliminarPlaneacion(planeacionId);
    }

    @GetMapping("/alertas-rezago/{ciclo_id}")
    public ResponseEntity<Map<String, Object>> alertasRezago(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam(value = "umbral_pct", defaultValue = "80.0") Double umbralPct) {
        return ResponseEntity.ok(planeacionService.getAlertasRezago(cicloId, umbralPct));
    }

    @GetMapping("/semana/{grupo_id}")
    public ResponseEntity<Map<String, Object>> planSemana(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam("fecha_inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio) {
        return ResponseEntity.ok(planeacionService.getPlanSemana(grupoId, fechaInicio));
    }

    @GetMapping("/insights/{grupo_id}")
    public ResponseEntity<Map<String, Object>> insightsGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(planeacionService.getInsightsGrupo(grupoId));
    }
}

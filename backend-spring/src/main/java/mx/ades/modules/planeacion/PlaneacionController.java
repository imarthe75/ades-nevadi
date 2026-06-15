package mx.ades.modules.planeacion;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planeacion.command.PlaneacionCommandService;
import mx.ades.modules.planeacion.query.PlaneacionQueryService;
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

    private final PlaneacionQueryService   queries;
    private final PlaneacionCommandService commands;

    @GetMapping("/temas")
    public ResponseEntity<List<Map<String, Object>>> temasConEstado(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(queries.getTemasConEstado(grupoId, materiaId));
    }

    @GetMapping("/cobertura/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> coberturaGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getCoberturaGrupo(grupoId));
    }

    @GetMapping("/clases")
    public ResponseEntity<List<Map<String, Object>>> listarPlaneacion(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(queries.getListarPlaneacion(grupoId, materiaId));
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
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearPlaneacion(body.grupo_id(), body.tema_id(),
                        body.fecha_planeada(), body.descripcion_actividades(),
                        body.recursos_didacticos()));
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
        return ResponseEntity.ok(commands.completarTema(
                planeacionId, body.clase_id(), body.fecha_ejecucion(),
                body.comentarios_profesor()));
    }

    @DeleteMapping("/clases/{planeacion_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPlaneacion(@PathVariable("planeacion_id") UUID planeacionId) {
        commands.eliminarPlaneacion(planeacionId);
    }

    @GetMapping("/alertas-rezago/{ciclo_id}")
    public ResponseEntity<Map<String, Object>> alertasRezago(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam(value = "umbral_pct", defaultValue = "80.0") Double umbralPct) {
        return ResponseEntity.ok(queries.getAlertasRezago(cicloId, umbralPct));
    }

    @GetMapping("/semana/{grupo_id}")
    public ResponseEntity<Map<String, Object>> planSemana(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam("fecha_inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio) {
        return ResponseEntity.ok(queries.getPlanSemana(grupoId, fechaInicio));
    }

    @GetMapping("/insights/{grupo_id}")
    public ResponseEntity<Map<String, Object>> insightsGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getInsightsGrupo(grupoId));
    }
}

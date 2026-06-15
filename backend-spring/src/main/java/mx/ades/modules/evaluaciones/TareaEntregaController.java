package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/entregas")
@RequiredArgsConstructor
public class TareaEntregaController {

    private final TareaEntregaService service;

    @GetMapping("/alumno/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> entregasDelAlumno(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "solo_pendientes", defaultValue = "false") Boolean soloPendientes) {
        return ResponseEntity.ok(service.getEntregasDelAlumno(alumnoId, periodoId, materiaId, soloPendientes));
    }

    @GetMapping("/pendientes/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> pendientesDelGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(service.getPendientesDelGrupo(grupoId, materiaId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> subirEntrega(
            @RequestParam("tarea_id") UUID tareaId,
            @RequestParam("alumno_id") UUID alumnoId,
            @RequestParam(value = "comentario", required = false) String comentario,
            @RequestParam(value = "archivo", required = false) MultipartFile file) {
        Map<String, Object> res = service.subirEntrega(tareaId, alumnoId, comentario, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    public record CalificarRequest(
            BigDecimal calificacion,
            String comentario
    ) {}

    @PatchMapping("/{entrega_id}/calificar")
    public ResponseEntity<Map<String, Object>> calificarEntrega(
            @PathVariable("entrega_id") UUID entregaId,
            @RequestBody CalificarRequest body) {
        String principalSub = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        UUID userId = service.getJdbcTemplate().queryForObject(
                "SELECT id FROM ades_usuarios WHERE oidc_sub = ?", UUID.class, principalSub);

        service.calificarEntrega(entregaId, body.calificacion(), body.comentario(), userId);
        return ResponseEntity.ok(Map.of("message", "Calificación registrada"));
    }

    @PostMapping("/{entrega_id}/excusa")
    public ResponseEntity<Map<String, Object>> registrarExcusa(
            @PathVariable("entrega_id") UUID entregaId,
            @RequestParam("motivo") String motivo) {
        service.registrarExcusa(entregaId, motivo);
        return ResponseEntity.ok(Map.of("message", "Excusa registrada"));
    }
}

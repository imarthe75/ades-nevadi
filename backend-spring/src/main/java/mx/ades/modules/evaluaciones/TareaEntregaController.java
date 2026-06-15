package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.query.TareaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final TareaQueryService   query;
    private final AdesUserService     userService;

    @GetMapping("/alumno/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> entregasDelAlumno(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "solo_pendientes", defaultValue = "false") Boolean soloPendientes) {
        return ResponseEntity.ok(query.entregasDelAlumno(alumnoId, periodoId, materiaId, soloPendientes));
    }

    @GetMapping("/pendientes/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> pendientesDelGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(query.pendientesDelGrupo(grupoId, materiaId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> subirEntrega(
            @RequestParam("tarea_id") UUID tareaId,
            @RequestParam("alumno_id") UUID alumnoId,
            @RequestParam(value = "comentario", required = false) String comentario,
            @RequestParam(value = "archivo", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.subirEntrega(tareaId, alumnoId, comentario, file));
    }

    public record CalificarRequest(BigDecimal calificacion, String comentario) {
    }

    @PatchMapping("/{entrega_id}/calificar")
    public ResponseEntity<Map<String, Object>> calificarEntrega(
            @PathVariable("entrega_id") UUID entregaId,
            @RequestBody CalificarRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        service.calificarEntrega(entregaId, body.calificacion(), body.comentario(), user.getPersonaId());
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

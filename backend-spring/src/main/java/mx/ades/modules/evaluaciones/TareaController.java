package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService service;

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> actividadesDeGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem) {
        return ResponseEntity.ok(service.getActividadesDeGrupo(grupoId, materiaId, periodoId, tipoItem));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(@RequestBody Tarea tarea) {
        Map<String, Object> response = service.crearActividad(tarea);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{actividad_id}/entregas")
    public ResponseEntity<List<Map<String, Object>>> entregasDeActividad(
            @PathVariable("actividad_id") UUID actividadId) {
        return ResponseEntity.ok(service.getEntregasDeActividad(actividadId));
    }

    public record CalificarMasivoRequest(
            List<TareaService.CalificarMasivoItem> items
    ) {}

    @PatchMapping("/{actividad_id}/calificar-masivo")
    public ResponseEntity<Map<String, Object>> calificarMasivo(
            @PathVariable("actividad_id") UUID actividadId,
            @RequestBody CalificarMasivoRequest request) {
        // Authenticated user ID resolution logic. For simplicity, we query ades_usuarios.id in service but we need oidcSub.
        // Let's resolve the user from db using Spring context sub inside controller or pass it down.
        // For local development or mock, we can fetch from SecurityContext or default. Let's do it cleanly:
        String principalSub = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        UUID userId = service.getJdbcTemplate().queryForObject(
                "SELECT id FROM ades_usuarios WHERE oidc_sub = ?", UUID.class, principalSub);

        int actualizados = service.calificarMasivo(actividadId, request.items(), userId);
        return ResponseEntity.ok(Map.of("actualizados", actualizados));
    }
}

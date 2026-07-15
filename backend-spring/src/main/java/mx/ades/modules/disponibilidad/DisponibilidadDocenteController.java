package mx.ades.modules.disponibilidad;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.disponibilidad.domain.port.in.EliminarSlotUseCase;
import mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase;
import mx.ades.modules.disponibilidad.query.DisponibilidadQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión de disponibilidad horaria de docentes.
 * Expone endpoints bajo /api/v1/disponibilidad para registrar (PUT bulk) y
 * consultar slots de disponibilidad semanal por profesor y ciclo escolar.
 * Soporta marcado de slots no disponibles con motivo, horas semanales máximas
 * y horas frente a grupo. El endpoint /cobertura/{cicloId} muestra qué docentes
 * tienen disponibilidad configurada. Utilizado por el módulo de horarios (aSc)
 * para generación y validación de asignaciones. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadDocenteController {

    private final AdesUserService           userService;
    private final GuardarDisponibilidadUseCase guardarDisponibilidad;
    private final EliminarSlotUseCase       eliminarSlot;
    private final DisponibilidadQueryService query;

    @Data
    public static class SlotIn {
        private Integer diaSemana;
        private LocalTime horaInicio;
        private LocalTime horaFin;
        private Boolean disponible = true;
        private String motivoNoDisponible;
    }

    @Data
    public static class BulkDisponibilidadIn {
        private List<SlotIn> slots;
        private UUID cicloEscolarId;
        private Double horasSemanaMax;
        private Double horasFrenteGrupo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "profesor_id", required = false) UUID profesorId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.listar(profesorId, cicloEscolarId, q));
    }

    @PutMapping("/docente/{profesorId}")
    public ResponseEntity<Map<String, Object>> guardar(
            @PathVariable("profesorId") UUID profesorId,
            @RequestBody BulkDisponibilidadIn data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<GuardarDisponibilidadUseCase.Slot> slots = data.getSlots().stream()
                .map(s -> new GuardarDisponibilidadUseCase.Slot(
                        s.getDiaSemana(), s.getHoraInicio(), s.getHoraFin(),
                        s.getDisponible(), s.getMotivoNoDisponible()))
                .toList();
        var cmd = new GuardarDisponibilidadUseCase.Command(
                profesorId, data.getCicloEscolarId(), slots,
                data.getHorasSemanaMax(), data.getHorasFrenteGrupo(), user.getUsername());
        guardarDisponibilidad.guardar(cmd);
        return ResponseEntity.ok(Map.of("detail", slots.size() + " slots guardados para docente " + profesorId));
    }

    @GetMapping("/docente/{profesorId}/resumen")
    public ResponseEntity<Map<String, Object>> resumen(
            @PathVariable("profesorId") UUID profesorId,
            @RequestParam(value = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.resumen(profesorId, cicloEscolarId));
    }

    @GetMapping("/cobertura/{cicloId}")
    public ResponseEntity<List<Map<String, Object>>> cobertura(
            @PathVariable("cicloId") UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(query.cobertura(cicloId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        eliminarSlot.eliminar(id);
    }
}

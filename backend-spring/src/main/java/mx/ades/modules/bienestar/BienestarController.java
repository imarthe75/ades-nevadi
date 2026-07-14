package mx.ades.modules.bienestar;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para eventos de bienestar institucional (SB-023) — día de la
 * amabilidad, actividades lúdicas, talleres. CRUD simple con scoping por
 * plantel para no-admins, consistente con el checklist de seguridad del proyecto.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/bienestar/eventos")
@RequiredArgsConstructor
public class BienestarController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final java.util.Set<String> TIPOS_VALIDOS =
            java.util.Set.of("ACTIVIDAD_LUDICA", "DIA_TEMATICO", "TALLER_BIENESTAR", "OTRO");

    @Data
    public static class EventoRequest {
        private String titulo;
        private String descripcion;
        private LocalDate fecha;
        private UUID plantelId;
        private String tipo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);

        if (effectivePlantel != null) {
            return ResponseEntity.ok(jdbc.queryForList("""
                SELECT e.id, e.titulo, e.descripcion, e.fecha, e.tipo, e.participantes_count,
                       p.nombre_plantel
                FROM ades_eventos_bienestar e
                LEFT JOIN ades_planteles p ON p.id = e.plantel_id
                WHERE e.is_active = TRUE AND (e.plantel_id = ? OR e.plantel_id IS NULL)
                ORDER BY e.fecha DESC
                """, effectivePlantel));
        }
        return ResponseEntity.ok(jdbc.queryForList("""
            SELECT e.id, e.titulo, e.descripcion, e.fecha, e.tipo, e.participantes_count,
                   p.nombre_plantel
            FROM ades_eventos_bienestar e
            LEFT JOIN ades_planteles p ON p.id = e.plantel_id
            WHERE e.is_active = TRUE
            ORDER BY e.fecha DESC
            """));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody EventoRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (body.getTitulo() == null || body.getTitulo().isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El título es obligatorio");
        if (body.getFecha() == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La fecha es obligatoria");
        // Espejo del CHECK ades_eventos_bienestar_tipo_check — sin este chequeo, un tipo
        // fuera del enum llegaba hasta el INSERT y violaba el CHECK constraint (409 genérico).
        if (body.getTipo() != null && !TIPOS_VALIDOS.contains(body.getTipo()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "tipo inválido. Valores permitidos: " + TIPOS_VALIDOS);

        UUID plantelId = body.getPlantelId() != null ? body.getPlantelId() : user.getPlantelId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_eventos_bienestar (id, titulo, descripcion, fecha, plantel_id, tipo)
            VALUES (?, ?, ?, ?, ?, COALESCE(?, 'ACTIVIDAD_LUDICA'))
            """, id, body.getTitulo(), body.getDescripcion(), body.getFecha(), plantelId, body.getTipo());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString()));
    }

    @PatchMapping("/{id}/participantes")
    @Transactional
    public ResponseEntity<Map<String, Object>> actualizarParticipantes(
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Integer count = body.get("participantes_count");
        if (count == null || count < 0)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "participantes_count inválido");
        int rows = jdbc.update("UPDATE ades_eventos_bienestar SET participantes_count = ? WHERE id = ?", count, id);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        return ResponseEntity.ok(Map.of("id", id.toString(), "participantes_count", count));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int rows = jdbc.update("UPDATE ades_eventos_bienestar SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        return ResponseEntity.noContent().build();
    }
}

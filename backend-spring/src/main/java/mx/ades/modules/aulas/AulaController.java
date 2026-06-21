package mx.ades.modules.aulas;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.aulas.domain.port.in.ActualizarAulaUseCase;
import mx.ades.modules.aulas.domain.port.in.CrearAulaUseCase;
import mx.ades.modules.aulas.domain.port.out.AulaRepositoryPort;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final CrearAulaUseCase crearUseCase;
    private final ActualizarAulaUseCase actualizarUseCase;
    private final AulaRepositoryPort repositoryPort;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Aula>> list(@RequestParam(name = "plantel_id", required = false) UUID plantelId) {
        return ResponseEntity.ok(plantelId != null
                ? repositoryPort.findByPlantelId(plantelId)
                : repositoryPort.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable("id") UUID id) {
        Aula aula = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada"));

        List<Map<String, Object>> franjas = jdbc.queryForList(
            "SELECT id, dia_semana, hora_inicio::text, hora_fin::text, motivo_bloqueo, is_active " +
            "FROM ades_disponibilidad_aula WHERE aula_id = ? AND is_active = true ORDER BY dia_semana, hora_inicio",
            id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", aula.getId());
        result.put("nombre_aula", aula.getNombreAula());
        result.put("plantel_id", aula.getPlantelId());
        result.put("tipo_aula", aula.getTipoAula());
        result.put("capacidad_alumnos", aula.getCapacidadAlumnos());
        result.put("is_active", aula.getIsActive());
        result.put("clave_aula", aula.getClaveAula());
        result.put("piso", aula.getPiso());
        result.put("edificio", aula.getEdificio());
        result.put("capacidad_maxima", aula.getCapacidadMaxima());
        result.put("tiene_proyector", aula.isTieneProyector());
        result.put("tiene_pizarra_digital", aula.isTienePizarraDigital());
        result.put("tiene_pizarron", aula.isTienePizarron());
        result.put("tiene_aire_acondicionado", aula.isTieneAireAcondicionado());
        result.put("tiene_ventiladores", aula.isTieneVentiladores());
        result.put("tiene_internet", aula.isTieneInternet());
        result.put("num_computadoras", aula.getNumComputadoras());
        result.put("estado_aula", aula.getEstadoAula());
        result.put("observaciones", aula.getObservaciones());
        result.put("franjas", franjas);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        CrearAulaUseCase.Command cmd = buildCrearCmd(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(crearUseCase.crear(cmd));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(actualizarUseCase.actualizar(buildActualizarCmd(id, body)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(actualizarUseCase.actualizar(buildActualizarCmd(id, body)));
    }

    /** POST /api/v1/aulas/{id}/disponibilidad — agrega una franja de disponibilidad */
    @PostMapping("/{id}/disponibilidad")
    public ResponseEntity<Map<String, Object>> agregarFranja(
            @PathVariable("id") UUID aulaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (!repositoryPort.findById(aulaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aula no encontrada");
        }
        Integer dia = body.get("dia_semana") != null ? ((Number) body.get("dia_semana")).intValue() : null;
        String horaInicio = (String) body.get("hora_inicio");
        String horaFin    = (String) body.get("hora_fin");
        String motivo     = (String) body.get("motivo_bloqueo");
        if (dia == null || horaInicio == null || horaFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dia_semana, hora_inicio y hora_fin son obligatorios");
        }
        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_disponibilidad_aula (id, aula_id, dia_semana, hora_inicio, hora_fin, motivo_bloqueo) " +
            "VALUES (?, ?, ?, ?::time, ?::time, ?)",
            newId, aulaId, dia, horaInicio, horaFin, motivo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", newId);
        result.put("aula_id", aulaId);
        result.put("dia_semana", dia);
        result.put("hora_inicio", horaInicio);
        result.put("hora_fin", horaFin);
        result.put("motivo_bloqueo", motivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** DELETE /api/v1/aulas/disponibilidad/{franjaId} — elimina una franja */
    @DeleteMapping("/disponibilidad/{franjaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarFranja(
            @PathVariable("franjaId") UUID franjaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        int rows = jdbc.update(
            "UPDATE ades_disponibilidad_aula SET is_active = false WHERE id = ?", franjaId);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Franja no encontrada");
    }

    /** POST /api/v1/aulas/{id}/verificar-conflicto — comprueba solapamientos */
    @PostMapping("/{id}/verificar-conflicto")
    public ResponseEntity<Map<String, Object>> verificarConflicto(
            @PathVariable("id") UUID aulaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Integer dia = body.get("dia_semana") != null ? ((Number) body.get("dia_semana")).intValue() : null;
        String horaInicio = (String) body.get("hora_inicio");
        String horaFin    = (String) body.get("hora_fin");
        if (dia == null || horaInicio == null || horaFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dia_semana, hora_inicio y hora_fin son obligatorios");
        }
        List<Map<String, Object>> conflictos = jdbc.queryForList(
            "SELECT id, dia_semana, hora_inicio::text, hora_fin::text, motivo_bloqueo " +
            "FROM ades_disponibilidad_aula " +
            "WHERE aula_id = ? AND dia_semana = ? AND is_active = true " +
            "  AND hora_inicio < ?::time AND hora_fin > ?::time",
            aulaId, dia, horaFin, horaInicio);
        boolean hayConflicto = !conflictos.isEmpty();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("conflicto", hayConflicto);
        resp.put("num_conflictos", conflictos.size());
        resp.put("conflictos", conflictos);
        resp.put("mensaje", hayConflicto ? "El aula ya tiene una franja en ese horario." : "Sin conflictos detectados.");
        return ResponseEntity.ok(resp);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CrearAulaUseCase.Command buildCrearCmd(Map<String, Object> b) {
        return new CrearAulaUseCase.Command(
            (String) b.get("nombre_aula"),
            b.get("plantel_id") != null ? UUID.fromString(b.get("plantel_id").toString()) : null,
            (String) b.get("tipo_aula"),
            b.get("capacidad_alumnos") != null ? ((Number) b.get("capacidad_alumnos")).intValue() : null
        );
    }

    private ActualizarAulaUseCase.Command buildActualizarCmd(UUID id, Map<String, Object> b) {
        return new ActualizarAulaUseCase.Command(
            id,
            (String) b.get("nombre_aula"),
            b.get("plantel_id") != null ? UUID.fromString(b.get("plantel_id").toString()) : null,
            (String) b.get("tipo_aula"),
            b.get("capacidad_alumnos") != null ? ((Number) b.get("capacidad_alumnos")).intValue() : null,
            b.get("is_active") != null ? Boolean.valueOf(b.get("is_active").toString()) : null,
            (String) b.get("clave_aula"),
            b.get("piso") != null ? ((Number) b.get("piso")).shortValue() : null,
            (String) b.get("edificio"),
            b.get("capacidad_maxima") != null ? ((Number) b.get("capacidad_maxima")).shortValue() : null,
            b.get("tiene_proyector") != null ? Boolean.valueOf(b.get("tiene_proyector").toString()) : null,
            b.get("tiene_pizarra_digital") != null ? Boolean.valueOf(b.get("tiene_pizarra_digital").toString()) : null,
            b.get("tiene_pizarron") != null ? Boolean.valueOf(b.get("tiene_pizarron").toString()) : null,
            b.get("tiene_aire_acondicionado") != null ? Boolean.valueOf(b.get("tiene_aire_acondicionado").toString()) : null,
            b.get("tiene_ventiladores") != null ? Boolean.valueOf(b.get("tiene_ventiladores").toString()) : null,
            b.get("tiene_internet") != null ? Boolean.valueOf(b.get("tiene_internet").toString()) : null,
            b.get("num_computadoras") != null ? ((Number) b.get("num_computadoras")).shortValue() : null,
            (String) b.get("estado_aula"),
            (String) b.get("observaciones")
        );
    }
}

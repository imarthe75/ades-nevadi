package mx.ades.modules.movilidad;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.movilidad.query.MovilidadQueryService;
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
import java.util.*;

@RestController
@RequestMapping("/api/v1/movilidad")
@RequiredArgsConstructor
public class MovilidadController {

    private final CambioGrupoRepository cambioGrupoRepository;
    private final BajaRepository bajaRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final MovilidadQueryService queryService;

    @Data
    public static class CambioGrupoRequest {
        private UUID grupoDestinoId;
        private String motivo;
        private UUID cicloEscolarId;
    }

    @Data
    public static class TrasladoRequest {
        private UUID plantelDestinoId;
        private UUID grupoDestinoId;
        private String motivo;
        private String plantelDestinoNombre;
        private String claveCtDestino;
    }

    @Data
    public static class BajaRequest {
        private String motivo;
        private LocalDate fechaEfectiva;
        private LocalDate fechaReingreso;
        private String plantelDestino;
        private String claveCtDestino;
        private String observaciones;
    }

    @Data
    public static class ReactivarRequest {
        private UUID grupoId;
        private UUID cicloEscolarId;
        private String observaciones;
    }

    private Map<String, Object> getInscripcionActiva(UUID estudianteId) {
        String sql = "SELECT i.id, i.grupo_id, i.ciclo_escolar_id, e.is_active as est_activo, " +
                "g.nombre_grupo, g.plantel_id, g.capacidad_maxima, " +
                "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = i.grupo_id AND is_active = TRUE) as inscritos " +
                "FROM ades_inscripciones i " +
                "JOIN ades_grupos g ON g.id = i.grupo_id " +
                "JOIN ades_estudiantes e ON e.id = i.estudiante_id " +
                "WHERE i.estudiante_id = ? AND i.is_active = TRUE " +
                "ORDER BY i.fecha_inscripcion DESC LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, estudianteId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El alumno no tiene inscripción activa");
        }
        return rows.get(0);
    }

    @PostMapping("/cambio-grupo/{estudiante_id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> cambioGrupo(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody CambioGrupoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Director o superior");
        }

        Map<String, Object> insc = getInscripcionActiva(estudianteId);
        UUID inscripcionId = (UUID) insc.get("id");
        UUID grupoOrigenId = (UUID) insc.get("grupo_id");

        // Validate destination group
        String destSql = "SELECT id, nombre_grupo, plantel_id, capacidad_maxima, " +
                "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) as inscritos " +
                "FROM ades_grupos WHERE id = ? AND is_active = TRUE";

        List<Map<String, Object>> destRows = jdbc.queryForList(destSql, body.getGrupoDestinoId(), body.getGrupoDestinoId());
        if (destRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo destino no encontrado o inactivo");
        }
        Map<String, Object> dest = destRows.get(0);
        long max = ((Number) dest.get("capacidad_maxima")).longValue();
        long inscritos = ((Number) dest.get("inscritos")).longValue();

        if (inscritos >= max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo destino lleno (" + inscritos + "/" + max + ")");
        }

        // Create change record
        CambioGrupo cg = new CambioGrupo();
        cg.setEstudianteId(estudianteId);
        cg.setInscripcionId(inscripcionId);
        cg.setGrupoOrigenId(grupoOrigenId);
        cg.setGrupoDestinoId(body.getGrupoDestinoId());
        cg.setMotivo(body.getMotivo());
        cg.setAutorizadoPorId(user.getId());
        cambioGrupoRepository.save(cg);

        // Update enrollment
        jdbc.update("UPDATE ades_inscripciones SET grupo_id = ?, usuario_modificacion = ? WHERE id = ?",
                body.getGrupoDestinoId(), user.getUsername(), inscripcionId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Cambio de grupo registrado",
                "grupo_anterior", insc.get("nombre_grupo"),
                "grupo_nuevo", dest.get("nombre_grupo")
        ));
    }

    @PostMapping("/traslado/{estudiante_id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> traslado(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody TrasladoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Admin Plantel o superior");
        }

        Map<String, Object> insc = getInscripcionActiva(estudianteId);
        UUID inscripcionId = (UUID) insc.get("id");

        // Deactivate current enrollment
        jdbc.update("UPDATE ades_inscripciones SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), inscripcionId);

        // Record withdrawal as TRASLADO
        Baja b = new Baja();
        b.setEstudianteId(estudianteId);
        b.setInscripcionId(inscripcionId);
        b.setTipoBaja("TRASLADO");
        b.setMotivo(body.getMotivo());
        b.setFechaEfectiva(LocalDate.now());
        b.setPlantelDestino(body.getPlantelDestinoNombre() != null ? body.getPlantelDestinoNombre() : body.getPlantelDestinoId().toString());
        b.setClaveCtDestino(body.getClaveCtDestino());
        b.setAutorizadoPorId(user.getId());
        bajaRepository.save(b);

        // If a new group within the system is specified, create new enrollment
        if (body.getGrupoDestinoId() != null) {
            List<UUID> activeCycles = jdbc.queryForList("SELECT id FROM ades_ciclos_escolares WHERE es_vigente = TRUE LIMIT 1", UUID.class);
            if (!activeCycles.isEmpty()) {
                jdbc.update("INSERT INTO ades_inscripciones (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, CURRENT_DATE, ?, ?)",
                        estudianteId, body.getGrupoDestinoId(), activeCycles.get(0), user.getUsername(), user.getUsername());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Traslado registrado exitosamente"));
    }

    @PostMapping("/baja-temporal/{estudiante_id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> bajaTemporal(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Director o superior");
        }

        Map<String, Object> insc = getInscripcionActiva(estudianteId);
        UUID inscripcionId = (UUID) insc.get("id");

        // Suspend current enrollment (is_active = FALSE)
        jdbc.update("UPDATE ades_inscripciones SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), inscripcionId);

        // Record drop
        Baja b = new Baja();
        b.setEstudianteId(estudianteId);
        b.setInscripcionId(inscripcionId);
        b.setTipoBaja("TEMPORAL");
        b.setMotivo(body.getMotivo());
        b.setFechaEfectiva(body.getFechaEfectiva() != null ? body.getFechaEfectiva() : LocalDate.now());
        b.setFechaReingreso(body.getFechaReingreso());
        b.setObservaciones(body.getObservaciones());
        b.setAutorizadoPorId(user.getId());
        bajaRepository.save(b);

        // Mark student as inactive
        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), estudianteId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Baja temporal registrada",
                "fecha_reingreso_estimada", body.getFechaReingreso() != null ? body.getFechaReingreso().toString() : null
        ));
    }

    @PostMapping("/baja-definitiva/{estudiante_id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> bajaDefinitiva(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody BajaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Admin Plantel o superior");
        }

        Map<String, Object> insc = getInscripcionActiva(estudianteId);
        UUID inscripcionId = (UUID) insc.get("id");

        // Deactivate enrollment
        jdbc.update("UPDATE ades_inscripciones SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), inscripcionId);

        // Record permanent withdrawal
        Baja b = new Baja();
        b.setEstudianteId(estudianteId);
        b.setInscripcionId(inscripcionId);
        b.setTipoBaja("DEFINITIVA");
        b.setMotivo(body.getMotivo());
        b.setFechaEfectiva(body.getFechaEfectiva() != null ? body.getFechaEfectiva() : LocalDate.now());
        b.setPlantelDestino(body.getPlantelDestino());
        b.setClaveCtDestino(body.getClaveCtDestino());
        b.setObservaciones(body.getObservaciones());
        b.setAutorizadoPorId(user.getId());
        bajaRepository.save(b);

        // Mark student as inactive
        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), estudianteId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Baja definitiva registrada"));
    }

    @PostMapping("/reactivar/{estudiante_id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> reactivar(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestBody ReactivarRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Director o superior");
        }

        // Verify active temporary drop
        List<UUID> activeDrops = jdbc.queryForList(
                "SELECT id FROM ades_bajas WHERE estudiante_id = ? AND tipo_baja = 'TEMPORAL' AND is_active = TRUE " +
                "ORDER BY fecha_efectiva DESC LIMIT 1", UUID.class, estudianteId);

        if (activeDrops.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El alumno no tiene baja temporal activa");
        }
        UUID dropId = activeDrops.get(0);

        // Reactivate student
        jdbc.update("UPDATE ades_estudiantes SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), estudianteId);

        // Close/deactivate temporary drop
        jdbc.update("UPDATE ades_bajas SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), dropId);

        // Create new enrollment
        jdbc.update("INSERT INTO ades_inscripciones (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, CURRENT_DATE, ?, ?)",
                estudianteId, body.getGrupoId(), body.getCicloEscolarId(), user.getUsername(), user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Alumno reactivado e inscrito en nuevo grupo"));
    }

    @GetMapping("/historial/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> historial(@PathVariable("estudiante_id") UUID estudianteId) {
        return ResponseEntity.ok(queryService.historial(estudianteId));
    }

    @GetMapping("/bajas")
    public ResponseEntity<List<Map<String, Object>>> listarBajas(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo_baja", required = false) String tipoBaja,
            @RequestParam(value = "solo_activas", defaultValue = "true") boolean soloActivas,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(queryService.listarBajas(plantelId, tipoBaja, soloActivas, skip, limit));
    }

    @GetMapping("/cambios-grupo")
    public ResponseEntity<List<Map<String, Object>>> listarCambiosGrupo(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID plantelFiltroUser = (user.getNivelAcceso() != null && user.getNivelAcceso() > 1)
                ? user.getPlantelId() : null;
        return ResponseEntity.ok(
                queryService.listarCambiosGrupo(estudianteId, plantelId, plantelFiltroUser, skip, limit));
    }
}

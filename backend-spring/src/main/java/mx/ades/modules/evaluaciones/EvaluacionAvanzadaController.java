package mx.ades.modules.evaluaciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.evaluaciones.domain.model.SlotHorario;
import mx.ades.modules.evaluaciones.domain.port.in.AsignarAulaHoraUseCase;
import mx.ades.modules.evaluaciones.query.EvaluacionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluacion-avanzada")
@RequiredArgsConstructor
public class EvaluacionAvanzadaController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final EscalaEvaluacionRepository escalaRepository;
    private final ObservacionPedagogicaRepository observacionRepository;
    private final NeeRepository neeRepository;
    private final AsignarAulaHoraUseCase asignarAulaHoraUseCase;
    private final EvaluacionQueryService queryService;

    // ── EV-012: Escalas Cualitativas ──────────────────────────────────────────

    @Data
    public static class EscalaCualitativaPayload {
        private String nombre;
        private String nivelEducativo;
        private String descripcion;
        private String valoresJson;
    }

    @GetMapping("/escalas-cualitativas")
    public ResponseEntity<List<EscalaEvaluacion>> listarEscalas(
            @RequestParam(value = "nivel_educativo", required = false) String nivelEducativo,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (nivelEducativo != null && !nivelEducativo.isBlank()) {
            return ResponseEntity.ok(escalaRepository.findByNivelEducativoAndIsActiveTrueOrderByNombre(nivelEducativo));
        }
        return ResponseEntity.ok(escalaRepository.findByIsActiveTrueOrderByNivelEducativoAscNombreAsc());
    }

    @PostMapping("/escalas-cualitativas")
    public ResponseEntity<Map<String, Object>> crearEscala(
            @RequestBody EscalaCualitativaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        EscalaEvaluacion escala = new EscalaEvaluacion();
        escala.setNombre(data.getNombre());
        escala.setNivelEducativo(data.getNivelEducativo());
        escala.setDescripcion(data.getDescripcion());
        escala.setValoresJson(data.getValoresJson());
        escala.setUsuarioCreacion(user.getUsername());
        escala.setUsuarioModificacion(user.getUsername());
        escalaRepository.save(escala);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", escala.getId().toString(), "message", "Escala creada"));
    }

    // ── EV-014: Actas SEP ─────────────────────────────────────────────────────

    @PostMapping("/actas-sep/{grupo_id}")
    public ResponseEntity<Map<String, Object>> generarActaSep(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam("periodo") String periodo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        List<Map<String, Object>> grpList = jdbc.queryForList(
            "SELECT id, nombre_grupo FROM ades_grupos WHERE id = ? AND is_active = TRUE", grupoId);
        if (grpList.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");

        List<Map<String, Object>> registros = queryService.actasSep(grupoId, periodo);
        long totalAlumnos = registros.stream()
                .map(r -> r.get("numero_control")).distinct().count();

        return ResponseEntity.ok(Map.of(
                "grupo", grpList.get(0), "periodo", periodo,
                "registros", registros, "total_alumnos", totalAlumnos,
                "message", "Datos para acta SEP generados. Use /api/v1/carbone para renderizar PDF."));
    }

    // ── EV-017: Observaciones Pedagógicas ────────────────────────────────────

    @Data
    public static class ObservacionPedagogicaPayload {
        private String observacion;
        private String periodo;
        private String tipo = "GENERAL";
    }

    @PostMapping("/observaciones/{alumno_id}")
    public ResponseEntity<Map<String, Object>> guardarObservaciones(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody ObservacionPedagogicaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 4);

        ObservacionPedagogica obs = new ObservacionPedagogica();
        obs.setAlumnoId(alumnoId);
        obs.setObservacion(data.getObservacion());
        obs.setPeriodo(data.getPeriodo());
        obs.setTipo(data.getTipo());
        obs.setAutorId(user.getPersonaId());
        obs.setUsuarioCreacion(user.getUsername());
        obs.setUsuarioModificacion(user.getUsername());
        observacionRepository.save(obs);

        return ResponseEntity.ok(Map.of("message", "Observación pedagógica registrada"));
    }

    @GetMapping("/observaciones/{alumno_id}")
    public ResponseEntity<List<ObservacionPedagogica>> listarObservaciones(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        if (tipo != null && !tipo.isBlank()) {
            return ResponseEntity.ok(observacionRepository.findByAlumnoIdAndTipoOrderByFechaCreacionDesc(alumnoId, tipo));
        }
        return ResponseEntity.ok(observacionRepository.findByAlumnoIdOrderByFechaCreacionDesc(alumnoId));
    }

    // ── EV-024: NEE ───────────────────────────────────────────────────────────

    @Data
    public static class NeePayload {
        private UUID alumnoId;
        private String tipoNee;
        private String descripcion;
        private String apoyosRequeridos;
        private String fechaDeteccion;
        private String profesionalDetecta;
        private Boolean activa = true;
    }

    @GetMapping("/nee")
    public ResponseEntity<List<Map<String, Object>>> listarNee(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo_nee", required = false) String tipoNee,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);
        return ResponseEntity.ok(queryService.listarNee(plantelId, tipoNee));
    }

    @PostMapping("/nee")
    public ResponseEntity<Map<String, Object>> registrarNee(
            @RequestBody NeePayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        Nee nee = new Nee();
        nee.setAlumnoId(data.getAlumnoId());
        nee.setTipoNee(data.getTipoNee());
        nee.setDescripcion(data.getDescripcion());
        nee.setApoyosRequeridos(data.getApoyosRequeridos());
        nee.setFechaDeteccion(data.getFechaDeteccion() != null ? LocalDate.parse(data.getFechaDeteccion()) : null);
        nee.setProfesionalDetecta(data.getProfesionalDetecta());
        nee.setActiva(data.getActiva());
        nee.setUsuarioCreacion(user.getUsername());
        nee.setUsuarioModificacion(user.getUsername());
        neeRepository.save(nee);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", nee.getId().toString(), "message", "NEE registrada"));
    }

    // ── EV-025: Asignación Aula/Hora (con detección de conflicto) ────────────

    @Data
    public static class AsignacionAulaHoraPayload {
        private UUID claseId;
        private UUID aulaId;
        private String fecha;
        private String horaInicio;
        private String horaFin;
        private String observaciones;
    }

    @PostMapping("/asignacion-aula-hora")
    public ResponseEntity<Map<String, Object>> asignarAulaHora(
            @RequestBody AsignacionAulaHoraPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 3);

        UUID asignacionId = asignarAulaHoraUseCase.ejecutar(new AsignarAulaHoraUseCase.Command(
                data.getClaseId(), data.getAulaId(),
                LocalDate.parse(data.getFecha()),
                SlotHorario.of(data.getHoraInicio(), data.getHoraFin()),
                data.getObservaciones(), user.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", asignacionId, "message", "Aula asignada para la fecha y hora indicadas"));
    }

    @GetMapping("/asignacion-aula-hora")
    public ResponseEntity<List<Map<String, Object>>> consultarAsignacionesAula(
            @RequestParam(value = "aula_id", required = false) UUID aulaId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        LocalDate fecha = fechaStr != null && !fechaStr.isBlank() ? LocalDate.parse(fechaStr) : null;
        return ResponseEntity.ok(queryService.listarAsignacionesAula(aulaId, fecha));
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void requireNivel(AdesUser user, int maxNivel) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > maxNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }
}

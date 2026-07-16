package mx.ades.modules.evaluaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * Adaptador REST para funcionalidades avanzadas de evaluación pedagógica.
 * Expone endpoints bajo /api/v1/evaluacion-avanzada para: gestión de escalas
 * cualitativas de evaluación (EV-012), generación de datos de actas SEP por grupo
 * y periodo (EV-014), registro y consulta de observaciones pedagógicas por alumno
 * (EV-017), gestión de Necesidades Educativas Especiales (NEE, EV-024) y asignación
 * de aula/hora a clases con detección de conflictos de horario (EV-025).
 * Las mutaciones requieren Coordinador o superior (nivelAcceso {@literal <=} 3 o 4).
 * Aplica {@code resolveUser} en todos los endpoints protegidos.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/evaluacion-avanzada")
@RequiredArgsConstructor
public class EvaluacionAvanzadaController {

    private final AdesUserService userService;
    private final EscalaEvaluacionRepository escalaRepository;
    private final ObservacionPedagogicaRepository observacionRepository;
    private final NeeRepository neeRepository;
    private final AsignarAulaHoraUseCase asignarAulaHora;
    private final EvaluacionQueryService queryService;

    // ── EV-012: Escalas Cualitativas ──────────────────────────────────────────

    @Data
    public static class EscalaCualitativaPayload {
        @NotBlank(message = "nombre es obligatorio")
        private String nombre;

        // Debe coincidir EXACTAMENTE con el CHECK de ades_escalas_evaluacion.nivel_educativo
        @NotBlank(message = "nivelEducativo es obligatorio")
        @Pattern(regexp = "PRIMARIA|SECUNDARIA|PREPARATORIA",
                message = "nivelEducativo debe ser PRIMARIA, SECUNDARIA o PREPARATORIA")
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
            @RequestBody @Valid EscalaCualitativaPayload data,
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

        List<Map<String, Object>> grpList = queryService.fetchGrupo(grupoId);
        if (grpList.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        // BOLA fix: acta SEP contiene calificaciones y asistencia de todo el grupo.
        // (Corregido 2026-07-16: el umbral original `> 2` dejaba a Director y
        // Admin_Plantel sin restricción cross-plantel; ahora usa el helper
        // compartido, mismo criterio que Kardex/PersonalAdmin/Badge/Capacitacion.)
        userService.verificarPlantel(user, (UUID) grpList.get(0).get("plantel_id"), "El grupo no pertenece a su plantel");

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
        @NotBlank(message = "observacion es requerida")
        private String observacion;
        private String periodo;
        private String tipo = "GENERAL";
    }

    @PostMapping("/observaciones/{alumno_id}")
    public ResponseEntity<Map<String, Object>> guardarObservaciones(
            @PathVariable("alumno_id") UUID alumnoId,
            @Valid @RequestBody ObservacionPedagogicaPayload data,
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
        // BFLA fix: la lectura de observaciones pedagógicas es tan sensible como su
        // creación (POST ya exige nivelAcceso<=4); antes solo llamaba resolveUser() sin
        // verificar rol, permitiendo a padres/alumnos (nivelAcceso=5) leer observaciones
        // pedagógicas de CUALQUIER alumno por path param.
        AdesUser user = userService.resolveUser(jwt);
        requireNivel(user, 4);
        if (tipo != null && !tipo.isBlank()) {
            return ResponseEntity.ok(observacionRepository.findByAlumnoIdAndTipoOrderByFechaCreacionDesc(alumnoId, tipo));
        }
        return ResponseEntity.ok(observacionRepository.findByAlumnoIdOrderByFechaCreacionDesc(alumnoId));
    }

    // ── EV-024: NEE ───────────────────────────────────────────────────────────

    @Data
    public static class NeePayload {
        @NotNull(message = "alumno_id es requerido")
        private UUID alumnoId;
        @NotBlank(message = "tipo_nee es requerido")
        private String tipoNee;
        @NotBlank(message = "descripcion es requerida")
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
        // BOLA fix: NEE (necesidades educativas especiales) es dato de salud/educativo
        // sensible. (Corregido 2026-07-16: el umbral original `> 2` dejaba a Director y
        // Admin_Plantel sin restricción — solo nivelAcceso 0/ADMIN_GLOBAL mantiene
        // alcance institucional real, mismo criterio que el resto de los módulos.)
        UUID plantel = (user.getNivelAcceso() != null && user.getNivelAcceso() > 0 && user.getPlantelId() != null)
                ? user.getPlantelId() : plantelId;
        return ResponseEntity.ok(queryService.listarNee(plantel, tipoNee));
    }

    @PostMapping("/nee")
    public ResponseEntity<Map<String, Object>> registrarNee(
            @Valid @RequestBody NeePayload data,
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

        if (data.getFecha() == null || data.getFecha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha es requerida");
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(data.getFecha());
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha con formato inválido (esperado YYYY-MM-DD)");
        }

        UUID asignacionId = asignarAulaHora.ejecutar(new AsignarAulaHoraUseCase.Command(
                data.getClaseId(), data.getAulaId(),
                fecha,
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
        AdesUser user = userService.resolveUser(jwt);
        LocalDate fecha = fechaStr != null && !fechaStr.isBlank() ? LocalDate.parse(fechaStr) : null;
        // BOLA fix (asimetría): mismo criterio que listarNee() — solo ADMIN_GLOBAL
        // (nivelAcceso 0) mantiene alcance institucional real.
        UUID plantel = (user.getNivelAcceso() != null && user.getNivelAcceso() > 0 && user.getPlantelId() != null)
                ? user.getPlantelId() : null;
        return ResponseEntity.ok(queryService.listarAsignacionesAula(aulaId, fecha, plantel));
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void requireNivel(AdesUser user, int maxNivel) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > maxNivel) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }
}

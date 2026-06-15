package mx.ades.modules.evaluaciones;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluacion-avanzada")
@RequiredArgsConstructor
@Slf4j
public class EvaluacionAvanzadaController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final EscalaEvaluacionRepository escalaRepository;
    private final ObservacionPedagogicaRepository observacionRepository;
    private final NeeRepository neeRepository;
    private final AsignacionAulaRepository asignacionRepository;

    @Data
    public static class EscalaCualitativaPayload {
        private String nombre;
        private String nivelEducativo;
        private String descripcion;
        private String valoresJson;
    }

    @Data
    public static class ObservacionPedagogicaPayload {
        private String observacion;
        private String periodo;
        private String tipo = "GENERAL";
    }

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

    @Data
    public static class AsignacionAulaHoraPayload {
        private UUID claseId;
        private UUID aulaId;
        private String fecha;
        private String horaInicio;
        private String horaFin;
        private String observaciones;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EV-012: Escalas Cualitativas
    // ──────────────────────────────────────────────────────────────────────────
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
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Coordinador o superior");
        }

        EscalaEvaluacion escala = new EscalaEvaluacion();
        escala.setNombre(data.getNombre());
        escala.setNivelEducativo(data.getNivelEducativo());
        escala.setDescripcion(data.getDescripcion());
        escala.setValoresJson(data.getValoresJson());
        escala.setUsuarioCreacion(user.getUsername());
        escala.setUsuarioModificacion(user.getUsername());

        escalaRepository.save(escala);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", escala.getId().toString(), "message", "Escala creada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EV-014: Actas SEP
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/actas-sep/{grupo_id}")
    public ResponseEntity<Map<String, Object>> generarActaSep(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam("periodo") String periodo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Coordinador o superior");
        }

        // Verify group exists and is active
        List<Map<String, Object>> grpList = jdbc.queryForList(
                "SELECT id, nombre_grupo FROM ades_grupos WHERE id = ? AND is_active = TRUE", grupoId);
        if (grpList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        }
        Map<String, Object> grupo = grpList.get(0);

        // Fetch consolidated grades
        String sql = "SELECT p.nombre || ' ' || p.apellido_paterno AS alumno, " +
                "e.numero_control, m.nombre_materia, " +
                "COALESCE(c.calificacion_final, 0) as calificacion, " +
                "COALESCE(c.asistencia_porcentaje, 0) as asistencia " +
                "FROM ades_inscripciones i " +
                "JOIN ades_estudiantes e ON e.id = i.estudiante_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_calificaciones c ON c.inscripcion_id = i.id " +
                "LEFT JOIN ades_materias m ON m.id = c.materia_id " +
                "WHERE i.grupo_id = ? AND i.is_active = TRUE " +
                "ORDER BY p.apellido_paterno, p.nombre, m.nombre_materia";

        List<Map<String, Object>> registros = jdbc.queryForList(sql, grupoId);

        long totalAlumnos = registros.stream()
                .map(r -> r.get("numero_control"))
                .distinct()
                .count();

        return ResponseEntity.ok(Map.of(
                "grupo", grupo,
                "periodo", periodo,
                "registros", registros,
                "total_alumnos", totalAlumnos,
                "message", "Datos para acta SEP generados. Use /api/v1/carbone para renderizar PDF."
        ));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EV-017: Observaciones Pedagógicas
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/observaciones/{alumno_id}")
    public ResponseEntity<Map<String, Object>> guardarObservaciones(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody ObservacionPedagogicaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

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

    // ──────────────────────────────────────────────────────────────────────────
    // EV-024: NEE — Necesidades Educativas Especiales
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/nee")
    public ResponseEntity<List<Map<String, Object>>> listarNee(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "tipo_nee", required = false) String tipoNee,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT n.id, n.tipo_nee, n.descripcion, n.apoyos_requeridos, " +
                "n.fecha_deteccion, n.profesional_detecta, " +
                "p.nombre || ' ' || p.apellido_paterno as alumno, " +
                "e.numero_control " +
                "FROM ades_nee n " +
                "JOIN ades_estudiantes e ON e.id = n.alumno_id " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "LEFT JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE " +
                "LEFT JOIN ades_grupos g ON g.id = i.grupo_id " +
                "WHERE n.activa = TRUE"
        );
        List<Object> params = new ArrayList<>();

        if (tipoNee != null && !tipoNee.isBlank()) {
            sql.append(" AND n.tipo_nee = ?");
            params.add(tipoNee);
        }
        if (plantelId != null) {
            sql.append(" AND g.plantel_id = ?");
            params.add(plantelId);
        }

        sql.append(" ORDER BY p.apellido_paterno, p.nombre");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping("/nee")
    public ResponseEntity<Map<String, Object>> registrarNee(
            @RequestBody NeePayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

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
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", nee.getId().toString(), "message", "NEE registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // EV-025: Asignación Aula/Hora
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/asignacion-aula-hora")
    public ResponseEntity<Map<String, Object>> asignarAulaHora(
            @RequestBody AsignacionAulaHoraPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        LocalDate fecha = LocalDate.parse(data.getFecha());
        LocalTime hi = LocalTime.parse(data.getHoraInicio());
        LocalTime hf = LocalTime.parse(data.getHoraFin());

        // Check classroom scheduling conflicts
        List<Map<String, Object>> conflict = jdbc.queryForList(
                "SELECT id FROM ades_asignaciones_aula " +
                "WHERE aula_id = ? AND fecha = ? " +
                "AND NOT (hora_fin <= ? OR hora_inicio >= ?) " +
                "AND is_active = TRUE",
                data.getAulaId(), fecha, hi, hf);

        if (!conflict.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El aula ya está ocupada en ese horario");
        }

        AsignacionAula aa = new AsignacionAula();
        aa.setClaseId(data.getClaseId());
        aa.setAulaId(data.getAulaId());
        aa.setFecha(fecha);
        aa.setHoraInicio(hi);
        aa.setHoraFin(hf);
        aa.setObservaciones(data.getObservaciones());
        aa.setUsuarioCreacion(user.getUsername());
        aa.setUsuarioModificacion(user.getUsername());

        asignacionRepository.save(aa);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Aula asignada para la fecha y hora indicadas"));
    }

    @GetMapping("/asignacion-aula-hora")
    public ResponseEntity<List<Map<String, Object>>> consultarAsignacionesAula(
            @RequestParam(value = "aula_id", required = false) UUID aulaId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT aa.id, aa.fecha, aa.hora_inicio, aa.hora_fin, aa.observaciones, " +
                "a.nombre_aula, a.clave_aula, " +
                "cl.descripcion as clase_desc " +
                "FROM ades_asignaciones_aula aa " +
                "JOIN ades_aulas a ON a.id = aa.aula_id " +
                "LEFT JOIN ades_clases cl ON cl.id = aa.clase_id " +
                "WHERE aa.is_active = TRUE"
        );
        List<Object> params = new ArrayList<>();

        if (aulaId != null) {
            sql.append(" AND aa.aula_id = ?");
            params.add(aulaId);
        }
        if (fechaStr != null && !fechaStr.isBlank()) {
            sql.append(" AND aa.fecha = ?");
            params.add(LocalDate.parse(fechaStr));
        }

        sql.append(" ORDER BY aa.fecha, aa.hora_inicio");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }
}

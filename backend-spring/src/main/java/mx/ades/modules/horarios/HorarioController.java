package mx.ades.modules.horarios;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.horarios.application.service.HorarioApplicationService;
import mx.ades.modules.horarios.application.service.HorarioSolverService;
import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.query.HorarioQueryService;
import mx.ades.modules.horarios.solver.HorarioCorrida;
import mx.ades.modules.horarios.solver.HorarioLeccion;
import mx.ades.modules.horarios.solver.HorarioTimeslot;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;

/**
 * Adaptador REST para la gestión de horarios escolares con motor nativo y transición legacy aSc.
 * Expone endpoints bajo /api/v1/horarios para consultar horarios por grupo o profesor,
 * crear, actualizar y eliminar bloques horarios individuales, y realizar el round-trip
 * completo de export/import XML con aSc TimeTables durante la transición.
 * La exportación acota el plantel para no-admins (anti cross-plantel); la importación queda
 * deshabilitada por defecto mediante feature-flag y se eliminará tras la validación en producción.
 * Requiere JWT en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final AdesUserService userService;
    private final HorarioQueryService queryService;
    private final CrearHorarioUseCase crearHorarioUseCase;
    private final ActualizarHorarioUseCase actualizarHorarioUseCase;
    private final HorarioApplicationService horarioService;
    private final HorarioSolverService horarioSolverService;
    private final HorarioCorridaRepository horarioCorridaRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioAscService ascService;
    private final mx.ades.modules.horarios.application.export.HorarioExcelExporter excelExporter;
    @Value("${ades.horarios.asc-import.enabled:false}")
    private boolean ascImportEnabled;

    private static final long MAX_XML_BYTES = 10 * 1024 * 1024; // 10 MB

    @GetMapping("/grupo/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> porGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porGrupo(grupoId, cicloId));
    }

    @GetMapping("/profesor/{profesor_id}")
    public ResponseEntity<List<Map<String, Object>>> porProfesor(
            @PathVariable("profesor_id") UUID profesorId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.porProfesor(profesorId, cicloId));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.listar(grupoId, plantelId, cicloId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            UUID id = crearHorarioUseCase.crear(new CrearHorarioUseCase.Command(
                body.getGrupoId(), body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getCicloEscolarId(), body.getDiaSemana(),
                body.getHoraInicio(), body.getHoraFin(), body.getOrigen(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(queryService.obtener(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable UUID id,
            @RequestBody HorarioPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        try {
            actualizarHorarioUseCase.actualizar(new ActualizarHorarioUseCase.Command(
                id, body.getMateriaId(), body.getProfesorId(), body.getAulaId(),
                body.getDiaSemana(), body.getHoraInicio(), body.getHoraFin(),
                body.getOrigen(), body.getMotivoCambio(), user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok(queryService.obtener(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        horarioService.eliminar(id);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Solver Timefold — corridas de optimización
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/solver/corridas")
    public ResponseEntity<Map<String, Object>> iniciarCorridaSolver(
            @RequestBody SolverRunPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permisos insuficientes para ejecutar el solver de horarios");
        }
        UUID plantelId = resolverPlantel(body.getPlantelId(), user);
        if (plantelId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");
        }
        if (body.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "ciclo_escolar_id es requerido");
        }
        if (body.getLecciones() == null || body.getLecciones().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Se requiere al menos una lección");
        }

        List<HorarioTimeslot> timeslots = null;
        if (body.getTimeslots() != null) {
            timeslots = body.getTimeslots().stream().map(this::toTimeslot).toList();
        }

        List<HorarioLeccion> lecciones = body.getLecciones().stream()
                .map(payload -> toLeccion(payload, body.getCicloEscolarId()))
                .toList();

        UUID corridaId = horarioSolverService.iniciarCorrida(
                plantelId,
                body.getCicloEscolarId(),
                user.getUsername(),
                timeslots,
                lecciones);

        HorarioCorrida corrida = horarioCorridaRepository.findById(corridaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar la corrida recién creada"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapCorrida(corrida));
    }

    @PostMapping("/solver/verificar")
    public ResponseEntity<Map<String, Object>> verificarHorario(
            @RequestBody SolverRunPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID plantelId = resolverPlantel(body.getPlantelId(), user);
        if (plantelId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");
        }
        if (body.getCicloEscolarId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "ciclo_escolar_id es requerido");
        }

        List<HorarioTimeslot> timeslots = null;
        if (body.getTimeslots() != null) {
            timeslots = body.getTimeslots().stream().map(this::toTimeslot).toList();
        }
        List<HorarioLeccion> lecciones = body.getLecciones() != null ? 
            body.getLecciones().stream().map(payload -> toLeccion(payload, body.getCicloEscolarId())).toList() : List.of();

        Map<String, Object> analysis = horarioSolverService.verificarHorario(
                plantelId,
                body.getCicloEscolarId(),
                timeslots,
                lecciones);

        return ResponseEntity.ok(analysis);
    }

    public ResponseEntity<List<Map<String, Object>>> listarCorridasSolver(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantelId = resolverPlantel(plantelId, user);
        if (effectivePlantelId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");
        }
        List<HorarioCorrida> corridas = cicloId == null
                ? horarioCorridaRepository.findTop20ByPlantelIdOrderByFechaCreacionDesc(effectivePlantelId)
                : horarioCorridaRepository.findTop20ByPlantelIdAndCicloEscolarIdOrderByFechaCreacionDesc(effectivePlantelId, cicloId);
        return ResponseEntity.ok(corridas.stream().map(this::mapCorrida).toList());
    }

    @GetMapping("/solver/corridas/{id}")
    public ResponseEntity<Map<String, Object>> obtenerCorridaSolver(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        HorarioCorrida corrida = horarioCorridaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corrida no encontrada"));
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && !user.getPlantelId().equals(corrida.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar corridas de otro plantel");
        }
        Map<String, Object> response = new LinkedHashMap<>(mapCorrida(corrida));
        response.put("horarios", queryService.porCorrida(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/solver/corridas/{id}/lock")
    public ResponseEntity<Map<String, Object>> fijarCorridaSolver(
            @PathVariable UUID id,
            @RequestBody LockPayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        HorarioCorrida corrida = horarioCorridaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corrida no encontrada"));
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && !user.getPlantelId().equals(corrida.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar corridas de otro plantel");
        }
        horarioSolverService.fijarHorariosDeCorrida(id, body.getHorarioIds());
        HorarioCorrida corridaActualizada = horarioCorridaRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar la corrida actualizada"));
        Map<String, Object> response = new LinkedHashMap<>(mapCorrida(corridaActualizada));
        response.put("horarios", queryService.porCorrida(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/solver/corridas/{id}/regenerar")
    public ResponseEntity<Map<String, Object>> regenerarCorridaSolver(
            @PathVariable UUID id,
            @RequestBody RegeneratePayload body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        HorarioCorrida corrida = horarioCorridaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corrida no encontrada"));
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null
                && !user.getPlantelId().equals(corrida.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes regenerar corridas de otro plantel");
        }
        UUID nuevaCorridaId = horarioSolverService.regenerarDesdeCorrida(id, body.getHorarioIds(), user.getUsername());
        HorarioCorrida nuevaCorrida = horarioCorridaRepository.findById(nuevaCorridaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar la corrida regenerada"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapCorrida(nuevaCorrida));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // aSc TimeTables — transición deprecated
    // ──────────────────────────────────────────────────────────────────────────

    @Deprecated
    @GetMapping("/exportar-asc/{ciclo_id}")
    public ResponseEntity<byte[]> exportarAsc(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // No-admins quedan acotados a su propio plantel (evita fuga cross-plantel)
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId();
        }
        String xml = ascService.exportarXml(cicloId, plantelId);
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "horarios_asc.xml");
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @Deprecated
    @PostMapping("/importar-asc/{ciclo_id}")
    public ResponseEntity<HorarioAscService.ImportResult> importarAsc(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "reemplazar", defaultValue = "false") boolean reemplazar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (!ascImportEnabled) {
            throw new ResponseStatusException(HttpStatus.GONE, "La importación ASC está deshabilitada en este despliegue");
        }
        // Solo coordinador (nivel 3) o superior puede reconstruir horarios
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permisos insuficientes para importar horarios");
        }
        // No-admins acotados a su plantel
        if (user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            plantelId = user.getPlantelId();
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }
        if (file.getSize() > MAX_XML_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de 10 MB");
        }
        try {
            byte[] bytes = file.getBytes();
            HorarioAscService.ImportResult result =
                    ascService.importarXml(bytes, cicloId, plantelId, reemplazar, user.getUsername());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al importar: " + e.getMessage());
        }
    }

    @Data
    public static class HorarioPayload {
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID aulaId;
        private UUID cicloEscolarId;
        private Integer diaSemana;
        private String horaInicio;
        private String horaFin;
        private String origen;
        private String motivoCambio;
    }

    @Data
    public static class SolverRunPayload {
        private UUID plantelId;
        private UUID cicloEscolarId;
        private List<SolverTimeslotPayload> timeslots;
        private List<SolverLessonPayload> lecciones;
    }

    @Data
    public static class SolverTimeslotPayload {
        private Integer diaSemana;
        private String horaInicio;
        private String horaFin;
        private String turno;
    }

    @Data
    public static class SolverLessonPayload {
        private UUID id;
        private UUID grupoId;
        private UUID materiaId;
        private UUID profesorId;
        private UUID aulaId;
        private UUID cicloEscolarId;
        private Boolean fijado;
        private SolverTimeslotPayload timeslot;
    }

    @Data
    public static class LockPayload {
        private List<UUID> horarioIds;
    }

    @Data
    public static class RegeneratePayload {
        private List<UUID> horarioIds;
    }

    private UUID resolverPlantel(UUID requestedPlantelId, AdesUser user) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            return user.getPlantelId();
        }
        return requestedPlantelId;
    }

    private HorarioTimeslot toTimeslot(SolverTimeslotPayload payload) {
        if (payload == null || payload.getDiaSemana() == null || payload.getHoraInicio() == null || payload.getHoraFin() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cada timeslot requiere dia_semana, hora_inicio y hora_fin");
        }
        if (payload.getDiaSemana() < 1 || payload.getDiaSemana() > 5) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "dia_semana debe estar entre 1 y 5");
        }
        return new HorarioTimeslot(
                payload.getDiaSemana(),
                LocalTime.parse(payload.getHoraInicio()),
                LocalTime.parse(payload.getHoraFin()),
                payload.getTurno());
    }

    private HorarioLeccion toLeccion(SolverLessonPayload payload, UUID cicloEscolarId) {
        if (payload == null || payload.getGrupoId() == null || payload.getMateriaId() == null || payload.getProfesorId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cada lección requiere grupo_id, materia_id y profesor_id");
        }
        HorarioLeccion leccion = new HorarioLeccion();
        leccion.setId(payload.getId() != null ? payload.getId() : UUID.randomUUID());
        leccion.setGrupoId(payload.getGrupoId());
        leccion.setMateriaId(payload.getMateriaId());
        leccion.setProfesorId(payload.getProfesorId());
        leccion.setAulaId(payload.getAulaId());
        leccion.setCicloEscolarId(payload.getCicloEscolarId() != null ? payload.getCicloEscolarId() : cicloEscolarId);
        leccion.setFijado(Boolean.TRUE.equals(payload.getFijado()));
        if (payload.getTimeslot() != null) {
            leccion.setTimeslot(toTimeslot(payload.getTimeslot()));
        } else if (Boolean.TRUE.equals(payload.getFijado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Las lecciones fijadas requieren un timeslot asignado");
        }
        return leccion;
    }


    @GetMapping("/corridas/{id}/excel")
    public ResponseEntity<byte[]> descargarExcelCorrida(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        var corrida = horarioCorridaRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corrida no encontrada."));
            
        java.util.List<java.util.Map<String, Object>> horarios = queryService.porCorrida(id);
        if (horarios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La corrida no tiene horarios generados.");
        }
        
        byte[] excelBytes = excelExporter.generarExcel(horarios, corrida.getId().toString());
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Horario_Corrida_" + id.toString().substring(0, 8) + ".xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    private Map<String, Object> mapCorrida(HorarioCorrida corrida) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", corrida.getId());
        response.put("plantel_id", corrida.getPlantelId());
        response.put("ciclo_escolar_id", corrida.getCicloEscolarId());
        response.put("estado", corrida.getEstado());
        response.put("score_text", corrida.getScoreText());
        response.put("score_analysis_json", corrida.getScoreAnalysisJson());
        response.put("tiempo_solving_ms", corrida.getTiempoSolvingMs());
        response.put("version", corrida.getVersion());
        response.put("generado_por", corrida.getGeneradoPor());
        long count = horarioRepository.countByCorridaId(corrida.getId());
        response.put("horarios_generados", count);
        response.put("resultado_excel_url", count > 0 ? "/api/v1/horarios/corridas/" + corrida.getId() + "/excel" : null);
        response.put("fecha_creacion", corrida.getFechaCreacion());
        response.put("fecha_modificacion", corrida.getFechaModificacion());
        return response;
    }
}

package mx.ades.modules.calificaciones;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.admin.ConfigQueryService;
import mx.ades.modules.calificaciones.domain.port.in.CalcularCalificacionPeriodoUseCase;
import mx.ades.modules.calificaciones.domain.port.in.GuardarCalificacionManualUseCase;
import mx.ades.modules.calificaciones.domain.port.in.ObtenerBoletaUseCase;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalcularCalificacionDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.CalificacionResponseDto;
import mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto.GuardarCalificacionManualDto;
import mx.ades.modules.calificaciones.query.CalificacionesQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/calificaciones")
@RequiredArgsConstructor
public class CalificacionesController {

    private final CalcularCalificacionPeriodoUseCase calcularCalificacionPeriodo;
    private final GuardarCalificacionManualUseCase   guardarCalificacionManual;
    private final ObtenerBoletaUseCase               obtenerBoleta;
    private final CalificacionesQueryService         queryService;
    private final ConfigQueryService                 configQueryService;
    private final JdbcTemplate                       jdbc;
    private final AdesUserService                    userService;

    private final ObjectMapper om = new ObjectMapper();

    private static final Set<String> NIVELES_LOGRO_VALIDOS = Set.of("A", "B", "C", "D");

    @PostMapping("/calcular")
    public ResponseEntity<Void> calcular(@RequestBody CalcularCalificacionDto req) {
        calcularCalificacionPeriodo.ejecutar(req.estudianteId(), req.inscripcionId(), req.materiaId(), req.periodoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public ResponseEntity<CalificacionResponseDto> guardarManual(
            @RequestBody GuardarCalificacionManualDto req) {
        return ResponseEntity.ok(
                CalificacionResponseDto.from(guardarCalificacionManual.ejecutar(req.toDomain())));
    }

    @GetMapping("/boleta/{estudianteId}")
    public ResponseEntity<List<CalificacionResponseDto>> boleta(
            @PathVariable("estudianteId") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {

        // Scoping: Validar que el usuario tiene acceso a ver calificaciones de este estudiante
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() < 3 && !user.getEstudianteId().equals(estudianteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes permiso para ver calificaciones de otro estudiante");
        }

        // Pasar usuarioId para scoping de caché (BOLA prevention)
        return ResponseEntity.ok(
                obtenerBoleta.ejecutar(estudianteId, user.getId()).stream()
                        .map(CalificacionResponseDto::from)
                        .toList());
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId) {
        return ResponseEntity.ok(queryService.periodos(cicloId));
    }

    /**
     * GET /api/v1/calificaciones/grupo/{grupoId}/libreta?materia_id=...
     * Devuelve la libreta del grupo: lista de alumnos con sus calificaciones por período.
     */
    @GetMapping("/grupo/{grupoId}/libreta")
    public ResponseEntity<Map<String, Object>> libreta(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(name = "materia_id") UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        // ALUMNO (5) y PADRE_FAMILIA (6) no pueden ver la libreta completa del grupo
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Sin permisos para acceder a la libreta del grupo");
        }
        // Para no-admin: verificar que el grupo pertenece al plantel del usuario
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            boolean perteneceAPlantel = !jdbc.queryForList(
                "SELECT id FROM ades_grupos WHERE id = ?::uuid AND plantel_id = ?::uuid",
                grupoId, user.getPlantelId()).isEmpty();
            if (!perteneceAPlantel) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sin acceso al grupo solicitado");
            }
        }

        // 1. Períodos activos del ciclo del grupo
        List<Map<String, Object>> periodos = jdbc.queryForList(
            "SELECT pe.id, pe.nombre_periodo, pe.numero_periodo " +
            "FROM ades_periodos_evaluacion pe " +
            "JOIN ades_grupos g ON g.ciclo_escolar_id = pe.ciclo_escolar_id " +
            "WHERE g.id = ? AND pe.is_active = true " +
            "ORDER BY pe.numero_periodo ASC",
            grupoId);

        // 2. Alumnos inscritos en el grupo
        List<Map<String, Object>> alumnos = jdbc.queryForList(
            "SELECT e.id AS estudiante_id, " +
            "  p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo " +
            "FROM ades_inscripciones i " +
            "JOIN ades_estudiantes e ON e.id = i.estudiante_id " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "WHERE i.grupo_id = ? AND i.is_active = true " +
            "ORDER BY p.apellido_paterno, p.nombre",
            grupoId);

        // 3. Calificaciones registradas (incluyendo nivel_logro para evaluación cualitativa)
        List<Map<String, Object>> cals = jdbc.queryForList(
            "SELECT estudiante_id, periodo_evaluacion_id, calificacion_final, nivel_logro " +
            "FROM ades_calificaciones_periodo " +
            "WHERE grupo_id = ? AND materia_id = ? AND is_active = true",
            grupoId, materiaId);

        // Construir mapa: estudianteId → (periodoId → {calificacion, nivel_logro})
        Map<String, Map<String, Object>> calMap = new HashMap<>();
        Map<String, Map<String, Object>> logroMap = new HashMap<>();
        for (Map<String, Object> cal : cals) {
            String eId = cal.get("estudiante_id").toString();
            String pId = cal.get("periodo_evaluacion_id").toString();
            calMap.computeIfAbsent(eId, k -> new HashMap<>()).put(pId, cal.get("calificacion_final"));
            if (cal.get("nivel_logro") != null) {
                logroMap.computeIfAbsent(eId, k -> new HashMap<>()).put(pId, cal.get("nivel_logro"));
            }
        }

        // Mapa periodoId → nombre
        Map<String, String> periodoNombres = periodos.stream()
            .collect(Collectors.toMap(
                p -> p.get("id").toString(),
                p -> p.get("nombre_periodo").toString()));

        // 4. Construir registros
        List<Map<String, Object>> registros = alumnos.stream().map(a -> {
            String eId = a.get("estudiante_id").toString();
            Map<String, Object> calsPorPeriodo = new LinkedHashMap<>();
            Map<String, Object> logrosPorPeriodo = new LinkedHashMap<>();
            Double sum = 0.0; int count = 0;
            for (Map<String, Object> p : periodos) {
                String pId = p.get("id").toString();
                String pNombre = p.get("nombre_periodo").toString();
                Object val = calMap.getOrDefault(eId, Map.of()).get(pId);
                Object logro = logroMap.getOrDefault(eId, Map.of()).get(pId);
                calsPorPeriodo.put(pNombre, val);
                logrosPorPeriodo.put(pNombre, logro);
                if (val != null) { sum += ((Number) val).doubleValue(); count++; }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("estudiante_id", eId);
            row.put("nombre_completo", a.get("nombre_completo"));
            row.put("calificaciones", calsPorPeriodo);
            row.put("niveles_logro", logrosPorPeriodo);
            row.put("promedio", count > 0 ? Math.round(sum / count * 10.0) / 10.0 : null);
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grupo_id", grupoId);
        result.put("materia_id", materiaId);
        result.put("periodos_detalle", periodos);
        result.put("registros", registros);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/calificaciones/config-cualitativa?nivel=PRIMARIA
     * Devuelve la config de evaluación cualitativa + escala activa para el nivel dado.
     * Público para docentes y admins.
     */
    @GetMapping("/config-cualitativa")
    public ResponseEntity<Map<String, Object>> configCualitativa(
            @RequestParam(value = "nivel", defaultValue = "PRIMARIA") String nivel,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(configQueryService.configCualitativa(nivel));
    }

    /**
     * POST /api/v1/calificaciones/cualitativa
     * Guarda evaluación cualitativa NEM (nivel_logro A/B/C/D + calificacion_final derivada).
     * Calcula calificacion_final desde el equiv_num de la escala activa.
     */
    @Data
    public static class CualitativaRequest {
        private UUID estudianteId;
        private UUID grupoId;
        private UUID materiaId;
        private UUID periodoEvaluacionId;
        private String nivelLogro;
    }

    @PostMapping("/cualitativa")
    public ResponseEntity<Map<String, Object>> guardarCualitativa(
            @RequestBody CualitativaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 4)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para registrar calificaciones");

        if (body.getNivelLogro() == null || !NIVELES_LOGRO_VALIDOS.contains(body.getNivelLogro()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "nivelLogro debe ser A, B, C o D");

        if (body.getEstudianteId() == null || body.getGrupoId() == null
                || body.getMateriaId() == null || body.getPeriodoEvaluacionId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan campos requeridos");

        // Obtener equiv_num de la escala activa PRIMARIA
        BigDecimal equivNum = resolverEquivNum(body.getNivelLogro());

        // UPSERT: actualizar si existe, insertar si no
        int updated = jdbc.update(
            "UPDATE ades_calificaciones_periodo " +
            "SET calificacion_final = ?, nivel_logro = ?, " +
            "    fecha_modificacion = now(), row_version = row_version + 1 " +
            "WHERE estudiante_id = ? AND grupo_id = ? AND materia_id = ? " +
            "  AND periodo_evaluacion_id = ? AND is_active = true",
            equivNum, body.getNivelLogro(),
            body.getEstudianteId(), body.getGrupoId(),
            body.getMateriaId(), body.getPeriodoEvaluacionId());

        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ades_calificaciones_periodo " +
                "(id, estudiante_id, grupo_id, materia_id, periodo_evaluacion_id, " +
                " calificacion_final, nivel_logro, is_active) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, true)",
                body.getEstudianteId(), body.getGrupoId(),
                body.getMateriaId(), body.getPeriodoEvaluacionId(),
                equivNum, body.getNivelLogro());
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "nivel_logro", body.getNivelLogro(),
            "calificacion_final", equivNum));
    }

    private BigDecimal resolverEquivNum(String nivel) {
        List<Map<String, Object>> escalas = jdbc.queryForList(
            "SELECT valores_json::text AS v FROM ades_escalas_evaluacion " +
            "WHERE nivel_educativo = 'PRIMARIA' AND is_active = true ORDER BY fecha_creacion DESC LIMIT 1");
        if (!escalas.isEmpty()) {
            try {
                List<Map<String, Object>> descriptores = om.readValue(
                    escalas.get(0).get("v").toString(), new TypeReference<>() {});
                for (Map<String, Object> d : descriptores) {
                    if (nivel.equals(d.get("nivel"))) {
                        Object equiv = d.get("equiv_num");
                        if (equiv instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                    }
                }
            } catch (Exception ignored) {}
        }
        // Fallback hardcoded si no hay escala en BD
        return switch (nivel) {
            case "A" -> new BigDecimal("10.0");
            case "B" -> new BigDecimal("8.0");
            case "C" -> new BigDecimal("6.5");
            default  -> new BigDecimal("5.0");
        };
    }
}

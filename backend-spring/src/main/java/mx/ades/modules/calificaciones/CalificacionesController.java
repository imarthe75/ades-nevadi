package mx.ades.modules.calificaciones;

import lombok.RequiredArgsConstructor;
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
    private final JdbcTemplate                       jdbc;
    private final AdesUserService                    userService;

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
            @PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(
                obtenerBoleta.ejecutar(estudianteId).stream()
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

        // 3. Calificaciones registradas
        List<Map<String, Object>> cals = jdbc.queryForList(
            "SELECT estudiante_id, periodo_evaluacion_id, calificacion_final " +
            "FROM ades_calificaciones_periodo " +
            "WHERE grupo_id = ? AND materia_id = ? AND is_active = true",
            grupoId, materiaId);

        // Construir mapa: estudianteId → (periodoId → calificacion)
        Map<String, Map<String, Object>> calMap = new HashMap<>();
        for (Map<String, Object> cal : cals) {
            String eId = cal.get("estudiante_id").toString();
            String pId = cal.get("periodo_evaluacion_id").toString();
            calMap.computeIfAbsent(eId, k -> new HashMap<>()).put(pId, cal.get("calificacion_final"));
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
            Double sum = 0.0; int count = 0;
            for (Map<String, Object> p : periodos) {
                String pId = p.get("id").toString();
                String pNombre = p.get("nombre_periodo").toString();
                Object val = calMap.getOrDefault(eId, Map.of()).get(pId);
                calsPorPeriodo.put(pNombre, val);
                if (val != null) { sum += ((Number) val).doubleValue(); count++; }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("estudiante_id", eId);
            row.put("nombre_completo", a.get("nombre_completo"));
            row.put("calificaciones", calsPorPeriodo);
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
}

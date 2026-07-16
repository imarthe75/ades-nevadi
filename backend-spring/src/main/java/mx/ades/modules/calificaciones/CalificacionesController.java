package mx.ades.modules.calificaciones;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * BOLA fix: antes de este chequeo, un DOCENTE (nivelAcceso 4) podía calcular/guardar
     * calificaciones de CUALQUIER grupo/materia del sistema, incluso de otro plantel o de
     * una materia que no imparte — el chequeo previo solo verificaba "nivelAcceso &le;4"
     * sin comprobar asignación real. Mismo criterio que
     * {@code GradebookController#requireAccesoGrupo}: admin/director/coordinador
     * (nivelAcceso &le;3) conservan alcance institucional; un DOCENTE (4) solo puede
     * operar sobre grupos donde esté realmente asignado (ades_asignaciones_docentes).
     */
    private void requireAccesoGrupo(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) {
            // BOLA fix (2026-07-16): "alcance institucional" nunca verificaba el plantel
            // del grupo — mismo hallazgo replicado en ActividadesController/
            // EntregasController/EvaluacionController/GradebookController/TareaController.
            // Solo nivelAcceso 0 mantiene alcance libre.
            List<UUID> plantelRows = jdbc.queryForList(
                    "SELECT gr.plantel_id FROM ades_grupos g " +
                    "JOIN ades_grados gr ON gr.id = g.grado_id " +
                    "WHERE g.id = ?", UUID.class, grupoId);
            if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
            userService.verificarPlantel(user, plantelRows.get(0), "El grupo no pertenece a su plantel");
            return;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_asignaciones_docentes ad " +
                "JOIN ades_profesores p ON p.id = ad.profesor_id " +
                "WHERE ad.grupo_id = ? AND p.persona_id = ? AND ad.is_active = TRUE",
                Long.class, grupoId, user.getPersonaId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No está asignado a este grupo");
        }
    }

    @PostMapping("/calcular")
    public ResponseEntity<Void> calcular(
            @RequestBody CalcularCalificacionDto req,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        List<UUID> grupoRows = jdbc.queryForList(
                "SELECT grupo_id FROM ades_inscripciones WHERE id = ?", UUID.class, req.inscripcionId());
        if (grupoRows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada");
        requireAccesoGrupo(user, grupoRows.get(0));
        calcularCalificacionPeriodo.ejecutar(req.estudianteId(), req.inscripcionId(), req.materiaId(), req.periodoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/manual")
    public ResponseEntity<CalificacionResponseDto> guardarManual(
            @RequestBody @Valid GuardarCalificacionManualDto req,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAccesoGrupo(user, req.grupoId());
        return ResponseEntity.ok(
                CalificacionResponseDto.from(guardarCalificacionManual.ejecutar(req.toDomain())));
    }

    @GetMapping("/boleta/{estudianteId}")
    public ResponseEntity<List<CalificacionResponseDto>> boleta(
            @PathVariable("estudianteId") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {

        // Scoping: Validar que el usuario tiene acceso a ver calificaciones de este estudiante.
        // AdesUser no trae un estudianteId propio (solo personaId) — un alumno/padre autenticado
        // solo puede ver la boleta si su persona está ligada a ese estudiante (previene BOLA).
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 4) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes WHERE id = ? AND persona_id = ?",
                Integer.class, estudianteId, user.getPersonaId());
            if (count == null || count == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para ver calificaciones de otro estudiante");
            }
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
        List<UUID> grupoPlantelRows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_grupos WHERE id = ?::uuid", UUID.class, grupoId);
        UUID grupoPlantelId = grupoPlantelRows.isEmpty() ? null : grupoPlantelRows.get(0);
        userService.verificarPlantel(user, grupoPlantelId, "Sin acceso al grupo solicitado");

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
    @Transactional
    public ResponseEntity<Map<String, Object>> guardarCualitativa(
            @RequestBody CualitativaRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (body.getGrupoId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grupoId es obligatorio");
        requireAccesoGrupo(user, body.getGrupoId());

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

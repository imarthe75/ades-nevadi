package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import mx.ades.modules.evaluaciones.query.EvaluacionQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * Adaptador REST para la gestión de evaluaciones (exámenes, tareas, proyectos).
 * Expone endpoints bajo /api/v1/evaluaciones para listar, crear, actualizar y
 * consultar evaluaciones por grupo. El endpoint /{id}/calificaciones/bulk permite
 * registrar calificaciones masivas de alumnos en una sola operación via
 * {@code CalificarEvaluacionMasivoUseCase}. Las evaluaciones tienen tipo, puntaje
 * máximo, periodo y fecha. El endpoint GET /{id}/calificaciones retorna las
 * calificaciones individuales con scoping por grupo. Toda operación requiere JWT válido.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/evaluaciones")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionRepository repository;
    private final AdesUserService userService;
    private final CalificarEvaluacionMasivoUseCase calificarEvaluacionMasivo;
    private final EvaluacionQueryService queryService;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA (mismo criterio que TareaController#listar): para no-admins, grupo_id es
        // obligatorio — de lo contrario se listarían evaluaciones de todos los grupos
        // del sistema (con promedios/calificaciones agregadas) sin ningún scoping.
        if (grupoId == null && (user.getNivelAcceso() == null || user.getNivelAcceso() > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'grupo_id' es requerido");
        }
        return ResponseEntity.ok(queryService.listar(grupoId, cicloId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Evaluacion> get(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        // Hallazgo de auditoría BOLA (Fase 5): este endpoint no llamaba resolveUser —
        // era alcanzable sin ninguna verificación de autenticación local.
        AdesUser user = userService.resolveUser(jwt);
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));
        // BOLA fix (asimetría): el fix anterior solo cubrió la falta de resolveUser, pero
        // seguía sin scoping por grupo — a diferencia de su hermano calificaciones(), que sí
        // exige requireAccesoGrupoEvaluacion. Cualquier usuario autenticado podía leer el
        // detalle (grupo, materia, fecha, puntaje) de cualquier evaluación del sistema.
        requireAccesoGrupoEvaluacion(user, eval.getGrupoId());
        return ResponseEntity.ok(eval);
    }

    @GetMapping("/{id}/calificaciones")
    public ResponseEntity<List<Map<String, Object>>> calificaciones(
            @PathVariable("id") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID grupoId = queryService.grupoIdDeEvaluacion(evalId);
        // Hallazgo de auditoría BOLA (Fase 5): lectura de calificaciones (dato sensible)
        // sin scoping — un docente podía consultar las notas de un grupo ajeno.
        requireAccesoGrupoEvaluacion(user, grupoId);
        return ResponseEntity.ok(queryService.calificacionesPorEvaluacion(evalId, grupoId));
    }

    @PostMapping("/{id}/calificaciones/bulk")
    public ResponseEntity<Map<String, Object>> bulkCalificaciones(
            @PathVariable("id") UUID evalId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);
        // Hallazgo de auditoría BOLA (Fase 5): calificación masiva sin verificar nivelAcceso
        // ni que el docente autenticado esté realmente asignado al grupo de la evaluación —
        // cualquier usuario autenticado (incluido alumno/padre) podía calificar cualquier
        // grupo del sistema. Ver requireAccesoGrupoEvaluacion().
        requireAccesoGrupoEvaluacion(user, queryService.grupoIdDeEvaluacion(evalId));

        Object cicloIdObj = body.get("ciclo_id");
        if (cicloIdObj != null) {
            UUID cicloId = UUID.fromString(cicloIdObj.toString());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawCalifs = (List<Map<String, Object>>) body.get("calificaciones");
        if (rawCalifs == null || rawCalifs.isEmpty()) {
            return ResponseEntity.ok(Map.of("updated", 0));
        }

        List<CalificarEvaluacionMasivoUseCase.EntradaCalificacion> entradas = new ArrayList<>();
        for (Map<String, Object> c : rawCalifs) {
            Object calVal = c.get("calificacion");
            if (calVal == null) continue;
            entradas.add(new CalificarEvaluacionMasivoUseCase.EntradaCalificacion(
                    UUID.fromString(c.get("estudiante_id").toString()),
                    Double.parseDouble(calVal.toString()),
                    (String) c.get("comentarios")));
        }

        int updated = calificarEvaluacionMasivo.ejecutar(
                new CalificarEvaluacionMasivoUseCase.Command(evalId, entradas, user.getUsername()));

        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping
    public ResponseEntity<Evaluacion> create(
            @RequestBody Evaluacion evaluacion,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (evaluacion.getNombreEvaluacion() == null || evaluacion.getNombreEvaluacion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nombre_evaluacion es obligatorio");
        }
        if (evaluacion.getGrupoId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "grupo_id es obligatorio");
        }
        // Hallazgo de auditoría BOLA/BFLA (Fase 5): no había verificación de nivelAcceso
        // ni de asignación docente↔grupo — cualquier usuario autenticado podía crear
        // evaluaciones para cualquier grupo del sistema.
        requireAccesoGrupoEvaluacion(user, evaluacion.getGrupoId());
        if (evaluacion.getMateriaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "materia_id es obligatorio");
        }
        if (evaluacion.getPeriodoEvaluacionId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "periodo_evaluacion_id es obligatorio");
        }
        if (evaluacion.getFechaEvaluacion() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fecha_evaluacion es obligatoria");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(evaluacion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Evaluacion> update(
            @PathVariable("id") UUID id,
            @RequestBody Evaluacion update,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));

        if (update.getNombreEvaluacion() == null || update.getNombreEvaluacion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "nombre_evaluacion es obligatorio");
        }
        if (update.getGrupoId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "grupo_id es obligatorio");
        }
        // Hallazgo de auditoría BOLA/BFLA (Fase 5): verifica tanto el grupo actual de la
        // evaluación como el grupo destino (por si el body intenta reasignarla a otro
        // grupo) — un docente no puede editar ni "mover" evaluaciones fuera de su alcance.
        requireAccesoGrupoEvaluacion(user, eval.getGrupoId());
        requireAccesoGrupoEvaluacion(user, update.getGrupoId());
        if (update.getMateriaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "materia_id es obligatorio");
        }
        if (update.getPeriodoEvaluacionId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "periodo_evaluacion_id es obligatorio");
        }
        if (update.getFechaEvaluacion() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fecha_evaluacion es obligatoria");
        }

        eval.setNombreEvaluacion(update.getNombreEvaluacion());
        eval.setDescripcion(update.getDescripcion());
        eval.setGrupoId(update.getGrupoId());
        eval.setMateriaId(update.getMateriaId());
        eval.setPeriodoEvaluacionId(update.getPeriodoEvaluacionId());
        eval.setFechaEvaluacion(update.getFechaEvaluacion());
        eval.setTipoEvaluacion(update.getTipoEvaluacion());
        eval.setPuntajeMaximo(update.getPuntajeMaximo());
        eval.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(eval));
    }

    /**
     * GET /evaluaciones/periodos/{periodo_id}/validar-cierre?grupo_id=...
     * Construida 2026-07-20 — antes no existía ningún endpoint en ningún backend
     * (Spring ni FastAPI), dejando el wizard "Cierre Formal de Período" del gradebook
     * (cierre-periodo.component.ts) roto en el 100% de los intentos (404 en el paso 1,
     * "Validar calificaciones"). Contrato exacto tomado del frontend ya existente (los
     * 4 pasos del wizard ya definían la forma esperada de la respuesta).
     * "Esperado" = alumnos con inscripción activa en el grupo × materias vigentes del
     * plan de estudio del grado+ciclo del grupo (ades_materias_plan). Un par
     * alumno×materia sin fila en ades_calificaciones_periodo para este periodo cuenta
     * como faltante — la columna calificacion_final es NOT NULL, así que la ausencia
     * de la fila es la única señal real de "sin calificar" (no hay estado intermedio).
     */
    @GetMapping("/periodos/{periodo_id}/validar-cierre")
    public ResponseEntity<Map<String, Object>> validarCierre(
            @PathVariable("periodo_id") UUID periodoId,
            @RequestParam("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivelCierre(user);
        requireAccesoGrupoEvaluacion(user, grupoId);

        List<Map<String, Object>> faltantes = jdbc.queryForList("""
            SELECT (p.nombre || ' ' || p.apellido_paterno) AS alumno, m.nombre_materia AS materia
            FROM ades_inscripciones i
            JOIN ades_grupos g ON g.id = i.grupo_id
            JOIN ades_materias_plan mp ON mp.grado_id = g.grado_id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_materias m ON m.id = mp.materia_id
            JOIN ades_estudiantes e ON e.id = i.estudiante_id
            JOIN ades_personas p ON p.id = e.persona_id
            WHERE i.grupo_id = ? AND i.is_active = TRUE
              AND NOT EXISTS (
                SELECT 1 FROM ades_calificaciones_periodo cp
                WHERE cp.grupo_id = i.grupo_id AND cp.estudiante_id = i.estudiante_id
                  AND cp.materia_id = mp.materia_id AND cp.periodo_evaluacion_id = ?
                  AND cp.is_active = TRUE
              )
            ORDER BY alumno, materia
            """, grupoId, periodoId);

        Integer totalEsperadas = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ades_inscripciones i
            JOIN ades_grupos g ON g.id = i.grupo_id
            JOIN ades_materias_plan mp ON mp.grado_id = g.grado_id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            WHERE i.grupo_id = ? AND i.is_active = TRUE
            """, Integer.class, grupoId);
        Integer conCalificacion = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ades_calificaciones_periodo
            WHERE grupo_id = ? AND periodo_evaluacion_id = ? AND is_active = TRUE
            """, Integer.class, grupoId, periodoId);
        Integer yaCerradas = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ades_calificaciones_periodo
            WHERE grupo_id = ? AND periodo_evaluacion_id = ? AND is_active = TRUE AND cerrada = TRUE
            """, Integer.class, grupoId, periodoId);

        long alumnosFaltantes = faltantes.stream().map(f -> f.get("alumno")).distinct().count();
        long materiasIncompletas = faltantes.stream().map(f -> f.get("materia")).distinct().count();

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("total_esperadas", totalEsperadas);
        detalles.put("con_calificacion", conCalificacion);
        detalles.put("ya_cerradas", yaCerradas);
        detalles.put("faltantes", faltantes.size());
        detalles.put("alumnos_sin_cal", faltantes.isEmpty() ? null : faltantes);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("puede_cerrar", faltantes.isEmpty());
        resp.put("alumnos_faltantes", alumnosFaltantes);
        resp.put("materias_incompletas", materiasIncompletas);
        resp.put("detalles", detalles);
        return ResponseEntity.ok(resp);
    }

    /**
     * POST /evaluaciones/periodos/{periodo_id}/cerrar — body {grupo_id, notas?}.
     * Bloquea permanentemente (cerrada=TRUE) todas las filas de
     * ades_calificaciones_periodo del grupo+periodo — mismo campo/semántica que
     * {@code CerrarCalificacionUseCase} (cierre individual ya existente en
     * GradebookController), aplicado en bloque a todo el grupo. Vuelve a validar en el
     * servidor antes de cerrar (nunca confiar en que el frontend ya validó en el paso
     * 1 del wizard — el estado pudo cambiar entre la validación y la confirmación).
     * "notas" del wizard se acepta pero no se persiste en una columna dedicada (no
     * existe una columna de "notas de cierre" a nivel grupo+periodo en el esquema
     * actual, y no se improvisa una migración nueva para esto) — queda igualmente
     * capturado en el log de auditoría vía AuditHttpFilter, que registra el body
     * completo de toda mutación.
     */
    @PostMapping("/periodos/{periodo_id}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarPeriodo(
            @PathVariable("periodo_id") UUID periodoId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireNivelCierre(user);
        Object grupoIdObj = body.get("grupo_id");
        if (grupoIdObj == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "grupo_id es obligatorio");
        }
        UUID grupoId = UUID.fromString(grupoIdObj.toString());
        requireAccesoGrupoEvaluacion(user, grupoId);

        Integer pendientes = jdbc.queryForObject("""
            SELECT COUNT(*) FROM ades_inscripciones i
            JOIN ades_grupos g ON g.id = i.grupo_id
            JOIN ades_materias_plan mp ON mp.grado_id = g.grado_id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            WHERE i.grupo_id = ? AND i.is_active = TRUE
              AND NOT EXISTS (
                SELECT 1 FROM ades_calificaciones_periodo cp
                WHERE cp.grupo_id = i.grupo_id AND cp.estudiante_id = i.estudiante_id
                  AND cp.materia_id = mp.materia_id AND cp.periodo_evaluacion_id = ?
                  AND cp.is_active = TRUE
              )
            """, Integer.class, grupoId, periodoId);
        if (pendientes != null && pendientes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cerrar: hay " + pendientes + " calificaciones pendientes");
        }

        int cerradas = jdbc.update("""
            UPDATE ades_calificaciones_periodo
            SET cerrada = TRUE, fecha_cierre = CURRENT_TIMESTAMP, cerrado_por = ?,
                fecha_modificacion = CURRENT_TIMESTAMP, usuario_modificacion = ?,
                row_version = row_version + 1
            WHERE grupo_id = ? AND periodo_evaluacion_id = ? AND cerrada = FALSE AND is_active = TRUE
            """, user.getId(), user.getUsername(), grupoId, periodoId);

        return ResponseEntity.ok(Map.of("calificaciones_cerradas", cerradas));
    }

    /**
     * Cierre formal de período: solo nivelAcceso &le;3 (ADMIN_GLOBAL/ADMIN_PLANTEL/
     * DIRECTOR/COORDINADOR_ACADEMICO) — mismo umbral que
     * {@code CerrarCalificacionUseCase.ROLES_AUTORIZADOS} usado para el cierre
     * individual de una calificación; un Docente (nivel 4) puede calificar pero no
     * bloquear el período completo de un grupo.
     */
    private void requireNivelCierre(AdesUser user) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo Coordinador Académico o superior puede cerrar formalmente un período");
        }
    }

    /**
     * Crear/editar evaluaciones y calificar (individual o masivo) es operación de
     * personal escolar (nivelAcceso &le;4: admin/director/coordinador/docente).
     * Admin/Director/Coordinador (nivelAcceso &le;3) tienen alcance institucional;
     * un Docente (nivelAcceso 4) solo puede operar sobre grupos donde esté realmente
     * asignado (tabla {@code ades_asignaciones_docentes}) — previene BOLA (OWASP API1)
     * y BFLA (OWASP API5). Hallazgo de auditoría Fase 5: ninguno de estos controles
     * existía; cualquier usuario autenticado (incluido alumno/padre) podía crear,
     * editar o calificar masivamente evaluaciones de cualquier grupo del sistema.
     */
    private void requireAccesoGrupoEvaluacion(AdesUser user, UUID grupoId) {
        Integer nivel = user.getNivelAcceso();
        if (nivel == null || nivel > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
        if (nivel <= 3) {
            // BOLA fix (2026-07-16): "alcance institucional" nunca verificaba el plantel
            // del grupo — mismo hallazgo replicado en varios controllers de evaluaciones/
            // gradebook. Solo nivelAcceso 0 mantiene alcance libre.
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
}

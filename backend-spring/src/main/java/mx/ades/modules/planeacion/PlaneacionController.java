package mx.ades.modules.planeacion;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planeacion.command.CalificacionesDesdeplanneacionCommandService;
import mx.ades.modules.planeacion.command.PlaneacionCommandService;
import mx.ades.modules.planeacion.query.BolecaDesdeplanneacionQueryService;
import mx.ades.modules.planeacion.query.CalificacionesDesdeplanneacionQueryService;
import mx.ades.modules.planeacion.query.PlaneacionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la planeación didáctica por grupo y materia.
 * Expone endpoints bajo /api/v1/planeacion para consultar temas con estado de avance,
 * cobertura curricular por grupo, listado de clases planeadas, crear una planeación de clase,
 * marcar un tema como completado con fecha de ejecución, eliminar planeaciones,
 * obtener alertas de rezago por ciclo y el plan semanal e insights de un grupo.
 * Este controlador no exige JWT propio en todos los endpoints (delega al servicio de comandos).
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/planeacion")
@RequiredArgsConstructor
public class PlaneacionController {

    private final PlaneacionQueryService   queries;
    private final PlaneacionCommandService commands;
    private final CalificacionesDesdeplanneacionQueryService calificacionesQueries;
    private final CalificacionesDesdeplanneacionCommandService calificacionesCommands;
    private final BolecaDesdeplanneacionQueryService bolecaQueries;

    @GetMapping("/temas")
    public ResponseEntity<List<Map<String, Object>>> temasConEstado(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(queries.getTemasConEstado(grupoId, materiaId));
    }

    @GetMapping("/cobertura/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> coberturaGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getCoberturaGrupo(grupoId));
    }

    @GetMapping("/clases")
    public ResponseEntity<List<Map<String, Object>>> listarPlaneacion(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(queries.getListarPlaneacion(grupoId, materiaId));
    }

    public record PlaneacionCreateRequest(
            UUID grupo_id,
            UUID tema_id,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_planeada,
            String descripcion_actividades,
            String recursos_didacticos
    ) {}

    @PostMapping("/clases")
    public ResponseEntity<Map<String, Object>> crearPlaneacion(
            @RequestBody PlaneacionCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearPlaneacion(body.grupo_id(), body.tema_id(),
                        body.fecha_planeada(), body.descripcion_actividades(),
                        body.recursos_didacticos()));
    }

    public record CompletarAvanceRequest(
            UUID clase_id,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_ejecucion,
            String comentarios_profesor
    ) {}

    @PostMapping("/clases/{planeacion_id}/completar")
    public ResponseEntity<Map<String, Object>> completarTema(
            @PathVariable("planeacion_id") UUID planeacionId,
            @RequestBody CompletarAvanceRequest body) {
        return ResponseEntity.ok(commands.completarTema(
                planeacionId, body.clase_id(), body.fecha_ejecucion(),
                body.comentarios_profesor()));
    }

    @DeleteMapping("/clases/{planeacion_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPlaneacion(@PathVariable("planeacion_id") UUID planeacionId) {
        commands.eliminarPlaneacion(planeacionId);
    }

    @GetMapping("/alertas-rezago/{ciclo_id}")
    public ResponseEntity<Map<String, Object>> alertasRezago(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestParam(value = "umbral_pct", defaultValue = "80.0") Double umbralPct) {
        return ResponseEntity.ok(queries.getAlertasRezago(cicloId, umbralPct));
    }

    @GetMapping("/semana/{grupo_id}")
    public ResponseEntity<Map<String, Object>> planSemana(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam("fecha_inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio) {
        return ResponseEntity.ok(queries.getPlanSemana(grupoId, fechaInicio));
    }

    @GetMapping("/insights/{grupo_id}")
    public ResponseEntity<Map<String, Object>> insightsGrupo(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getInsightsGrupo(grupoId));
    }

    // ── OA-012: Ajuste dinámico ante suspensión de clase ──────────────────────

    @GetMapping("/pendientes-reprogramar/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> pendientesReprogramar(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getPendientesReprogramar(grupoId));
    }

    public record ReprogramarRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nueva_fecha) {}

    @PatchMapping("/clases/{planeacion_id}/reprogramar")
    public ResponseEntity<Map<String, Object>> reprogramar(
            @PathVariable("planeacion_id") UUID planeacionId,
            @RequestBody ReprogramarRequest body) {
        commands.reprogramar(planeacionId, body.nueva_fecha());
        return ResponseEntity.ok(Map.of("id", planeacionId.toString(), "fecha_planeada", body.nueva_fecha().toString()));
    }

    // ── FASE 2: Planeaciones Semanales (trimestre + semana + modalidad) ────────

    /**
     * GET /api/v1/planeacion/semanal?grupo_id=...&trimestre=1&semana=5&materia_id=...
     * Obtener planeación de una semana específica con filtros por trimestre/semana/materia.
     */
    @GetMapping("/semanal")
    public ResponseEntity<List<Map<String, Object>>> getPlaneacionSemanal(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "trimestre", required = false) Integer trimestre,
            @RequestParam(value = "semana", required = false) Integer semana,
            @RequestParam(value = "materia_id", required = false) UUID materiaId) {
        return ResponseEntity.ok(queries.getPlaneacionSemanal(grupoId, trimestre, semana, materiaId));
    }

    /**
     * GET /api/v1/planeacion/cobertura-semanal?grupo_id=...&trimestre=1
     * Obtener cobertura % por semana (para dashboard).
     */
    @GetMapping("/cobertura-semanal")
    public ResponseEntity<List<Map<String, Object>>> getCoberturaSemanal(
            @RequestParam("grupo_id") UUID grupoId,
            @RequestParam(value = "trimestre", required = false) Integer trimestre) {
        return ResponseEntity.ok(queries.getCoberturaSemanal(grupoId, trimestre));
    }

    /**
     * POST /api/v1/planeacion/semanal
     * Crear planeación con trimestre, semana y modalidad.
     */
    public record PlaneacionSemanalCreateRequest(
            UUID grupo_id,
            UUID tema_id,
            UUID competencia_id,
            Integer numero_trimestre,
            Integer numero_semana,
            String modalidad,  // PRESENCIAL, VIRTUAL, HIBRIDA
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_planeada,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_fin,
            String descripcion_actividades,
            String recursos_didacticos
    ) {}

    @PostMapping("/semanal")
    public ResponseEntity<Map<String, Object>> crearPlaneacionSemanal(
            @RequestBody PlaneacionSemanalCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearPlaneacionSemanal(
                        body.grupo_id(), body.tema_id(), body.competencia_id(),
                        body.numero_trimestre(), body.numero_semana(), body.modalidad(),
                        body.fecha_planeada(), body.fecha_fin(),
                        body.descripcion_actividades(), body.recursos_didacticos()));
    }

    /**
     * PATCH /api/v1/planeacion/{id}/semanal
     * Actualizar planeación con nuevos campos (trimestre, semana, modalidad).
     */
    public record PlaneacionSemanalUpdateRequest(
            Integer numero_trimestre,
            Integer numero_semana,
            String modalidad,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_fin,
            String descripcion_actividades,
            String recursos_didacticos
    ) {}

    @PatchMapping("/{planeacion_id}/semanal")
    public ResponseEntity<Map<String, Object>> actualizarPlaneacionSemanal(
            @PathVariable("planeacion_id") UUID planeacionId,
            @RequestBody PlaneacionSemanalUpdateRequest body) {
        return ResponseEntity.ok(commands.actualizarPlaneacionSemanal(
                planeacionId,
                body.numero_trimestre(), body.numero_semana(), body.modalidad(),
                body.fecha_fin(), body.descripcion_actividades(), body.recursos_didacticos()));
    }

    // ── FASE 2: Planeación Semanal Integral (con aprendizajes) ──────────────────

    /**
     * GET /api/v1/planeacion/temario-aprendizajes
     * Obtener temario y aprendizajes esperados disponibles para una materia/grado.
     * Usado para llenar selectores al crear planeación.
     */
    @GetMapping("/temario-aprendizajes")
    public ResponseEntity<Map<String, Object>> getTemarioYAprendizajes(
            @RequestParam("materia_id") UUID materiaId,
            @RequestParam("grado_id") UUID gradoId) {
        return ResponseEntity.ok(queries.getTemarioYAprendizajes(materiaId, gradoId));
    }

    /**
     * POST /api/v1/planeacion/semanal-integral
     * Crear planeación semanal con múltiples temas y sus aprendizajes esperados.
     *
     * Body JSON:
     * {
     *   "grupo_id": "uuid",
     *   "materia_id": "uuid",
     *   "trimestre": 1,
     *   "semana": 5,
     *   "modalidad": "PRESENCIAL",
     *   "fecha_inicio": "2026-07-08",
     *   "fecha_fin": "2026-07-12",
     *   "temas_seleccionados": [
     *     {
     *       "tema_id": "uuid",
     *       "aprendizajes_ids": ["uuid", "uuid"]
     *     }
     *   ]
     * }
     */
    public record CrearPlaneacionSemanalIntegralRequest(
            UUID grupo_id,
            UUID materia_id,
            Integer trimestre,
            Integer semana,
            String modalidad,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_inicio,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_fin,
            List<Map<String, Object>> temas_seleccionados
    ) {}

    @PostMapping("/semanal-integral")
    public ResponseEntity<Map<String, Object>> crearPlaneacionSemanalIntegral(
            @RequestBody CrearPlaneacionSemanalIntegralRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearPlaneacionSemanal(
                        body.grupo_id(),
                        body.materia_id(),
                        body.trimestre(),
                        body.semana(),
                        body.modalidad(),
                        body.fecha_inicio(),
                        body.fecha_fin(),
                        body.temas_seleccionados()
                )
        );
    }

    // ── FASE 3: Tareas/Exámenes Vinculados ────────────────────────────────────

    /**
     * GET /api/v1/planeacion/grupo/{grupo_id}/planeaciones-activas
     * Obtener planeaciones activas de un grupo para crear tareas/exámenes vinculados.
     */
    @GetMapping("/grupo/{grupo_id}/planeaciones-activas")
    public ResponseEntity<List<Map<String, Object>>> getPlaneacionesActivas(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(queries.getPlaneacionesDelGrupo(grupoId));
    }

    /**
     * GET /api/v1/planeacion/{planeacion_id}/detalles
     * Obtener detalles completos de una planeación (incluye aprendizajes vinculados).
     */
    @GetMapping("/{planeacion_id}/detalles")
    public ResponseEntity<Map<String, Object>> getDetallesplanneacion(
            @PathVariable("planeacion_id") UUID planeacionClaseId) {
        return ResponseEntity.ok(queries.getDetallesplanneacion(planeacionClaseId));
    }

    /**
     * POST /api/v1/planeacion/tareas/desde-planeacion
     * Crear tarea vinculada a una planeación semanal.
     *
     * Body JSON:
     * {
     *   "planeacion_clase_id": "uuid",
     *   "titulo": "Resolver problemas de suma",
     *   "descripcion": "Ejercicios del tema 5",
     *   "fecha_entrega": "2026-07-15",
     *   "puntaje_maximo": 10.0,
     *   "permite_entrega_tarde": false,
     *   "instrucciones_url": "http://..."
     * }
     *
     * La tarea hereda automáticamente:
     * - planeacion_clase_id (explícito)
     * - grupo_id (de la planeación)
     * - materia_id (del tema en planeación)
     * - aprendizajes_esperados[] (via trigger BEFORE INSERT)
     */
    public record CrearTareaDesdeplanneacionRequest(
            UUID planeacion_clase_id,
            String titulo,
            String descripcion,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_entrega,
            Double puntaje_maximo,
            Boolean permite_entrega_tarde,
            String instrucciones_url
    ) {}

    @PostMapping("/tareas/desde-planeacion")
    public ResponseEntity<Map<String, Object>> crearTareaDesdeplanneacion(
            @RequestBody CrearTareaDesdeplanneacionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearTareaDesdeplanneacion(
                        body.planeacion_clase_id(),
                        body.titulo(),
                        body.descripcion(),
                        body.fecha_entrega(),
                        body.puntaje_maximo(),
                        body.permite_entrega_tarde(),
                        body.instrucciones_url()
                )
        );
    }

    /**
     * POST /api/v1/planeacion/examenes/desde-planeacion
     * Crear examen vinculado a una planeación semanal.
     *
     * Body JSON:
     * {
     *   "planeacion_clase_id": "uuid",
     *   "nombre_evaluacion": "Examen Período 1",
     *   "descripcion": "Evaluación sumativa tema 1-5",
     *   "fecha": "2026-07-15",
     *   "puntaje_maximo": 10.0
     * }
     */
    public record CrearExamenDesdeplanneacionRequest(
            UUID planeacion_clase_id,
            String nombre_evaluacion,
            String descripcion,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Double puntaje_maximo
    ) {}

    @PostMapping("/examenes/desde-planeacion")
    public ResponseEntity<Map<String, Object>> crearExamenDesdeplanneacion(
            @RequestBody CrearExamenDesdeplanneacionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                commands.crearExamenDesdeplanneacion(
                        body.planeacion_clase_id(),
                        body.nombre_evaluacion(),
                        body.descripcion(),
                        body.fecha(),
                        body.puntaje_maximo()
                )
        );
    }

    // ── FASE 4: Calificaciones + Boletas + Estadísticas ────────────────────────

    // --- SUBCAPA 4A: Obtener tareas/exámenes pendientes de calificar ---

    /**
     * GET /api/v1/planeacion/grupo/{grupo_id}/tareas-pendientes-calificar
     * Obtener tareas del grupo con entregas sin calificar.
     */
    @GetMapping("/grupo/{grupo_id}/tareas-pendientes-calificar")
    public ResponseEntity<List<Map<String, Object>>> getTareasPendientes(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(
            calificacionesQueries.getTareasPendientesCalificar(grupoId)
        );
    }

    /**
     * GET /api/v1/planeacion/tareas/{tarea_id}/detalles
     * Obtener detalles de tarea con aprendizajes y entregas.
     */
    @GetMapping("/tareas/{tarea_id}/detalles")
    public ResponseEntity<Map<String, Object>> getDetallesTarea(
            @PathVariable("tarea_id") UUID tareaId) {
        return ResponseEntity.ok(
            calificacionesQueries.getDetallesTarea(tareaId)
        );
    }

    /**
     * GET /api/v1/planeacion/grupo/{grupo_id}/examenes-pendientes-calificar
     * Obtener evaluaciones del grupo sin calificar.
     */
    @GetMapping("/grupo/{grupo_id}/examenes-pendientes-calificar")
    public ResponseEntity<List<Map<String, Object>>> getExamenesPendientes(
            @PathVariable("grupo_id") UUID grupoId) {
        return ResponseEntity.ok(
            calificacionesQueries.getExamenesPendientesCalificar(grupoId)
        );
    }

    /**
     * GET /api/v1/planeacion/examenes/{evaluacion_id}/detalles
     * Obtener detalles de evaluación con aprendizajes y estudiantes.
     */
    @GetMapping("/examenes/{evaluacion_id}/detalles")
    public ResponseEntity<Map<String, Object>> getDetallesEvaluacion(
            @PathVariable("evaluacion_id") UUID evaluacionId) {
        return ResponseEntity.ok(
            calificacionesQueries.getDetallesEvaluacion(evaluacionId)
        );
    }

    // --- SUBCAPA 4B: Guardar calificaciones ---

    /**
     * POST /api/v1/planeacion/calificaciones/tarea
     * Guardar calificación de una tarea para un alumno.
     */
    public record GuardarCalificacionTareaRequest(
            UUID tarea_id,
            UUID alumno_id,
            Double calificacion,
            String comentarios
    ) {}

    @PostMapping("/calificaciones/tarea")
    public ResponseEntity<Map<String, Object>> guardarCalificacionTarea(
            @RequestBody GuardarCalificacionTareaRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            calificacionesCommands.guardarCalificacionTarea(
                body.tarea_id(),
                body.alumno_id(),
                body.calificacion(),
                body.comentarios()
            )
        );
    }

    /**
     * POST /api/v1/planeacion/calificaciones/evaluacion
     * Guardar calificación de una evaluación para un estudiante.
     */
    public record GuardarCalificacionEvaluacionRequest(
            UUID evaluacion_id,
            UUID estudiante_id,
            Double calificacion,
            String comentarios
    ) {}

    @PostMapping("/calificaciones/evaluacion")
    public ResponseEntity<Map<String, Object>> guardarCalificacionEvaluacion(
            @RequestBody GuardarCalificacionEvaluacionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            calificacionesCommands.guardarCalificacionEvaluacion(
                body.evaluacion_id(),
                body.estudiante_id(),
                body.calificacion(),
                body.comentarios()
            )
        );
    }

    /**
     * POST /api/v1/planeacion/calificaciones/tarea-batch
     * Guardar múltiples calificaciones de una tarea (bulk).
     */
    public record GuardarCalificacionesTareaBatchRequest(
            UUID tarea_id,
            List<Map<String, Object>> calificaciones
    ) {}

    @PostMapping("/calificaciones/tarea-batch")
    public ResponseEntity<Map<String, Object>> guardarCalificacionesTareaBatch(
            @RequestBody GuardarCalificacionesTareaBatchRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            calificacionesCommands.guardarCalificacionesTareaBatch(
                body.tarea_id(),
                body.calificaciones()
            )
        );
    }

    /**
     * POST /api/v1/planeacion/calificaciones/evaluacion-batch
     * Guardar múltiples calificaciones de una evaluación (bulk).
     */
    public record GuardarCalificacionesEvaluacionBatchRequest(
            UUID evaluacion_id,
            List<Map<String, Object>> calificaciones
    ) {}

    @PostMapping("/calificaciones/evaluacion-batch")
    public ResponseEntity<Map<String, Object>> guardarCalificacionesEvaluacionBatch(
            @RequestBody GuardarCalificacionesEvaluacionBatchRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            calificacionesCommands.guardarCalificacionesEvaluacionBatch(
                body.evaluacion_id(),
                body.calificaciones()
            )
        );
    }

    // --- SUBCAPA 4C: Boletas + Estadísticas ---

    /**
     * GET /api/v1/planeacion/boleta/{alumno_id}/{grupo_id}/{trimestre}
     * Calcular boleta de un alumno basada en planeación → tareas → calificaciones.
     */
    @GetMapping("/boleta/{alumno_id}/{grupo_id}/{trimestre}")
    public ResponseEntity<Map<String, Object>> calcularBoleta(
            @PathVariable("alumno_id") UUID alumnoId,
            @PathVariable("grupo_id") UUID grupoId,
            @PathVariable("trimestre") Integer trimestre) {
        return ResponseEntity.ok(
            bolecaQueries.calcularBolecaDesdeplanneacion(alumnoId, grupoId, trimestre)
        );
    }

    /**
     * GET /api/v1/planeacion/cobertura/{grupo_id}/{trimestre}
     * Obtener cobertura curricular del grupo (% aprendizajes planeados vs evaluados).
     */
    @GetMapping("/cobertura/{grupo_id}/{trimestre}")
    public ResponseEntity<Map<String, Object>> getCoberturaCurricular(
            @PathVariable("grupo_id") UUID grupoId,
            @PathVariable("trimestre") Integer trimestre) {
        return ResponseEntity.ok(
            bolecaQueries.getCoberturaCurricularGrupo(grupoId, trimestre)
        );
    }

    /**
     * GET /api/v1/planeacion/cobertura-por-competencia/{grupo_id}/{trimestre}
     * Obtener cobertura desglosada por competencia y materia.
     */
    @GetMapping("/cobertura-por-competencia/{grupo_id}/{trimestre}")
    public ResponseEntity<List<Map<String, Object>>> getCoberturaPorCompetencia(
            @PathVariable("grupo_id") UUID grupoId,
            @PathVariable("trimestre") Integer trimestre) {
        return ResponseEntity.ok(
            bolecaQueries.getCoberturaPorCompetencia(grupoId, trimestre)
        );
    }
}

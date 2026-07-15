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
        if (nivel <= 3) return; // admin/director/coordinador: alcance institucional
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

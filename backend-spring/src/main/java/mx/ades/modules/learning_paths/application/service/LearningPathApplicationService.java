package mx.ades.modules.learning_paths.application.service;

import mx.ades.modules.learning_paths.domain.model.EstatusAsignacion;
import mx.ades.modules.learning_paths.domain.port.in.AsignarPathUseCase;
import mx.ades.modules.learning_paths.domain.port.in.CrearLearningPathUseCase;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Caso de uso: gestión de rutas de aprendizaje personalizadas para alumnos en riesgo.
 * Implementa {@link RegistrarProgresoUseCase}, {@link CrearLearningPathUseCase}
 * y {@link AsignarPathUseCase} coordinando el dominio de learning paths con
 * el puerto de repositorio, con asignación automática basada en alertas de
 * reprobación y ausentismo crítico detectadas por el sistema de IA.
 *
 * @author ADES
 * @since 2026
 */
public class LearningPathApplicationService
        implements RegistrarProgresoUseCase, CrearLearningPathUseCase, AsignarPathUseCase {

    private final LearningPathRepositoryPort repo;

    public LearningPathApplicationService(LearningPathRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public RegistrarProgresoUseCase.Result ejecutar(RegistrarProgresoUseCase.Command command) {
        repo.upsertProgreso(command.asignacionId(), command.recursoId(),
                command.tiempoMin(), command.calificacion());
        LearningPathRepositoryPort.ProgresoStats stats = repo.calcularProgreso(command.asignacionId());
        EstatusAsignacion nuevoEstatus = EstatusAsignacion.PENDIENTE
                .transicion(stats.totalCompletados(), stats.totalObligatorios());
        repo.actualizarEstatus(command.asignacionId(), nuevoEstatus.name(), stats.pctCompletado());
        return new RegistrarProgresoUseCase.Result(nuevoEstatus.name(), stats.pctCompletado());
    }

    @Override
    @Transactional
    public Map<String, Object> crear(CrearLearningPathUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        return repo.insertPath(id, cmd.nombre(), cmd.descripcion(), cmd.nivelEducativoId(),
                cmd.materiaId(), cmd.criterioActivacion(), cmd.umbralActivacion());
    }

    @Override
    @Transactional
    public Map<String, Object> asignar(AsignarPathUseCase.Command cmd) {
        String pathNombre = repo.fetchPathNombre(cmd.pathId())
                .orElseThrow(() -> new IllegalStateException("Learning path no encontrado"));
        UUID id = UUID.randomUUID();
        Map<String, Object> result = new HashMap<>(repo.upsertAsignacion(id, cmd.pathId(),
                cmd.estudianteId(), cmd.asignadoPor(), cmd.motivo()));
        result.put("path_nombre", pathNombre);
        return result;
    }

    @Transactional
    public Map<String, Object> agregarRecurso(UUID pathId, Integer orden, String tipo, String titulo,
                                               String descripcion, String urlRecurso,
                                               Integer duracionMin, Boolean obligatorio) {
        if (!repo.existsPath(pathId)) throw new IllegalStateException("Learning path no encontrado");
        // ades_lp_recursos.titulo es NOT NULL — el DTO de entrada (RecursoRequest) no
        // tenía ninguna anotación Jakarta ni validación aquí; un titulo nulo llegaba
        // hasta el INSERT y disparaba una violación NOT NULL a nivel BD en vez de un
        // 400 claro (hallazgo de auditoría de consistencia BD↔backend).
        if (titulo == null || titulo.isBlank())
            throw new IllegalArgumentException("titulo es requerido");
        UUID id = UUID.randomUUID();
        return repo.insertRecurso(id, pathId, orden, tipo, titulo, descripcion, urlRecurso, duracionMin, obligatorio);
    }

    @Transactional
    public int asignarAutomatico(UUID grupoId, UUID asignadoPor) {
        List<Map<String, Object>> alertas = repo.fetchAlertasByGrupo(grupoId);
        if (alertas.isEmpty()) return 0;
        List<Map<String, Object>> paths = repo.fetchPathsByCriterios(
                List.of("REPROBACION", "AUSENTISMO", "RIESGO_ALTO"));
        Map<String, String> tipoACriterio = Map.of(
                "RIESGO_REPROBACION", "REPROBACION",
                "AUSENTISMO_CRITICO", "AUSENTISMO");
        Map<String, UUID> pathPorCriterio = new HashMap<>();
        for (Map<String, Object> p : paths) {
            pathPorCriterio.put((String) p.get("criterio_activacion"), (UUID) p.get("id"));
        }
        int asignadas = 0;
        for (Map<String, Object> alerta : alertas) {
            String criterio = tipoACriterio.get((String) alerta.get("tipo_alerta"));
            if (criterio == null) continue;
            UUID pathId = pathPorCriterio.get(criterio);
            if (pathId == null) continue;
            repo.insertAsignacionAuto(pathId, (UUID) alerta.get("estudiante_id"),
                    asignadoPor, "AUTO_" + alerta.get("tipo_alerta"));
            asignadas++;
        }
        return asignadas;
    }
}

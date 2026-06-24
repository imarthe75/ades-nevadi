package mx.ades.modules.learning_paths.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para learning paths, recursos, asignaciones
 * y progreso de estudiantes.
 *
 * @author ADES
 * @since 2026
 */
public interface LearningPathRepositoryPort {

    record ProgresoStats(int totalCompletados, int totalObligatorios, double pctCompletado) {}

    // progreso (existing)
    void upsertProgreso(UUID asignacionId, UUID recursoId, Integer tiempoMin, Double calificacion);
    ProgresoStats calcularProgreso(UUID asignacionId);
    void actualizarEstatus(UUID asignacionId, String estatus, double pctCompletado);

    // nuevos
    Map<String, Object> insertPath(UUID id, String nombre, String descripcion, UUID nivelEducativoId,
                                   UUID materiaId, String criterioActivacion, Double umbralActivacion);

    boolean existsPath(UUID pathId);

    Optional<String> fetchPathNombre(UUID pathId);

    Map<String, Object> insertRecurso(UUID id, UUID pathId, Integer orden, String tipo,
                                      String titulo, String descripcion, String urlRecurso,
                                      Integer duracionMin, Boolean obligatorio);

    Map<String, Object> upsertAsignacion(UUID id, UUID pathId, UUID estudianteId,
                                         UUID asignadoPor, String motivo);

    List<Map<String, Object>> fetchAlertasByGrupo(UUID grupoId);

    List<Map<String, Object>> fetchPathsByCriterios(List<String> criterios);

    void insertAsignacionAuto(UUID pathId, UUID estudianteId, UUID asignadoPor, String motivo);
}

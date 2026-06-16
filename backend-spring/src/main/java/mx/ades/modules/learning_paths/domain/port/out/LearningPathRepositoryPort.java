package mx.ades.modules.learning_paths.domain.port.out;

import java.util.UUID;

public interface LearningPathRepositoryPort {

    record ProgresoStats(int totalCompletados, int totalObligatorios, double pctCompletado) {}

    void upsertProgreso(UUID asignacionId, UUID recursoId, Integer tiempoMin, Double calificacion);

    ProgresoStats calcularProgreso(UUID asignacionId);

    void actualizarEstatus(UUID asignacionId, String estatus, double pctCompletado);
}

package mx.ades.modules.entregas.domain.port.out;

import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para entregas de tareas en {@code ades_tareas_entregas}.
 *
 * @author ADES
 * @since 2026
 */
public interface EntregaRepositoryPort {
    List<Map<String, Object>> listByAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, boolean soloPendientes);
    List<Map<String, Object>> pendientesByGrupo(UUID grupoId, UUID materiaId);
    void upsertEntrega(SubirEntregaUseCase.Command cmd);

    /**
     * H-3 (auditoría 2026-07-20, mismo hallazgo que la ruta de calificación masiva):
     * calificar una entrega en PENDIENTE (nunca subida por el alumno) está permitido,
     * pero debe quedar distinguible de una entrega real revisada.
     */
    record CalificarResult(int rows, boolean sinEntrega) {}

    CalificarResult calificar(CalificarEntregaUseCase.Command cmd);
    int registrarExcusa(UUID entregaId, String motivo, String usuario);

    /** OA-020: reabre una entrega calificada/excusada para permitir una nueva entrega. */
    int reabrir(UUID entregaId, String motivo, String usuario);
}

package mx.ades.modules.justificaciones.domain.port.out;

import mx.ades.modules.justificaciones.domain.model.EstadoJustificacion;
import mx.ades.modules.justificaciones.domain.model.TipoJustificacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface JustificacionRepositoryPort {

    Optional<Map<String, Object>> findById(UUID id);

    boolean existsByAsistenciaId(UUID asistenciaId);

    /** Returns true if asistencia exists, is active, and is NOT PRESENTE */
    boolean asistenciaJustificable(UUID asistenciaId);

    UUID create(UUID asistenciaId, TipoJustificacion tipo, String motivo,
                String documentoUrl, UUID usuarioId);

    void linkToAsistencia(UUID asistenciaId, UUID justificacionId);

    String resolve(UUID id, EstadoJustificacion estado, UUID aprobadaPor,
                   String motivoRechazo, String usuarioMod);

    List<Map<String, Object>> list(UUID estudianteId, String estado, UUID grupoId, int pagina, int porPagina, UUID plantelId);

    UUID estudianteDeAsistencia(UUID asistenciaId);

    UUID asistenciaDeJustificacion(UUID justificacionId);
}

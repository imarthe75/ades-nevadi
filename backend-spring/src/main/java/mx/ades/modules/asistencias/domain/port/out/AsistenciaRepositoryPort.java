package mx.ades.modules.asistencias.domain.port.out;

import mx.ades.modules.asistencias.domain.model.Asistencia;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para la entidad {@code Asistencia}
 * en la tabla {@code ades_asistencias}.
 *
 * <p>Incluye consulta por clase, guardado masivo con upsert y conteo de asistencias
 * válidas (PRESENTE, TARDE, JUSTIFICADO) por estudiante y grupo.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface AsistenciaRepositoryPort {
    List<Asistencia> findByClaseId(UUID claseId);
    void guardarMasivo(List<Asistencia> asistencias);
    long contarAsistenciasByEstudiante(UUID estudianteId, UUID grupoId);
}

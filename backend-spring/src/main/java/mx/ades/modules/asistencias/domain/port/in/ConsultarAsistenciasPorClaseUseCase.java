package mx.ades.modules.asistencias.domain.port.in;

import mx.ades.modules.asistencias.domain.model.Asistencia;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para consultar todas las asistencias
 * registradas para una clase específica en el dominio de asistencias.
 *
 * @author ADES
 * @since 2026
 */
public interface ConsultarAsistenciasPorClaseUseCase {
    List<Asistencia> ejecutar(UUID claseId);
}

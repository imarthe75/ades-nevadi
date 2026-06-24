package mx.ades.modules.expediente.domain.port.out;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para bajas de alumnos en {@code ades_bajas}.
 *
 * @author ADES
 * @since 2026
 */
public interface BajaRepositoryPort {

    UUID guardar(UUID estudianteId, TipoBaja tipo, String motivo,
                 LocalDate fechaEfectiva, LocalDate fechaReingreso,
                 String plantelDestino, String claveCtDestino,
                 String observaciones, UUID autorizadoPorId);

    void desactivarEstudiante(UUID estudianteId);
}

package mx.ades.modules.expediente.domain.port.out;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.LocalDate;
import java.util.UUID;

public interface BajaRepositoryPort {

    UUID guardar(UUID estudianteId, TipoBaja tipo, String motivo,
                 LocalDate fechaEfectiva, LocalDate fechaReingreso,
                 String plantelDestino, String claveCtDestino,
                 String observaciones, UUID autorizadoPorId);

    void desactivarEstudiante(UUID estudianteId);
}

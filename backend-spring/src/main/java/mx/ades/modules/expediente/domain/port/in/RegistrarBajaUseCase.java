package mx.ades.modules.expediente.domain.port.in;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarBajaUseCase {

    record Command(
            UUID estudianteId,
            TipoBaja tipo,
            String motivo,
            LocalDate fechaEfectiva,
            LocalDate fechaReingreso,
            String plantelDestino,
            String claveCtDestino,
            String observaciones,
            UUID autorizadoPorId,
            String username) {}

    record Result(UUID bajaId, boolean estudianteDesactivado) {}

    Result ejecutar(Command command);
}

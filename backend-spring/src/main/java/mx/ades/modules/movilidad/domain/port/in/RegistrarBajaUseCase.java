package mx.ades.modules.movilidad.domain.port.in;

import mx.ades.modules.movilidad.domain.model.TipoMovilidad;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarBajaUseCase {

    record Command(
            UUID estudianteId,
            TipoMovilidad tipo,
            String motivo,
            LocalDate fechaEfectiva,
            LocalDate fechaReingreso,
            String plantelDestino,
            String claveCtDestino,
            String observaciones,
            UUID grupoDestinoId,
            UUID autorizadoPorId,
            String usuarioModificacion
    ) {
        public Command {
            if (estudianteId == null) throw new IllegalArgumentException("estudianteId requerido");
            if (tipo == null || !tipo.generaRegistroBaja())
                throw new IllegalArgumentException("El tipo " + tipo + " no genera baja");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("motivo requerido");
        }
    }

    record Result(String mensaje, TipoMovilidad tipo, LocalDate fechaEfectiva) {}

    Result ejecutar(Command command);
}

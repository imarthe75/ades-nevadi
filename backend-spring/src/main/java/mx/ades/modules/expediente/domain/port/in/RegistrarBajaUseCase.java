package mx.ades.modules.expediente.domain.port.in;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar la baja de un alumno en el módulo expediente.
 * <p>Desactiva al estudiante en el sistema y publica {@code BajaRegistradaEvent}.
 * Soporta traslado (con plantel destino y clave CT), deserción y baja administrativa.</p>
 *
 * @author ADES
 * @since 2026
 */
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

package mx.ades.modules.asistencia_personal.domain.port.in;

import mx.ades.modules.asistencia_personal.domain.model.TipoJornada;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para actualizar (PATCH) un registro de asistencia
 * del personal en el dominio de asistencia_personal.
 *
 * <p>Solo usuarios con {@code nivelAcceso <= 3} (Coordinador o superior) pueden
 * justificar asistencias.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarAsistenciaUseCase {

    record Patch(LocalTime horaEntrada, LocalTime horaSalida, TipoJornada tipoJornada,
                 Boolean esRetardo, Integer minutosRetardo, String observaciones,
                 Boolean justificado, String justificacion) {}

    record Command(UUID id, Patch patch, UUID justificadoPor, String usuario, int nivelAcceso) {
        public Command {
            if (id == null) throw new IllegalArgumentException("id es requerido");
            if (patch.justificado() != null && nivelAcceso > 3)
                throw new IllegalArgumentException("Solo Coordinador o superior puede justificar asistencias");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}

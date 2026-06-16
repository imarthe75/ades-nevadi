package mx.ades.modules.asistencia_personal.domain.port.in;

import mx.ades.modules.asistencia_personal.domain.model.TipoJornada;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

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

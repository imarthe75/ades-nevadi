package mx.ades.modules.asistencia_personal.domain.port.in;

import mx.ades.modules.asistencia_personal.domain.model.TipoJornada;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface RegistrarAsistenciaUseCase {

    record Command(UUID personaId, LocalDate fecha, LocalTime horaEntrada, LocalTime horaSalida,
                   TipoJornada tipoJornada, boolean esRetardo, int minutosRetardo,
                   String observaciones, String usuario) {
        public Command {
            if (personaId == null) throw new IllegalArgumentException("persona_id es requerido");
            if (fecha == null) throw new IllegalArgumentException("fecha es requerida");
        }
    }

    /** Upsert: creates or updates the record for (personaId, fecha). Returns updated row. */
    java.util.Map<String, Object> registrar(Command cmd);
}

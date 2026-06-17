package mx.ades.modules.profesores.domain.port.in;

import mx.ades.modules.profesores.Profesor;

import java.util.UUID;

public interface CrearProfesorUseCase {

    record Command(
            UUID personaId,
            UUID plantelId,
            String numeroEmpleado,
            String tipoContrato
    ) {
        public Command {
            if (personaId == null)
                throw new IllegalArgumentException("persona_id es requerido");
            if (plantelId == null)
                throw new IllegalArgumentException("plantel_id es requerido");
        }
    }

    Profesor crear(Command cmd);
}

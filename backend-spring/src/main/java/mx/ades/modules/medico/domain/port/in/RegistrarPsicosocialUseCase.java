package mx.ades.modules.medico.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarPsicosocialUseCase {

    record Command(
            UUID alumnoId, String tipoAtencion, String motivo, String observaciones,
            String estrategiasSugeridas, Boolean requiereDerivacion, String derivadoA,
            LocalDate proximaSesion, Integer nivelAcceso, String usuario) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID registrar(Command cmd);
}

package mx.ades.modules.medico.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarTutoriaUseCase {

    record Command(
            UUID alumnoId, String tipoTutoria, String tema, String descripcion,
            Integer duracionMinutos, String acuerdos, LocalDate proximaSesion,
            Boolean requiereSeguimiento, Integer nivelAcceso, String usuario) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
            if (tipoTutoria == null || tipoTutoria.isBlank())
                throw new IllegalArgumentException("tipo_tutoria es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID registrar(Command cmd);
}

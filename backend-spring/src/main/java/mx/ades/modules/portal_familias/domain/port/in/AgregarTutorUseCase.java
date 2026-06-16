package mx.ades.modules.portal_familias.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface AgregarTutorUseCase {

    Map<String, Object> agregar(Command cmd);

    record Command(
        UUID alumnoId,
        UUID personaId,
        String relacion,
        Integer prioridad,
        Boolean puedeRecoger,
        Boolean esResponsableEconomico,
        Boolean esContactoEmergencia,
        String nivelAccesoPortal,
        String usuarioCreacion
    ) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumnoId es requerido");
            if (personaId == null) throw new IllegalArgumentException("personaId es requerido");
            if (relacion == null || relacion.isBlank()) relacion = "TUTOR";
            if (nivelAccesoPortal == null || nivelAccesoPortal.isBlank()) nivelAccesoPortal = "LECTURA";
        }
    }
}

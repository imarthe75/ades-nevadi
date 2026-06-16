package mx.ades.modules.procesos.domain.port.in;

import java.util.UUID;

public interface ProcesarPreinscripcionUseCase {

    record Command(UUID admisionId, UUID cicloEscolarId, UUID grupoId, String username) {
        public Command {
            if (admisionId == null)    throw new IllegalArgumentException("admision_id es obligatorio");
            if (grupoId == null)       throw new IllegalArgumentException("grupo_id es obligatorio");
            if (cicloEscolarId == null) throw new IllegalArgumentException("ciclo_escolar_id es obligatorio");
        }
    }

    record PreinscripcionResult(UUID estudianteId, String matricula,
                                String nombre, String apellidoPaterno, String curp) {}

    PreinscripcionResult ejecutar(Command command);
}

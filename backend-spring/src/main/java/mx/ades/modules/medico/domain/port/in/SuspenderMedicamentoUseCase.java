package mx.ades.modules.medico.domain.port.in;

import java.util.UUID;

public interface SuspenderMedicamentoUseCase {

    record Command(UUID medicamentoId, Integer nivelAcceso, String usuario) {
        public Command {
            if (medicamentoId == null) throw new IllegalArgumentException("medicamento_id es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    void suspender(Command cmd);
}

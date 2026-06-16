package mx.ades.modules.medico.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface RegistrarMedicamentoUseCase {

    record Command(
            UUID alumnoId, String nombreMedicamento, String dosis, String frecuencia,
            String horario, String viaAdministracion, String prescritoPor,
            LocalDate fechaInicio, LocalDate fechaFin, String observaciones,
            Integer nivelAcceso, String usuario) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
            if (nombreMedicamento == null || nombreMedicamento.isBlank())
                throw new IllegalArgumentException("nombre_medicamento es requerido");
            if (nivelAcceso == null || nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere nivel Médico/Coordinador o superior");
        }
    }

    UUID registrar(Command cmd);
}

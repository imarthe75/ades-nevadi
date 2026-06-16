package mx.ades.modules.expediente_laboral.domain.port.in;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface CrearExpedienteLaboralUseCase {

    record Command(UUID personaId, String tipoContrato, LocalDate fechaContratacion,
                   LocalDate fechaFinContrato, Double salarioMensual, String imssNumero,
                   String infonavitNumero, String curp, String rfc, String cedulaProfesional,
                   String nivelEstudios, String especialidad, String institucionFormacion,
                   String claveCt, String claveIssste, String usuarioId, int nivelAcceso) {
        public Command {
            if (personaId == null) throw new IllegalArgumentException("persona_id es requerido");
            if (nivelAcceso > 2) throw new IllegalArgumentException("Solo RH o Dirección puede crear expedientes laborales");
            if (fechaFinContrato != null && fechaContratacion != null && fechaFinContrato.isBefore(fechaContratacion))
                throw new IllegalArgumentException("fecha_fin_contrato debe ser >= fecha_contratacion");
        }
    }

    Map<String, Object> crear(Command cmd);
}

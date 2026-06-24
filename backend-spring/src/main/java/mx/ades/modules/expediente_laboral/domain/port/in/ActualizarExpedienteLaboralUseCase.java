package mx.ades.modules.expediente_laboral.domain.port.in;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para actualizar (PATCH) un expediente laboral existente.
 * Solo accesible para usuarios con nivelAcceso {@literal <=} 2 (RH o Dirección).
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarExpedienteLaboralUseCase {

    record Patch(String tipoContrato, LocalDate fechaFinContrato, Double salarioMensual,
                 String imssNumero, String infonavitNumero, String curp, String rfc,
                 String cedulaProfesional, String nivelEstudios, String especialidad,
                 String institucionFormacion, String claveCt, String claveIssste) {}

    record Command(UUID id, Patch patch, String usuarioId, int nivelAcceso) {
        public Command {
            if (id == null) throw new IllegalArgumentException("id es requerido");
            if (nivelAcceso > 2) throw new IllegalArgumentException("Solo RH o Dirección puede editar expedientes");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}

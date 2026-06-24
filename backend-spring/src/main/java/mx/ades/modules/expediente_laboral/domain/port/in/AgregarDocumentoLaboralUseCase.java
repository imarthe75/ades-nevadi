package mx.ades.modules.expediente_laboral.domain.port.in;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para agregar un documento digital al expediente laboral
 * (contrato, título, cédula, NSS, identificación, acta de nacimiento, CURP, IMSS u otro).
 *
 * @author ADES
 * @since 2026
 */
public interface AgregarDocumentoLaboralUseCase {

    Set<String> TIPOS_VALIDOS = Set.of(
            "contrato", "titulo", "cedula", "nss", "identificacion",
            "acta_nacimiento", "curp_doc", "imss", "otro");

    record Command(UUID expedienteId, String tipoDocumento, String url, String usuarioId) {
        public Command {
            if (expedienteId == null) throw new IllegalArgumentException("expediente_id es requerido");
            if (tipoDocumento == null || !TIPOS_VALIDOS.contains(tipoDocumento))
                throw new IllegalArgumentException("tipo_documento inválido: " + tipoDocumento);
        }
    }

    Map<String, Object> agregar(Command cmd);
}

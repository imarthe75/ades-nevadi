package mx.ades.modules.compliance.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar una retención de alumno en el módulo compliance.
 * <p>Una retención es una medida administrativa que impide la reinscripción o entrega de documentos
 * hasta que se cumplan las condiciones requeridas. Requiere nivel de acceso 3 o inferior.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarRetencionUseCase {

    record Command(UUID alumnoId, String tipoRetencion, String motivo,
                   LocalDate fechaInicio, LocalDate fechaFin,
                   String accionesRequeridas, UUID autorizadoPor,
                   String usuario, int nivelAcceso) {
        public Command {
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
            if (tipoRetencion == null || tipoRetencion.isBlank()) throw new IllegalArgumentException("tipo_retencion es requerido");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("motivo es requerido");
            if (nivelAcceso > 3) throw new IllegalArgumentException("Se requiere Director o superior para registrar retención");
        }
    }

    UUID registrar(Command cmd);
}

package mx.ades.modules.licencias.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para aprobar o rechazar una solicitud de licencia del personal.
 * Al rechazar, las observaciones son obligatorias como motivo de rechazo.
 *
 * @author ADES
 * @since 2026
 */
public interface ResolverLicenciaUseCase {

    enum Accion { APROBAR, RECHAZAR }

    record Command(
            UUID licenciaId,
            Accion accion,
            String observaciones,
            UUID usuarioId,
            String usuarioNombre,
            int nivelAcceso
    ) {
        public Command {
            if (licenciaId == null)
                throw new IllegalArgumentException("licencia_id es requerido");
            if (accion == null)
                throw new IllegalArgumentException("accion es requerida");
            if (accion == Accion.RECHAZAR && (observaciones == null || observaciones.isBlank()))
                throw new IllegalArgumentException("observaciones (motivo_rechazo) es requerido al RECHAZAR");
        }
    }

    void resolver(Command cmd);
}

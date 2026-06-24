package mx.ades.modules.justificaciones.domain.port.in;

import mx.ades.modules.justificaciones.domain.model.AccionJustificacion;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para resolver (aprobar o rechazar) una justificación pendiente.
 *
 * @author ADES
 * @since 2026
 */
public interface ResolverJustificacionUseCase {

    record Command(
            UUID justificacionId,
            AccionJustificacion accion,
            String motivoRechazo,
            UUID usuarioId,
            int nivelAcceso
    ) {
        public Command {
            if (justificacionId == null)
                throw new IllegalArgumentException("justificacion_id es requerido");
            if (accion == null)
                throw new IllegalArgumentException("accion es requerida");
            if (accion.requiereMotivo() && (motivoRechazo == null || motivoRechazo.isBlank()))
                throw new IllegalArgumentException("motivo_rechazo es obligatorio al RECHAZAR");
        }
    }

    String resolver(Command cmd);
}

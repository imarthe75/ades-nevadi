package mx.ades.modules.capacitaciones.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para que el área de RH valide una capacitación
 * docente registrada en el dominio de capacitaciones.
 *
 * <p>Requiere {@code nivelAcceso <= 3} para aprobar la validación.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface ValidarCapacitacionUseCase {

    record Command(UUID capacitacionId, UUID usuarioId, String usuarioNombre, int nivelAcceso) {
        public Command {
            if (capacitacionId == null)
                throw new IllegalArgumentException("capacitacion_id es requerido");
        }
    }

    void validar(Command cmd);
}

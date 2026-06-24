package mx.ades.modules.cierre.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para ejecutar el cierre oficial de un ciclo
 * escolar en el dominio de cierre.
 *
 * <p>Operación <strong>irreversible</strong>: marca el ciclo como {@code CERRADO}
 * y lo saca de vigencia. Solo administradores o directores ({@code nivelAcceso <= 2})
 * pueden ejecutarla.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CerrarCicloUseCase {

    record Command(UUID cicloId, UUID cicloDestinoId, Integer nivelAcceso, String usuario) {
        public Command {
            if (cicloId == null) throw new IllegalArgumentException("ciclo_id es requerido");
            if (nivelAcceso == null || nivelAcceso > 2)
                throw new IllegalArgumentException("Solo administradores o directores pueden realizar el cierre de ciclo");
        }
    }

    String cerrar(Command cmd);
}

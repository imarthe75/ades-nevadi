package mx.ades.modules.condiciones.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para actualizar una condición crónica de salud en el módulo condiciones.
 * <p>Permite modificar datos de medicación, médico responsable y estado de activación del registro.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarCondicionUseCase {

    record Command(
            UUID condicionId,
            String descripcion,
            String medicacionNombre,
            String dosis,
            String frecuencia,
            String alergias,
            String medicoResponsable,
            String telefonoMedico,
            Boolean activa,
            int nivelAcceso
    ) {
        public Command {
            if (condicionId == null)
                throw new IllegalArgumentException("condicion_id es requerido");
        }
    }

    void actualizar(Command cmd);
}

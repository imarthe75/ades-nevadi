package mx.ades.modules.condiciones.domain.port.in;

import mx.ades.modules.condiciones.domain.model.TipoCondicion;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar una condición crónica de salud de un alumno
 * en el módulo condiciones.
 * <p>Los datos de medicación y contacto médico se almacenan para uso en emergencias escolares.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarCondicionUseCase {

    record Command(
            UUID alumnoId,
            TipoCondicion tipoCondicion,
            String descripcion,
            String medicacionNombre,
            String dosis,
            String frecuencia,
            String alergias,
            String medicoResponsable,
            String telefonoMedico,
            int nivelAcceso
    ) {
        public Command {
            if (alumnoId == null)
                throw new IllegalArgumentException("alumno_id es requerido");
            if (tipoCondicion == null)
                throw new IllegalArgumentException("tipo_condicion es requerido");
            if (descripcion == null || descripcion.isBlank())
                throw new IllegalArgumentException("descripcion es requerida");
        }
    }

    UUID registrar(Command cmd);
}

package mx.ades.modules.aulas.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para actualizar los datos de un aula existente
 * en el dominio de aulas, incluyendo equipamiento e infraestructura.
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarAulaUseCase {

    record Command(
            UUID aulaId,
            String nombreAula,
            UUID plantelId,
            String tipoAula,
            Integer capacidadAlumnos,
            Boolean isActive,
            // campos extendidos
            String claveAula,
            Short piso,
            String edificio,
            Short capacidadMaxima,
            Boolean tieneProyector,
            Boolean tienePizarraDigital,
            Boolean tienePizarron,
            Boolean tieneAireAcondicionado,
            Boolean tieneVentiladores,
            Boolean tieneInternet,
            Short numComputadoras,
            String estadoAula,
            String observaciones
    ) {
        public Command {
            if (aulaId == null)
                throw new IllegalArgumentException("El ID del aula es requerido");
            if (nombreAula == null || nombreAula.isBlank())
                throw new IllegalArgumentException("El nombre del aula es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}

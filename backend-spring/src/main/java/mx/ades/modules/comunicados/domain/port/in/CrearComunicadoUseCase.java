package mx.ades.modules.comunicados.domain.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear un comunicado institucional en el módulo comunicados.
 * <p>Soporta comunicados puntuales y recurrentes. El alcance se determina por plantelId,
 * nivelEducativoId o grupoId (null = todos).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CrearComunicadoUseCase {

    record Command(String titulo, String contenido, String tipoComunicado,
                   UUID plantelId, UUID nivelEducativoId, UUID grupoId,
                   Boolean requiereAcuse, LocalDateTime fechaVencimiento,
                   Boolean esRecurrente, String periodicidad, UUID creadoPorId) {
        public Command {
            if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("titulo del comunicado es requerido");
            if (contenido == null || contenido.isBlank()) throw new IllegalArgumentException("contenido del comunicado es requerido");
            if (creadoPorId == null) throw new IllegalArgumentException("creado_por_id es requerido");
        }
    }

    UUID crear(Command cmd);
}

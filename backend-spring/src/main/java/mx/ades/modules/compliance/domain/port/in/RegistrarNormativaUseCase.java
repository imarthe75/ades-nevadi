package mx.ades.modules.compliance.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar una normativa institucional en el módulo compliance.
 * <p>Requiere nivel de acceso 2 o inferior (Admin o superior). Indica a qué niveles educativos aplica.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarNormativaUseCase {

    record Command(String nombre, String tipo, String descripcion,
                   LocalDate fechaVigenciaInicio, LocalDate fechaVigenciaFin,
                   String urlDocumento, boolean aplicaPrimaria, boolean aplicaSecundaria,
                   boolean aplicaPreparatoria, String usuario, int nivelAcceso) {
        public Command {
            if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("nombre de la normativa es requerido");
            if (tipo == null || tipo.isBlank()) throw new IllegalArgumentException("tipo de normativa es requerido");
            if (nivelAcceso > 2) throw new IllegalArgumentException("Se requiere Admin o superior para registrar normativa");
        }
    }

    UUID registrar(Command cmd);
}

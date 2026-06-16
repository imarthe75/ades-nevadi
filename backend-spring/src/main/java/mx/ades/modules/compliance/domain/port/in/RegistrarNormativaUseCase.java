package mx.ades.modules.compliance.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

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

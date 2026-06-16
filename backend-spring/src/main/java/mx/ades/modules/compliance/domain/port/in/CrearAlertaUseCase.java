package mx.ades.modules.compliance.domain.port.in;

import mx.ades.modules.compliance.domain.model.SeveridadAlerta;

import java.util.UUID;

public interface CrearAlertaUseCase {

    record Command(String tipoAlerta, String descripcion, UUID alumnoId, UUID plantelId,
                   SeveridadAlerta severidad, boolean requiereAccion,
                   String usuario, int nivelAcceso) {
        public Command {
            if (tipoAlerta == null || tipoAlerta.isBlank()) throw new IllegalArgumentException("tipo_alerta es requerido");
            if (descripcion == null || descripcion.isBlank()) throw new IllegalArgumentException("descripcion es requerida");
            if (nivelAcceso > 3) throw new IllegalArgumentException("Se requiere Director o superior para crear alertas");
        }
    }

    UUID crear(Command cmd);
}

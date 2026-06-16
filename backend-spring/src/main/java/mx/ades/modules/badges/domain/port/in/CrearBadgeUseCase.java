package mx.ades.modules.badges.domain.port.in;

import mx.ades.modules.badges.domain.model.CriterioTipo;
import mx.ades.modules.badges.domain.model.TipoBadge;

import java.math.BigDecimal;
import java.util.UUID;

public interface CrearBadgeUseCase {

    record Command(String nombre, String descripcion, String icono, String color,
                   TipoBadge tipo, CriterioTipo criterioTipo, String criterioMetrica,
                   BigDecimal criterioValor, UUID plantelId) {
        public Command {
            if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("nombre del badge es requerido");
            if (tipo == null) throw new IllegalArgumentException("tipo_badge es requerido");
        }
    }

    UUID crear(Command cmd);
}

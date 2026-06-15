package mx.ades.modules.conducta.domain.event;

import mx.ades.modules.conducta.domain.model.TipoSancion;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio — se publica cuando se aplica una sanción disciplinaria.
 * El listener de infraestructura notifica a padres si la sanción lo requiere.
 */
public record SancionConductaEvent(
        UUID sancionId,
        UUID estudianteId,
        UUID reporteId,
        TipoSancion tipoSancion,
        boolean notificadoPadres,
        Instant ocurridoEn
) {
    public boolean debNotificarPadres() {
        return tipoSancion.requiereNotificacionPadres() && !notificadoPadres;
    }
}

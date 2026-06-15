package mx.ades.modules.calificaciones.domain.event;

import mx.ades.modules.calificaciones.domain.model.EstatusPromocion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio — se publica cuando una calificación de período es calculada o guardada.
 * Los listeners de infraestructura reaccionan: notifican a padres si el alumno reprobó.
 */
public record CalificacionCerradaEvent(
        UUID estudianteId,
        UUID materiaId,
        UUID grupoId,
        UUID periodoId,
        BigDecimal calificacionFinal,
        EstatusPromocion estatus,
        Instant ocurridoEn
) {
    public boolean esReprobado() {
        return estatus.esReprobado();
    }
}

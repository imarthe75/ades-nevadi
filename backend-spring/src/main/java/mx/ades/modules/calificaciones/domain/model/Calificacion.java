package mx.ades.modules.calificaciones.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo de dominio puro para el resultado de calificación de un período.
 * El cálculo real lo realiza calcular_calificacion_periodo() en PostgreSQL;
 * este record transporta y valida el resultado hacia la capa de aplicación.
 */
public record Calificacion(
        UUID id,
        UUID estudianteId,
        UUID grupoId,
        UUID materiaId,
        UUID periodoId,
        BigDecimal calificacionFinal,
        boolean esAcreditado,
        String observaciones
) {
    public Calificacion {
        Objects.requireNonNull(estudianteId,    "estudianteId requerido");
        Objects.requireNonNull(materiaId,       "materiaId requerido");
        Objects.requireNonNull(periodoId,       "periodoId requerido");
        Objects.requireNonNull(calificacionFinal, "calificacionFinal requerida");
    }

    public EstatusPromocion estatusPromocion() {
        return EstatusPromocion.from(esAcreditado);
    }
}

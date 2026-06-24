package mx.ades.modules.conducta.domain.port.in;

import mx.ades.modules.conducta.domain.model.TipoSancion;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando que encapsula los datos necesarios para aplicar una sanción disciplinaria
 * sobre un reporte de conducta en el módulo conducta.
 *
 * @author ADES
 * @since 2026
 */
public record AplicarSancionCommand(
        UUID reporteId,
        TipoSancion tipoSancion,
        String justificacion,
        UUID autorizadoPorId,
        LocalDate fechaSancion,
        LocalDate fechaFinSancion,
        boolean notificadoPadres,
        LocalDate fechaNotificacion,
        String medioNotificacion,
        String notasAdicionales,
        int nivelAccesoAplicador
) {}

package mx.ades.modules.expediente.domain.event;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando se registra la baja de un alumno.
 * <p>Indica si el alumno fue desactivado en el sistema como consecuencia de la baja.</p>
 *
 * @author ADES
 * @since 2026
 */
public record BajaRegistradaEvent(
        UUID bajaId,
        UUID estudianteId,
        TipoBaja tipo,
        LocalDate fechaEfectiva,
        boolean estudianteDesactivado,
        String registradoPor,
        Instant ocurridoEn) {}

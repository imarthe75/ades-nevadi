package mx.ades.modules.evaluaciones.domain.event;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando un aula es asignada a un slot horario específico
 * sin conflicto de reserva.
 *
 * @author ADES
 * @since 2026
 */
public record AulaAsignadaEvent(
        UUID asignacionId,
        UUID aulaId,
        LocalDate fecha,
        SlotHorario slot,
        String asignadoPor,
        Instant ocurridoEn) {}

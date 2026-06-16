package mx.ades.modules.evaluaciones.domain.event;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AulaAsignadaEvent(
        UUID asignacionId,
        UUID aulaId,
        LocalDate fecha,
        SlotHorario slot,
        String asignadoPor,
        Instant ocurridoEn) {}

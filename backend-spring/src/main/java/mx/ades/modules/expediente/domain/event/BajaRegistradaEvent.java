package mx.ades.modules.expediente.domain.event;

import mx.ades.modules.expediente.domain.model.TipoBaja;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BajaRegistradaEvent(
        UUID bajaId,
        UUID estudianteId,
        TipoBaja tipo,
        LocalDate fechaEfectiva,
        boolean estudianteDesactivado,
        String registradoPor,
        Instant ocurridoEn) {}

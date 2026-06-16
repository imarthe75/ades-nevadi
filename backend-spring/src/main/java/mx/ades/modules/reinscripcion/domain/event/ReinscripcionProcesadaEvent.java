package mx.ades.modules.reinscripcion.domain.event;

import mx.ades.modules.reinscripcion.domain.model.AccionReinscripcion;

import java.time.Instant;
import java.util.UUID;

public record ReinscripcionProcesadaEvent(
        UUID registroId,
        AccionReinscripcion accion,
        String estado,
        UUID procesadoPor,
        Instant ocurridoEn) {}

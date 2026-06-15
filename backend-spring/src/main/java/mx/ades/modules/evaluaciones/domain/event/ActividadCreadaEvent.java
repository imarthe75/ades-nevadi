package mx.ades.modules.evaluaciones.domain.event;

import mx.ades.modules.evaluaciones.domain.model.TipoItem;

import java.time.Instant;
import java.util.UUID;

public record ActividadCreadaEvent(
        UUID tareaId,
        UUID grupoId,
        UUID materiaId,
        TipoItem tipoItem,
        int slotsCreados,
        Instant ocurridoEn) {
}

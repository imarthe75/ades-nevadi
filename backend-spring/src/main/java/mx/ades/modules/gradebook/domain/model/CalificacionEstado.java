package mx.ades.modules.gradebook.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CalificacionEstado(UUID id, BigDecimal calificacionCalculada, boolean cerrada) {

    public boolean permiteAjuste(boolean esAdmin) {
        return !cerrada || esAdmin;
    }
}

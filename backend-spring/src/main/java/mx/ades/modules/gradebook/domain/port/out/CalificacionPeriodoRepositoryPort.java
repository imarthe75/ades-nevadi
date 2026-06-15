package mx.ades.modules.gradebook.domain.port.out;

import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.model.CalificacionEstado;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CalificacionPeriodoRepositoryPort {

    Optional<CalificacionEstado> findEstado(UUID calPeriodoId);

    void aplicarAjuste(UUID calPeriodoId, AjusteManual ajuste, BigDecimal calFinal, String username);

    /** @return true si se cerró (false = ya estaba cerrada o no existe) */
    boolean cerrar(UUID calPeriodoId, String username);
}

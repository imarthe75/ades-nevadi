package mx.ades.modules.admin.domain.port.out;

import java.util.UUID;

public interface PromocionRepositoryPort {
    Object ejecutarPromocion(UUID cicloId, UUID plantelId, String usuario);
}

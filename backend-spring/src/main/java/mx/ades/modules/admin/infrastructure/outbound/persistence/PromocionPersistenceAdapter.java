package mx.ades.modules.admin.infrastructure.outbound.persistence;

import mx.ades.modules.admin.domain.port.out.PromocionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PromocionPersistenceAdapter implements PromocionRepositoryPort {

    private final JdbcTemplate jdbc;

    public PromocionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object ejecutarPromocion(UUID cicloId, UUID plantelId, String usuario) {
        if (plantelId != null) {
            return jdbc.queryForObject(
                "SELECT fn_evaluar_estatus_promocion(?::uuid, ?::uuid, ?)",
                Object.class, cicloId.toString(), plantelId.toString(), usuario);
        }
        return jdbc.queryForObject(
            "SELECT fn_evaluar_estatus_promocion(?::uuid, NULL::uuid, ?)",
            Object.class, cicloId.toString(), usuario);
    }
}

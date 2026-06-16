package mx.ades.modules.reinscripcion.infrastructure.outbound.persistence;

import mx.ades.modules.reinscripcion.domain.port.out.ReinscripcionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ReinscripcionPersistenceAdapter implements ReinscripcionRepositoryPort {

    private final JdbcTemplate jdbc;

    public ReinscripcionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void procesarAccion(UUID registroId, String estado, String razonRechazo, UUID procesadoPor) {
        int count = jdbc.update(
            "UPDATE ades_reinscripcion_ciclo " +
            "SET estado = ?, aprobado_por = ?, razon_rechazo = ?, " +
            "fecha_aprobacion = CASE WHEN ? = 'APROBADO' THEN now() ELSE NULL END, " +
            "fecha_modificacion = now(), row_version = row_version + 1 " +
            "WHERE id = ? AND is_active = TRUE",
            estado, procesadoPor, razonRechazo, estado, registroId);

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de reinscripción no encontrado");
        }
    }
}

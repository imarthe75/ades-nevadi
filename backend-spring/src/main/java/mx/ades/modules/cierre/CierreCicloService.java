package mx.ades.modules.cierre;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CierreCicloService {

    private final JdbcTemplate jdbc;

    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public String cerrarCiclo(UUID cicloOrigenId, UUID cicloDestinoId, String usuario) {
        try {
            return jdbc.queryForObject(
                    "SELECT cerrar_ciclo_y_promover(?, ?, ?)::text",
                    String.class, cicloOrigenId, cicloDestinoId, usuario);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al cerrar ciclo y promover alumnos: " + e.getMessage(), e);
        }
    }
}

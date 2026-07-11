package mx.ades.modules.horarios;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Desactiva un horario individual en su propia transacción. Extraído de
 * {@link HorarioAscService#importarXml} para preservar el diseño deliberado de
 * "cada renglón se confirma de forma independiente" (un horario inválido no
 * debe revertir los demás) — que requiere un bean Spring separado, porque
 * auto-invocación (`this.metodo()`) dentro de la misma clase no pasa por el
 * proxy de {@code @Transactional}.
 */
@Service
public class HorarioDesactivadorService {

    private final JdbcTemplate jdbc;

    public HorarioDesactivadorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void desactivar(UUID horarioId, String usuario) {
        jdbc.update("UPDATE ades_horarios SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, horarioId);
    }
}

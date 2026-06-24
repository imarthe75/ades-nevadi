package mx.ades.modules.cierre.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de consulta (lado lectura CQRS) para el módulo cierre.
 *
 * <p>Consulta los indicadores de cierre de ciclo desde la vista
 * {@code v_indicadores_cierre_ciclo}, que resume el estado de calificaciones,
 * asistencias y promociones del ciclo antes del cierre definitivo.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class CierreQueryService {

    private final JdbcTemplate jdbc;

    public Optional<Map<String, Object>> indicadores(UUID cicloId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM v_indicadores_cierre_ciclo WHERE ciclo_escolar_id = ?", cicloId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}

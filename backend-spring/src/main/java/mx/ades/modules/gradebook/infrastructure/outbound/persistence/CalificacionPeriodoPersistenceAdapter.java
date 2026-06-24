package mx.ades.modules.gradebook.infrastructure.outbound.persistence;

import mx.ades.modules.gradebook.domain.model.AjusteManual;
import mx.ades.modules.gradebook.domain.model.CalificacionEstado;
import mx.ades.modules.gradebook.domain.port.out.CalificacionPeriodoRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC que implementa {@link CalificacionPeriodoRepositoryPort}.
 * Gestiona la lectura del estado, la aplicación de ajustes manuales y el cierre
 * de calificaciones de periodo en {@code ades_calificaciones_periodo}.
 *
 * @author ADES
 * @since 2026
 */
@Component
public class CalificacionPeriodoPersistenceAdapter implements CalificacionPeriodoRepositoryPort {

    private final JdbcTemplate jdbc;

    public CalificacionPeriodoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CalificacionEstado> findEstado(UUID calPeriodoId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, calificacion_calculada, cerrada
            FROM ades_calificaciones_periodo
            WHERE id = ? AND is_active = TRUE
            """, calPeriodoId);

        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = rows.get(0);
        BigDecimal calCal = r.get("calificacion_calculada") != null
                ? new BigDecimal(r.get("calificacion_calculada").toString()) : null;
        return Optional.of(new CalificacionEstado(
                (UUID) r.get("id"), calCal, Boolean.TRUE.equals(r.get("cerrada"))));
    }

    @Override
    @Transactional
    public void aplicarAjuste(UUID calPeriodoId, AjusteManual ajuste, BigDecimal calFinal, String username) {
        jdbc.update("""
            UPDATE ades_calificaciones_periodo
            SET ajuste_manual          = ?,
                justificacion_ajuste   = ?,
                calificacion_final     = ?,
                fecha_modificacion     = CURRENT_TIMESTAMP,
                row_version            = row_version + 1,
                usuario_modificacion   = ?
            WHERE id = ?
            """,
                ajuste.valor(), ajuste.justificacion(), calFinal, username, calPeriodoId);
    }

    @Override
    @Transactional
    public boolean cerrar(UUID calPeriodoId, String username) {
        int rows = jdbc.update("""
            UPDATE ades_calificaciones_periodo
            SET cerrada              = TRUE,
                fecha_cierre         = CURRENT_TIMESTAMP,
                fecha_modificacion   = CURRENT_TIMESTAMP,
                usuario_modificacion = ?
            WHERE id = ? AND cerrada = FALSE AND is_active = TRUE
            """, username, calPeriodoId);
        return rows > 0;
    }
}

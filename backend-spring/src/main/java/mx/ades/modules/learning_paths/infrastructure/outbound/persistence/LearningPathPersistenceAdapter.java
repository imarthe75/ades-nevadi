package mx.ades.modules.learning_paths.infrastructure.outbound.persistence;

import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class LearningPathPersistenceAdapter implements LearningPathRepositoryPort {

    private final JdbcTemplate jdbc;

    public LearningPathPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertProgreso(UUID asignacionId, UUID recursoId, Integer tiempoMin, Double calificacion) {
        jdbc.update("""
                INSERT INTO ades_lp_progreso
                    (asignacion_id, recurso_id, completado, tiempo_min, calificacion, fccompletado)
                VALUES (?, ?, TRUE, ?, ?, NOW())
                ON CONFLICT (asignacion_id, recurso_id) DO UPDATE
                SET completado = TRUE,
                    tiempo_min = COALESCE(EXCLUDED.tiempo_min, ades_lp_progreso.tiempo_min),
                    calificacion = COALESCE(EXCLUDED.calificacion, ades_lp_progreso.calificacion),
                    fccompletado = NOW()
                """,
                asignacionId, recursoId, tiempoMin,
                calificacion != null ? BigDecimal.valueOf(calificacion) : null);
    }

    @Override
    public ProgresoStats calcularProgreso(UUID asignacionId) {
        Integer completados = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_progreso WHERE asignacion_id = ? AND completado = TRUE
                """, Integer.class, asignacionId);

        Integer obligatorios = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_recursos r
                WHERE r.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?)
                  AND r.is_active = TRUE AND r.obligatorio = TRUE
                """, Integer.class, asignacionId);

        Integer totalRecursos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ades_lp_recursos r
                WHERE r.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = ?)
                  AND r.is_active = TRUE
                """, Integer.class, asignacionId);

        int comp = completados != null ? completados : 0;
        int oblig = obligatorios != null ? obligatorios : 0;
        int total = totalRecursos != null ? totalRecursos : 0;
        double pct = total > 0 ? Math.round(comp * 1000.0 / total) / 10.0 : 0.0;

        return new ProgresoStats(comp, oblig, pct);
    }

    @Override
    public void actualizarEstatus(UUID asignacionId, String estatus, double pctCompletado) {
        jdbc.update("""
                UPDATE ades_lp_asignaciones
                SET pct_completado = ?,
                    estatus = ?,
                    fccompletado = CASE WHEN ? = 'COMPLETADO' THEN NOW() ELSE fccompletado END,
                    fecha_modificacion = NOW()
                WHERE id = ?
                """, BigDecimal.valueOf(pctCompletado), estatus, estatus, asignacionId);
    }
}

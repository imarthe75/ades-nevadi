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

    public Map<String, Object> validarCalificacionesCompletas(UUID cicloId) {
        Integer gruposSinCalificar = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT g.id) FROM ades_grupos g " +
            "WHERE g.ciclo_escolar_id = ? AND g.is_active = TRUE " +
            "AND NOT EXISTS (SELECT 1 FROM ades_calificaciones c " +
            "WHERE c.grupo_id = g.id AND c.ciclo_escolar_id = g.ciclo_escolar_id)",
            Integer.class, cicloId);

        Integer materiasSinCalificar = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT m.id) FROM ades_grupos g " +
            "JOIN ades_materias m ON m.id IN (SELECT materia_id FROM ades_grupo_materias WHERE grupo_id = g.id) " +
            "WHERE g.ciclo_escolar_id = ? AND g.is_active = TRUE " +
            "AND NOT EXISTS (SELECT 1 FROM ades_calificaciones c " +
            "WHERE c.materia_id = m.id AND c.grupo_id = g.id)",
            Integer.class, cicloId);

        Integer alumnosSinCalificar = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT a.id) FROM ades_alumnos a " +
            "WHERE a.ciclo_escolar_id = ? AND a.is_active = TRUE " +
            "AND NOT EXISTS (SELECT 1 FROM ades_calificaciones c " +
            "WHERE c.estudiante_id = a.id AND c.ciclo_escolar_id = a.ciclo_escolar_id)",
            Integer.class, cicloId);

        boolean esValido = (gruposSinCalificar == null || gruposSinCalificar == 0) &&
                          (materiasSinCalificar == null || materiasSinCalificar == 0) &&
                          (alumnosSinCalificar == null || alumnosSinCalificar == 0);

        return Map.of(
            "valido", esValido,
            "grupos_sin_calificar", gruposSinCalificar != null ? gruposSinCalificar : 0,
            "materias_sin_calificar", materiasSinCalificar != null ? materiasSinCalificar : 0,
            "alumnos_sin_calificar", alumnosSinCalificar != null ? alumnosSinCalificar : 0
        );
    }
}

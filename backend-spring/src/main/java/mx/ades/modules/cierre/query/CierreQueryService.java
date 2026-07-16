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
        return indicadores(cicloId, null);
    }

    public Optional<Map<String, Object>> indicadores(UUID cicloId, UUID plantelId) {
        if (plantelId == null) {
            // Agrega todo el instituto para el ciclo dado
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM v_indicadores_cierre_ciclo WHERE ciclo_escolar_id = ?", cicloId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
        // Filtra indicadores por plantel específico usando la cadena inscripciones→grupos→grados
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                c.id                                          AS ciclo_escolar_id,
                c.nombre_ciclo,
                c.nivel_educativo_id,
                n.nombre_nivel,
                c.fecha_inicio,
                c.fecha_fin,
                c.estado,
                COUNT(DISTINCT CASE WHEN i.is_active THEN i.estudiante_id END) AS matricula_total,
                COUNT(DISTINCT CASE WHEN ad.is_active THEN ad.profesor_id  END) AS total_docentes,
                COALESCE(ROUND(AVG(CASE WHEN cp.is_active THEN cp.calificacion_final END), 2), 0.0)
                                                              AS promedio_general,
                COALESCE(ROUND(
                    (COUNT(DISTINCT CASE WHEN cp.is_active AND cp.es_acreditado THEN cp.id END)::numeric * 100.0)
                    / NULLIF(COUNT(DISTINCT CASE WHEN cp.is_active THEN cp.id END), 0)
                , 2), 100.00)                                 AS tasa_aprobacion,
                COUNT(DISTINCT CASE WHEN b.is_active THEN b.id END) AS total_bajas,
                COUNT(DISTINCT CASE WHEN i.is_active AND e.is_active THEN i.estudiante_id END) AS total_alumnos_activos
            FROM ades_ciclos_escolares c
            JOIN ades_niveles_educativos  n  ON n.id  = c.nivel_educativo_id
            LEFT JOIN ades_grupos         g  ON g.ciclo_escolar_id = c.id
            LEFT JOIN ades_grados         gr ON gr.id = g.grado_id
            LEFT JOIN ades_inscripciones  i  ON i.grupo_id = g.id AND i.ciclo_escolar_id = c.id
            LEFT JOIN ades_estudiantes    e  ON e.id = i.estudiante_id
            LEFT JOIN ades_asignaciones_docentes ad ON ad.grupo_id = g.id AND ad.is_active = true
            LEFT JOIN ades_periodos_evaluacion   pe ON pe.ciclo_escolar_id = c.id
            LEFT JOIN ades_calificaciones_periodo cp ON cp.periodo_evaluacion_id = pe.id AND cp.estudiante_id = i.estudiante_id
            LEFT JOIN ades_bajas          b  ON b.is_active = true AND b.estudiante_id = i.estudiante_id
            WHERE c.id = ?
              AND gr.plantel_id = ?
            GROUP BY c.id, c.nombre_ciclo, c.nivel_educativo_id, n.nombre_nivel, c.fecha_inicio, c.fecha_fin, c.estado
            """, cicloId, plantelId);
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

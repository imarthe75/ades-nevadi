package mx.ades.modules.planeacion.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecturas de planeación — SQL directo es correcto aquí (CQRS read side).
 * La lógica de estado PENDIENTE/PLANEADO/IMPARTIDO refleja EstadoTema del dominio.
 */
@Service
@RequiredArgsConstructor
public class PlaneacionQueryService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTemasConEstado(UUID grupoId, UUID materiaId) {
        String query = """
            SELECT
                t.id          AS tema_id,
                t.nombre_tema,
                t.descripcion AS descripcion_tema,
                t.orden,
                t.periodo_sugerido,
                m.id          AS materia_id,
                m.nombre_materia,
                pc.id         AS planeacion_id,
                pc.fecha_planeada,
                pc.descripcion_actividades,
                av.id         AS avance_id,
                av.fecha_ejecucion,
                av.es_completado,
                av.comentarios_profesor,
                CASE
                    WHEN av.es_completado = TRUE THEN 'IMPARTIDO'
                    WHEN pc.id IS NOT NULL       THEN 'PLANEADO'
                    ELSE                              'PENDIENTE'
                END           AS estado
            FROM ades_grupos g
            JOIN ades_grados gr         ON gr.id = g.grado_id
            JOIN ades_materias_plan mp  ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id
                AND mp.is_active = TRUE
            JOIN ades_materias m        ON m.id = mp.materia_id
            JOIN ades_temas t           ON t.materia_id = m.id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id)
                AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.id = ?::uuid AND t.is_active = TRUE
            """;

        List<Object> params = new ArrayList<>();
        params.add(grupoId.toString());

        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }
        query += " ORDER BY m.nombre_materia, t.orden";

        return jdbc.queryForList(query, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCoberturaGrupo(UUID grupoId) {
        return jdbc.queryForList("""
            SELECT
                m.id          AS materia_id,
                m.nombre_materia,
                COUNT(t.id)                                             AS total_temas,
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)     AS temas_impartidos,
                COUNT(pc.id) FILTER (WHERE av.id IS NULL)               AS temas_planeados,
                ROUND(
                    COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                    / NULLIF(COUNT(t.id), 0) * 100, 1
                ) AS pct_cobertura
            FROM ades_grupos g
            JOIN ades_grados gr        ON gr.id = g.grado_id
            JOIN ades_materias_plan mp ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_materias m       ON m.id = mp.materia_id
            JOIN ades_temas t          ON t.materia_id = m.id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id) AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.id = ?
            GROUP BY m.id, m.nombre_materia
            ORDER BY m.nombre_materia
            """, grupoId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getListarPlaneacion(UUID grupoId, UUID materiaId) {
        List<Object> params = new ArrayList<>();
        String query = """
            SELECT pc.id, pc.grupo_id, pc.tema_id, pc.fecha_planeada,
                   pc.descripcion_actividades, pc.recursos_didacticos,
                   t.nombre_tema, m.nombre_materia
            FROM ades_planeacion_clases pc
            JOIN ades_temas t    ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            WHERE pc.grupo_id = ?::uuid AND pc.is_active = TRUE
            """;
        params.add(grupoId.toString());

        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }
        query += " ORDER BY pc.fecha_planeada";
        return jdbc.queryForList(query, params.toArray());
    }

    /** OA-012: temas planeados que quedaron pendientes de reprogramar por clase suspendida. */
    public List<Map<String, Object>> getPendientesReprogramar(UUID grupoId) {
        return jdbc.queryForList("""
            SELECT pc.id, pc.grupo_id, pc.tema_id, pc.fecha_planeada,
                   t.nombre_tema, m.nombre_materia
            FROM ades_planeacion_clases pc
            JOIN ades_temas t    ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            WHERE pc.grupo_id = ?::uuid AND pc.is_active = TRUE AND pc.pendiente_reprogramar = TRUE
            ORDER BY pc.fecha_planeada
            """, grupoId.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAlertasRezago(UUID cicloId, Double umbralPct) {
        List<Map<String, Object>> grupos = jdbc.queryForList("""
            SELECT g.id AS grupo_id, g.nombre_grupo,
                   COUNT(t.id) AS total_temas,
                   COUNT(av.id) FILTER (WHERE av.es_completado = TRUE) AS impartidos,
                   ROUND(COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                       / NULLIF(COUNT(t.id),0) * 100, 1) AS pct_cobertura
            FROM ades_grupos g
            JOIN ades_grados gr        ON gr.id = g.grado_id
            JOIN ades_materias_plan mp ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_temas t          ON t.materia_id = mp.materia_id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id) AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.ciclo_escolar_id = ?
            GROUP BY g.id, g.nombre_grupo
            HAVING ROUND(COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                / NULLIF(COUNT(t.id),0) * 100, 1) < ?
            ORDER BY pct_cobertura
            """, cicloId, umbralPct);
        return Map.of("grupos_en_rezago", grupos, "umbral_pct", umbralPct);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlanSemana(UUID grupoId, LocalDate fechaInicio) {
        LocalDate fechaFin = fechaInicio.plusDays(6);
        List<Map<String, Object>> temas = jdbc.queryForList("""
            SELECT pc.id, pc.fecha_planeada, pc.descripcion_actividades,
                   t.nombre_tema, m.nombre_materia,
                   (av.es_completado IS NOT NULL AND av.es_completado) AS completado
            FROM ades_planeacion_clases pc
            JOIN ades_temas t    ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            LEFT JOIN ades_avance_planificacion av ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE pc.grupo_id = ? AND pc.is_active = TRUE
              AND pc.fecha_planeada BETWEEN ? AND ?
            ORDER BY pc.fecha_planeada, m.nombre_materia
            """, grupoId, fechaInicio, fechaFin);
        return Map.of("semana_inicio", fechaInicio, "semana_fin", fechaFin, "temas", temas);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInsightsGrupo(UUID grupoId) {
        Map<String, Object> resumen = jdbc.queryForMap("""
            SELECT
                COUNT(t.id) AS total_temas,
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE) AS impartidos,
                COUNT(pc.id) FILTER (WHERE av.id IS NULL)           AS planeados,
                COUNT(t.id) - COUNT(pc.id)                          AS pendientes
            FROM ades_grupos g
            JOIN ades_grados gr        ON gr.id = g.grado_id
            JOIN ades_materias_plan mp ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_temas t          ON t.materia_id = mp.materia_id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id) AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.id = ?
            """, grupoId);
        return resumen;
    }
}

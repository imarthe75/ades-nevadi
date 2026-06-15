package mx.ades.modules.planeacion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlaneacionService {

    private final PlaneacionRepository repository;
    private final JdbcTemplate jdbcTemplate;

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

        return jdbcTemplate.queryForList(query, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCoberturaGrupo(UUID grupoId) {
        String query = """
            SELECT
                m.id          AS materia_id,
                m.nombre_materia,
                COUNT(t.id)                                               AS total_temas,
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)       AS temas_impartidos,
                COUNT(pc.id) FILTER (WHERE av.id IS NULL)                 AS temas_planeados,
                ROUND(
                    COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                    / NULLIF(COUNT(t.id), 0) * 100, 1
                )                                                         AS pct_cobertura
            FROM ades_grupos g
            JOIN ades_grados gr        ON gr.id = g.grado_id
            JOIN ades_materias_plan mp ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id
                AND mp.is_active = TRUE
            JOIN ades_materias m       ON m.id = mp.materia_id
            JOIN ades_temas t          ON t.materia_id = m.id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id)
                AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.id = ?::uuid
            GROUP BY m.id, m.nombre_materia
            ORDER BY m.nombre_materia
        """;
        return jdbcTemplate.queryForList(query, grupoId.toString());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getListarPlaneacion(UUID grupoId, UUID materiaId) {
        String query = """
            SELECT
                pc.id, pc.fecha_planeada,
                pc.descripcion_actividades, pc.recursos_didacticos,
                t.id AS tema_id, t.nombre_tema, t.orden,
                m.nombre_materia,
                av.es_completado, av.fecha_ejecucion, av.comentarios_profesor
            FROM ades_planeacion_clases pc
            JOIN ades_temas t    ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE pc.grupo_id = ?::uuid AND pc.is_active = TRUE
        """;

        List<Object> params = new ArrayList<>();
        params.add(grupoId.toString());

        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }

        query += " ORDER BY pc.fecha_planeada, m.nombre_materia, t.orden";

        return jdbcTemplate.queryForList(query, params.toArray());
    }

    @Transactional
    public Map<String, Object> crearPlaneacion(UUID grupoId, UUID temaId, LocalDate fecha, String descripcion, String recursos) {
        // We use native query because of ON CONFLICT DO NOTHING
        String sql = """
            INSERT INTO ades_planeacion_clases
                (grupo_id, tema_id, fecha_planeada, descripcion_actividades, recursos_didacticos)
            VALUES
                (?::uuid, ?::uuid, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING id, tema_id, fecha_planeada
        """;
        List<Map<String, Object>> resultList = jdbcTemplate.queryForList(sql, 
                grupoId.toString(), 
                temaId.toString(), 
                fecha, 
                descripcion, 
                recursos);
        
        if (resultList.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("ok", true);
            fallback.put("mensaje", "Ya existía una planeación para este tema/grupo");
            return fallback;
        }
        return resultList.getFirst();
    }

    @Transactional
    public Map<String, Object> completarTema(UUID planeacionId, UUID claseId, LocalDate fecha, String comentarios) {
        String sql = """
            INSERT INTO ades_avance_planificacion
                (planeacion_clase_id, clase_id, fecha_ejecucion, es_completado, comentarios_profesor)
            VALUES
                (?::uuid, ?::uuid, ?, TRUE, ?)
            ON CONFLICT DO NOTHING
            RETURNING id, es_completado, fecha_ejecucion
        """;
        List<Map<String, Object>> resultList = jdbcTemplate.queryForList(sql,
                planeacionId.toString(),
                claseId != null ? claseId.toString() : null,
                fecha,
                comentarios);

        if (resultList.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("ok", true);
            fallback.put("mensaje", "Ya registrado");
            return fallback;
        }
        return resultList.getFirst();
    }

    @Transactional
    public void eliminarPlaneacion(UUID planeacionId) {
        jdbcTemplate.update("UPDATE ades_planeacion_clases SET is_active = FALSE WHERE id = ?::uuid", planeacionId.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAlertasRezago(UUID cicloId, Double umbralPct) {
        String query = """
            SELECT
                g.id                   AS grupo_id,
                g.nombre_grupo,
                m.id                   AS materia_id,
                m.nombre_materia,
                COUNT(t.id)            AS total_temas,
                COUNT(pc.id) FILTER (WHERE pc.estado = 'IMPARTIDO') AS temas_impartidos,
                ROUND(
                    COUNT(pc.id) FILTER (WHERE pc.estado = 'IMPARTIDO') * 100.0
                    / NULLIF(COUNT(t.id), 0), 1
                ) AS pct_cubierto
            FROM ades_grupos g
            JOIN ades_grados gr          ON gr.id = g.grado_id
            JOIN ades_materias_grado mg  ON mg.grado_id = gr.id AND mg.is_active = TRUE
            JOIN ades_materias m         ON m.id = mg.materia_id
            LEFT JOIN ades_temas t       ON t.materia_id = m.id AND t.is_active = TRUE
            LEFT JOIN (
                SELECT sub_pc.grupo_id, sub_pc.materia_id, sub_pc.is_active,
                       CASE
                           WHEN sub_av.es_completado = TRUE THEN 'IMPARTIDO'
                           ELSE 'PLANEADO'
                       END AS estado
                FROM ades_planeacion_clases sub_pc
                LEFT JOIN ades_avance_planificacion sub_av ON sub_av.planeacion_clase_id = sub_pc.id AND sub_av.is_active = TRUE
                JOIN ades_temas sub_t ON sub_t.id = sub_pc.tema_id
            ) pc ON pc.grupo_id = g.id AND pc.materia_id = m.id AND pc.is_active = TRUE
            WHERE g.ciclo_escolar_id = ?::uuid AND g.is_active = TRUE
            GROUP BY g.id, g.nombre_grupo, m.id, m.nombre_materia
            HAVING COUNT(t.id) > 0
               AND (COUNT(pc.is_active) FILTER (WHERE pc.estado = 'IMPARTIDO') * 100.0
                    / NULLIF(COUNT(t.id), 0)) < ?
            ORDER BY pct_cubierto ASC, g.nombre_grupo, m.nombre_materia
        """;
        List<Map<String, Object>> alertas = jdbcTemplate.queryForList(query, cicloId.toString(), umbralPct);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ciclo_id", cicloId);
        response.put("umbral_pct", umbralPct);
        response.put("total_alertas", alertas.size());
        response.put("alertas", alertas);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlanSemana(UUID grupoId, LocalDate fechaInicio) {
        LocalDate fechaFin = fechaInicio.plusDays(6);

        String queryPlaneados = """
            SELECT
                pc.id               AS planeacion_id,
                pc.fecha_planeada,
                t.id                AS tema_id,
                t.nombre_tema,
                t.orden,
                m.id                AS materia_id,
                m.nombre_materia,
                av.es_completado,
                av.fecha_ejecucion,
                CASE WHEN av.es_completado = TRUE THEN 'IMPARTIDO'
                     WHEN pc.id IS NOT NULL        THEN 'PLANEADO'
                     ELSE                               'PENDIENTE'
                END AS estado
            FROM ades_planeacion_clases pc
            JOIN ades_temas t    ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            LEFT JOIN ades_avance_planificacion av
                   ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE pc.grupo_id = ?::uuid
              AND pc.is_active = TRUE
              AND pc.fecha_planeada BETWEEN ? AND ?
            ORDER BY pc.fecha_planeada, m.nombre_materia, t.orden
        """;
        List<Map<String, Object>> planeados = jdbcTemplate.queryForList(queryPlaneados, grupoId.toString(), fechaInicio, fechaFin);

        String querySugerencias = """
            WITH impartidos AS (
                SELECT t.id AS tema_id, t.materia_id
                FROM ades_planeacion_clases pc
                JOIN ades_temas t ON t.id = pc.tema_id
                JOIN ades_avance_planificacion av
                     ON av.planeacion_clase_id = pc.id AND av.es_completado = TRUE AND av.is_active = TRUE
                WHERE pc.grupo_id = ?::uuid AND pc.is_active = TRUE
            ),
            planeados_ya AS (
                SELECT tema_id FROM ades_planeacion_clases
                WHERE grupo_id = ?::uuid AND is_active = TRUE
            )
            SELECT
                t.id        AS tema_id,
                t.nombre_tema,
                t.orden,
                m.id        AS materia_id,
                m.nombre_materia,
                ROW_NUMBER() OVER (PARTITION BY m.id ORDER BY t.orden) AS rn
            FROM ades_grupos g
            JOIN ades_grados gr         ON gr.id = g.grado_id
            JOIN ades_materias_plan mp  ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_materias m        ON m.id = mp.materia_id
            JOIN ades_temas t           ON t.materia_id = m.id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id) AND t.is_active = TRUE
            WHERE g.id = ?::uuid
              AND t.id NOT IN (SELECT tema_id FROM impartidos)
              AND t.id NOT IN (SELECT tema_id FROM planeados_ya)
        """;
        List<Map<String, Object>> rawSugerencias = jdbcTemplate.queryForList(querySugerencias, grupoId.toString(), grupoId.toString(), grupoId.toString());
        List<Map<String, Object>> sugerencias = new ArrayList<>();
        for (Map<String, Object> sug : rawSugerencias) {
            Number rn = (Number) sug.get("rn");
            if (rn != null && rn.intValue() <= 2) {
                sugerencias.add(sug);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("grupo_id", grupoId);
        
        Map<String, Object> semana = new HashMap<>();
        semana.put("inicio", fechaInicio);
        semana.put("fin", fechaFin);
        response.put("semana", semana);
        
        response.put("planeados", planeados);
        response.put("sugerencias", sugerencias);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInsightsGrupo(UUID grupoId) {
        // 1. Cobertura del temario por materia
        String queryCobertura = """
            SELECT
                m.id           AS materia_id,
                m.nombre_materia,
                m.tipo_materia,
                COUNT(t.id)                                                     AS total_temas,
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)             AS temas_impartidos,
                COUNT(pc.id) FILTER (WHERE av.id IS NULL OR av.es_completado = FALSE) AS temas_planeados,
                COUNT(t.id) FILTER (WHERE pc.id IS NULL)                        AS temas_pendientes,
                COALESCE(ROUND(
                    COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)::numeric
                    / NULLIF(COUNT(t.id), 0) * 100, 1
                ), 0) AS pct_cobertura
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
            WHERE g.id = ?::uuid
            GROUP BY m.id, m.nombre_materia, m.tipo_materia
            ORDER BY pct_cobertura ASC, m.nombre_materia
        """;
        List<Map<String, Object>> cobertura = jdbcTemplate.queryForList(queryCobertura, grupoId.toString());

        // 2. Tareas vinculadas vs sin tema
        String queryTareas = """
            SELECT
                COUNT(*)                               AS total_tareas,
                COUNT(*) FILTER (WHERE tema_id IS NOT NULL) AS tareas_con_tema,
                COUNT(*) FILTER (WHERE tema_id IS NULL)     AS tareas_sin_tema,
                COALESCE(ROUND(
                    COUNT(*) FILTER (WHERE tema_id IS NOT NULL)::numeric
                    / NULLIF(COUNT(*), 0) * 100, 1
                ), 0) AS pct_vinculadas
            FROM ades_tareas
            WHERE grupo_id = ?::uuid AND is_active = TRUE
        """;
        List<Map<String, Object>> tareasList = jdbcTemplate.queryForList(queryTareas, grupoId.toString());
        Map<String, Object> tareasStats = tareasList.isEmpty() ? new HashMap<>() : tareasList.getFirst();

        // 3. Distribucion de calificaciones (promedio por materia)
        String queryCalificaciones = """
            SELECT
                m.nombre_materia,
                ROUND(AVG(cp.calificacion_final)::numeric, 2) AS promedio,
                COUNT(DISTINCT cp.alumno_id)                  AS alumnos_evaluados,
                COUNT(DISTINCT cp.alumno_id) FILTER (WHERE cp.calificacion_final < 6) AS en_riesgo
            FROM ades_calificaciones_periodo cp
            JOIN ades_materias m ON m.id = cp.materia_id
            WHERE cp.grupo_id = ?::uuid AND cp.is_active = TRUE
            GROUP BY m.id, m.nombre_materia
            ORDER BY promedio ASC
        """;
        List<Map<String, Object>> calificaciones = jdbcTemplate.queryForList(queryCalificaciones, grupoId.toString());

        // Resumen global
        long totalTemas = 0;
        long totalImpartidos = 0;
        for (Map<String, Object> r : cobertura) {
            Number tt = (Number) r.get("total_temas");
            Number ti = (Number) r.get("temas_impartidos");
            if (tt != null) totalTemas += tt.longValue();
            if (ti != null) totalImpartidos += ti.longValue();
        }
        double pctGlobal = totalTemas > 0 ? Math.round((double) totalImpartidos * 1000.0 / totalTemas) / 10.0 : 0.0;

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("total_temas", totalTemas);
        resumen.put("temas_impartidos", totalImpartidos);
        resumen.put("pct_cobertura", pctGlobal);
        resumen.put("estado", pctGlobal < 40 ? "CRITICO" : (pctGlobal < 70 ? "ALERTA" : "OK"));

        Map<String, Object> response = new HashMap<>();
        response.put("grupo_id", grupoId);
        response.put("resumen", resumen);
        response.put("cobertura_por_materia", cobertura);
        response.put("tareas", tareasStats);
        response.put("calificaciones", calificaciones);

        return response;
    }
}

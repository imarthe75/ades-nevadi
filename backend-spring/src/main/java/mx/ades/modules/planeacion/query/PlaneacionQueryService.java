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
        List<Map<String, Object>> coberturaPorMateria = jdbc.queryForList("""
            SELECT
                mp.materia_id,
                m.nombre_materia,
                m.tipo_materia,
                COUNT(t.id)                                          AS total_temas,
                COUNT(av.id) FILTER (WHERE av.es_completado = TRUE)   AS temas_impartidos,
                COUNT(pc.id) FILTER (WHERE av.id IS NULL)             AS temas_planeados,
                COUNT(t.id) - COUNT(pc.id)                            AS temas_pendientes,
                CASE WHEN COUNT(t.id) = 0 THEN 0
                     ELSE ROUND(100.0 * COUNT(av.id) FILTER (WHERE av.es_completado = TRUE) / COUNT(t.id))
                END                                                   AS pct_cobertura
            FROM ades_grupos g
            JOIN ades_grados gr        ON gr.id = g.grado_id
            JOIN ades_materias_plan mp ON mp.grado_id = gr.id
                AND mp.ciclo_escolar_id = g.ciclo_escolar_id AND mp.is_active = TRUE
            JOIN ades_materias m       ON m.id = mp.materia_id
            JOIN ades_temas t          ON t.materia_id = mp.materia_id
                AND (t.grado_id IS NULL OR t.grado_id = gr.id) AND t.is_active = TRUE
            LEFT JOIN ades_planeacion_clases pc
                ON pc.tema_id = t.id AND pc.grupo_id = g.id AND pc.is_active = TRUE
            LEFT JOIN ades_avance_planificacion av
                ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE g.id = ?
            GROUP BY mp.materia_id, m.nombre_materia, m.tipo_materia
            ORDER BY m.nombre_materia
            """, grupoId);

        long totalTemas = coberturaPorMateria.stream().mapToLong(m -> ((Number) m.get("total_temas")).longValue()).sum();
        long temasImpartidos = coberturaPorMateria.stream().mapToLong(m -> ((Number) m.get("temas_impartidos")).longValue()).sum();
        int pctCobertura = totalTemas == 0 ? 0 : (int) Math.round(100.0 * temasImpartidos / totalTemas);
        String estado = pctCobertura >= 80 ? "OK" : pctCobertura >= 50 ? "ALERTA" : "CRITICO";

        Map<String, Object> resumen = Map.of(
                "total_temas", totalTemas,
                "temas_impartidos", temasImpartidos,
                "pct_cobertura", pctCobertura,
                "estado", estado);

        Map<String, Object> tareas = jdbc.queryForMap("""
            SELECT
                COUNT(*)                                    AS total_tareas,
                COUNT(*) FILTER (WHERE tema_id IS NOT NULL)  AS tareas_con_tema,
                COUNT(*) FILTER (WHERE tema_id IS NULL)      AS tareas_sin_tema,
                CASE WHEN COUNT(*) = 0 THEN 0
                     ELSE ROUND(100.0 * COUNT(*) FILTER (WHERE tema_id IS NOT NULL) / COUNT(*))
                END                                          AS pct_vinculadas
            FROM ades_tareas
            WHERE grupo_id = ? AND is_active = TRUE
            """, grupoId);

        List<Map<String, Object>> calificaciones = jdbc.queryForList("""
            SELECT
                m.nombre_materia,
                ROUND(AVG(cp.calificacion_final), 1)                    AS promedio,
                COUNT(DISTINCT cp.estudiante_id)                        AS alumnos_evaluados,
                COUNT(DISTINCT cp.estudiante_id) FILTER (WHERE cp.calificacion_final < 6) AS en_riesgo
            FROM ades_calificaciones_periodo cp
            JOIN ades_materias m ON m.id = cp.materia_id
            WHERE cp.grupo_id = ? AND cp.is_active = TRUE
            GROUP BY m.nombre_materia
            ORDER BY m.nombre_materia
            """, grupoId);

        return Map.of(
                "resumen", resumen,
                "cobertura_por_materia", coberturaPorMateria,
                "tareas", tareas,
                "calificaciones", calificaciones);
    }

    /**
     * Obtener planeación de una semana específica (FASE 2 - Planeaciones Semanales).
     * Busca por trimestre y número de semana.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPlaneacionSemanal(UUID grupoId, Integer trimestre, Integer semana, UUID materiaId) {
        List<Object> params = new ArrayList<>();
        String query = """
            SELECT
                pc.id AS planeacion_id,
                pc.numero_trimestre,
                pc.numero_semana,
                pc.modalidad,
                pc.fecha_planeada,
                pc.fecha_fin,
                pc.descripcion_actividades,
                pc.recursos_didacticos,
                t.id AS tema_id,
                t.nombre_tema,
                m.id AS materia_id,
                m.nombre_materia,
                c.codigo AS competencia_codigo,
                c.nombre AS competencia_nombre,
                av.es_completado,
                av.fecha_ejecucion,
                av.comentarios_profesor
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            LEFT JOIN ades_competencias c ON c.ref = pc.competencia_id
            LEFT JOIN ades_avance_planificacion av ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE pc.grupo_id = ?::uuid
              AND pc.is_active = TRUE
            """;
        params.add(grupoId.toString());

        if (trimestre != null) {
            query += " AND pc.numero_trimestre = ?";
            params.add(trimestre);
        }
        if (semana != null) {
            query += " AND pc.numero_semana = ?";
            params.add(semana);
        }
        if (materiaId != null) {
            query += " AND t.materia_id = ?::uuid";
            params.add(materiaId.toString());
        }
        query += " ORDER BY pc.numero_semana, m.nombre_materia, t.orden";
        return jdbc.queryForList(query, params.toArray());
    }

    /**
     * Obtener cobertura por semana (FASE 2 - Dashboard semanal).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCoberturaSemanal(UUID grupoId, Integer trimestre) {
        List<Object> params = new ArrayList<>();
        String query = """
            SELECT
                pc.numero_semana,
                pc.numero_trimestre,
                COUNT(DISTINCT pc.id) AS temas_planeados,
                COUNT(DISTINCT CASE WHEN av.es_completado = TRUE THEN pc.id END) AS temas_impartidos,
                ROUND(
                    COUNT(DISTINCT CASE WHEN av.es_completado = TRUE THEN pc.id END)::NUMERIC /
                    NULLIF(COUNT(DISTINCT pc.id), 0) * 100, 1
                ) AS pct_cobertura
            FROM ades_planeacion_clases pc
            LEFT JOIN ades_avance_planificacion av ON av.planeacion_clase_id = pc.id AND av.is_active = TRUE
            WHERE pc.grupo_id = ?::uuid AND pc.is_active = TRUE
            """;
        params.add(grupoId.toString());

        if (trimestre != null) {
            query += " AND pc.numero_trimestre = ?";
            params.add(trimestre);
        }
        query += " GROUP BY pc.numero_semana, pc.numero_trimestre ORDER BY pc.numero_semana";
        return jdbc.queryForList(query, params.toArray());
    }

    /**
     * Validar que trimestre/semana están dentro del rango válido del ciclo escolar.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getValidacionTrimestralSemanal(Integer trimestre, Integer semana) {
        Boolean trimestreValido = trimestre != null && trimestre >= 1 && trimestre <= 3;
        Boolean semanaValida = semana != null && semana >= 1 && semana <= 40;
        return Map.of(
            "trimestre_valido", trimestreValido,
            "semana_valida", semanaValida,
            "ambas_validas", trimestreValido && semanaValida);
    }

    /**
     * FASE 2: Obtener temario (temas) y aprendizajes esperados disponibles
     * para una materia/grado específicos. Usado para crear planeación semanal.
     *
     * @param materiaId    UUID de la materia
     * @param gradoId      UUID del grado
     * @return Map con dos arrays: temas y aprendizajes
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTemarioYAprendizajes(UUID materiaId, UUID gradoId) {
        List<Map<String, Object>> temas = jdbc.queryForList("""
            SELECT
                t.id as tema_id,
                t.nombre_tema,
                t.descripcion,
                t.orden,
                t.periodo_sugerido,
                m.id as materia_id,
                m.nombre_materia
            FROM ades_temas t
            JOIN ades_materias m ON m.id = t.materia_id
            WHERE t.materia_id = ?::uuid
              AND (t.grado_id IS NULL OR t.grado_id = ?::uuid)
              AND t.is_active = TRUE
            ORDER BY t.orden, t.nombre_tema
            """, materiaId.toString(), gradoId.toString());

        List<Map<String, Object>> aprendizajes = jdbc.queryForList("""
            SELECT
                ae.ref as aprendizaje_id,
                ae.codigo,
                ae.descripcion,
                ae.orden,
                ae.grado_id,
                ae.materia_id,
                c.ref as competencia_id,
                c.codigo as competencia_codigo,
                c.nombre as competencia_nombre
            FROM ades_aprendizajes_esperados ae
            LEFT JOIN ades_competencias c ON c.ref = ae.competencia_id
            WHERE ae.materia_id = ?::uuid
              AND ae.grado_id = ?::uuid
              AND ae.activo = TRUE
            ORDER BY ae.orden, ae.codigo
            """, materiaId.toString(), gradoId.toString());

        return Map.of(
            "temas", temas,
            "aprendizajes", aprendizajes,
            "cantidad_temas", temas.size(),
            "cantidad_aprendizajes", aprendizajes.size()
        );
    }

    // ── FASE 3: Tareas/Exámenes Vinculados ────────────────────────────────────

    /**
     * FASE 3: Obtener planeaciones activas de un grupo.
     * Usado para selector al crear tarea/examen vinculado.
     *
     * @param grupoId UUID del grupo
     * @return List de planeaciones con detalles
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPlaneacionesDelGrupo(UUID grupoId) {
        return jdbc.queryForList("""
            SELECT
                pc.ref as planeacion_id,
                pc.numero_trimestre,
                pc.numero_semana,
                pc.modalidad,
                pc.fecha_planeada,
                pc.fecha_fin,
                t.nombre_tema,
                m.nombre_materia,
                COUNT(DISTINCT pae.ref) as cantidad_aprendizajes
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            LEFT JOIN ades_planeacion_aprendizajes pae ON pae.planeacion_clase_id = pc.ref
            WHERE pc.grupo_id = ?::uuid
              AND pc.is_active = TRUE
            GROUP BY pc.ref, pc.numero_trimestre, pc.numero_semana, pc.modalidad,
                     pc.fecha_planeada, pc.fecha_fin, t.nombre_tema, m.nombre_materia
            ORDER BY pc.numero_trimestre DESC, pc.numero_semana DESC, pc.fecha_planeada DESC
            """, grupoId.toString());
    }

    /**
     * FASE 3: Obtener aprendizajes vinculados a una planeación específica.
     * Usados para llenar aprendizajes_esperados en tarea/examen.
     *
     * @param planeacionClaseId UUID de la planeación
     * @return List de aprendizajes esperados
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAprendizajesDeplanneacion(UUID planeacionClaseId) {
        return jdbc.queryForList("""
            SELECT
                ae.ref as aprendizaje_id,
                ae.codigo,
                ae.descripcion,
                ae.orden,
                c.ref as competencia_id,
                c.codigo as competencia_codigo,
                c.nombre as competencia_nombre
            FROM ades_planeacion_aprendizajes pae
            JOIN ades_aprendizajes_esperados ae ON ae.ref = pae.aprendizaje_esperado_id
            LEFT JOIN ades_competencias c ON c.ref = ae.competencia_id
            WHERE pae.planeacion_clase_id = ?::uuid
            ORDER BY ae.orden, ae.codigo
            """, planeacionClaseId.toString());
    }

    /**
     * FASE 3: Obtener detalles de una planeación específica.
     * Usado para llenar contexto al crear tarea (grupo, materia, grado, aprendizajes).
     *
     * @param planeacionClaseId UUID de la planeación
     * @return Map con detalles completos
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDetallesplanneacion(UUID planeacionClaseId) {
        Map<String, Object> planeacion = jdbc.queryForMap("""
            SELECT
                pc.ref as planeacion_id,
                pc.grupo_id,
                pc.numero_trimestre,
                pc.numero_semana,
                pc.modalidad,
                pc.fecha_planeada,
                pc.fecha_fin,
                t.id as tema_id,
                t.nombre_tema,
                m.id as materia_id,
                m.nombre_materia,
                g.id as grado_id,
                g.numero_grado
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            JOIN ades_grupos gr ON gr.ref = pc.grupo_id
            JOIN ades_grados g ON g.id = gr.grado_id
            WHERE pc.ref = ?::uuid AND pc.is_active = TRUE
            """, planeacionClaseId.toString());

        // Obtener aprendizajes
        List<Map<String, Object>> aprendizajes = getAprendizajesDeplanneacion(planeacionClaseId);

        planeacion.put("aprendizajes", aprendizajes);
        planeacion.put("cantidad_aprendizajes", aprendizajes.size());

        return planeacion;
    }
}

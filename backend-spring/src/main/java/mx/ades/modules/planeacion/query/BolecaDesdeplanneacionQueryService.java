package mx.ades.modules.planeacion.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FASE 4: Cálculo de Boletas desde Planeación
 *
 * Flujo:
 * 1. Obtener planeaciones del período (trimestre)
 * 2. Obtener aprendizajes vinculados a esas planeaciones
 * 3. Obtener calificaciones de tareas/exámenes
 * 4. Calcular promedio por aprendizaje
 * 5. Agrupar por competencia
 * 6. Calcular nota final ponderada
 * 7. Retornar boleta integrada con trazabilidad
 */
@Service
@RequiredArgsConstructor
public class BolecaDesdeplanneacionQueryService {

    private final JdbcTemplate jdbc;

    /**
     * FASE 4: Calcular boleta de un alumno basada en trazabilidad planeación → tareas → calificaciones.
     *
     * @param alumnoId UUID del alumno
     * @param grupoId  UUID del grupo
     * @param trimestre Trimestre (1-3)
     * @return Map con estructura de boleta integrada
     */
    @Cacheable(value = "boletas", key = "#alumnoId + '_' + #grupoId + '_' + #trimestre")
    @Transactional(readOnly = true)
    public Map<String, Object> calcularBolecaDesdeplanneacion(UUID alumnoId, UUID grupoId, Integer trimestre) {
        // 1. Obtener planeaciones del trimestre
        List<Map<String, Object>> planeaciones = jdbc.queryForList("""
            SELECT DISTINCT
                pc.ref as planeacion_id,
                pc.numero_trimestre,
                pc.numero_semana,
                t.nombre_tema,
                m.nombre_materia
            FROM ades_planeacion_clases pc
            JOIN ades_temas t ON t.id = pc.tema_id
            JOIN ades_materias m ON m.id = t.materia_id
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
            ORDER BY pc.numero_semana, t.nombre_tema
            """, grupoId.toString(), trimestre);

        // 2. Obtener aprendizajes por competencia
        List<Map<String, Object>> aprendizajesPorCompetencia = jdbc.queryForList("""
            SELECT
                c.ref as competencia_id,
                c.codigo as competencia_codigo,
                c.nombre as competencia_nombre,
                ae.ref as aprendizaje_id,
                ae.codigo as aprendizaje_codigo,
                ae.descripcion as aprendizaje_descripcion
            FROM ades_planeacion_aprendizajes pae
            JOIN ades_aprendizajes_esperados ae ON ae.ref = pae.aprendizaje_esperado_id
            JOIN ades_competencias c ON c.ref = ae.competencia_id
            JOIN ades_planeacion_clases pc ON pc.ref = pae.planeacion_clase_id
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
            ORDER BY c.nombre, ae.codigo
            """, grupoId.toString(), trimestre);

        // 3. Para cada aprendizaje, obtener calificaciones (tareas + exámenes)
        Map<String, List<Double>> calificacionesPorAprendizaje = new java.util.HashMap<>();

        for (Map<String, Object> ap : aprendizajesPorCompetencia) {
            UUID aprendizajeId = (UUID) ap.get("aprendizaje_id");
            String clave = aprendizajeId.toString();

            // Calificaciones de tareas
            List<Double> notasTareas = jdbc.queryForList("""
                SELECT CAST(ct.calificacion AS DOUBLE PRECISION)
                FROM ades_calificaciones_tareas ct
                JOIN ades_tareas t ON t.ref = ct.tarea_id
                WHERE ct.alumno_id = ?::uuid
                  AND t.aprendizajes_esperados @> ARRAY[?::uuid]
                """, Double.class, alumnoId.toString(), aprendizajeId.toString());

            // Calificaciones de exámenes
            List<Double> notasExamenes = jdbc.queryForList("""
                SELECT CAST(ce.calificacion AS DOUBLE PRECISION)
                FROM ades_calificaciones_evaluaciones ce
                WHERE ce.estudiante_id = ?::uuid
                  AND ce.aprendizajes_esperados @> ARRAY[?::uuid]
                """, Double.class, alumnoId.toString(), aprendizajeId.toString());

            // Combinar todas las calificaciones
            java.util.List<Double> todas = new java.util.ArrayList<>();
            todas.addAll(notasTareas);
            todas.addAll(notasExamenes);

            if (!todas.isEmpty()) {
                calificacionesPorAprendizaje.put(clave, todas);
            }
        }

        // 4. Calcular promedio por aprendizaje y agrupar por competencia
        Map<String, Map<String, Object>> calificacionesPorCompetencia = new java.util.LinkedHashMap<>();

        for (Map<String, Object> ap : aprendizajesPorCompetencia) {
            UUID aprendizajeId = (UUID) ap.get("aprendizaje_id");
            UUID competenciaId = (UUID) ap.get("competencia_id");
            String competenciaCodigo = (String) ap.get("competencia_codigo");
            String competenciaNombre = (String) ap.get("competencia_nombre");

            String aprendizajeClave = aprendizajeId.toString();
            List<Double> calificaciones = calificacionesPorAprendizaje.get(aprendizajeClave);

            if (calificaciones != null && !calificaciones.isEmpty()) {
                double promedio = calificaciones.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

                String compClave = competenciaId.toString();
                if (!calificacionesPorCompetencia.containsKey(compClave)) {
                    java.util.Map<String, Object> competencia = new java.util.HashMap<>();
                    competencia.put("competencia_id", competenciaId);
                    competencia.put("competencia_codigo", competenciaCodigo);
                    competencia.put("competencia_nombre", competenciaNombre);
                    competencia.put("calificaciones", new java.util.ArrayList<Double>());
                    calificacionesPorCompetencia.put(compClave, competencia);
                }

                @SuppressWarnings("unchecked")
                java.util.List<Double> cals = (java.util.List<Double>) calificacionesPorCompetencia
                    .get(compClave).get("calificaciones");
                cals.add(promedio);
            }
        }

        // 5. Calcular promedio por competencia y nota final
        double notaFinal = 0.0;
        int contador = 0;

        for (Map<String, Object> comp : calificacionesPorCompetencia.values()) {
            @SuppressWarnings("unchecked")
            java.util.List<Double> cals = (java.util.List<Double>) comp.get("calificaciones");
            double promedioCompetencia = cals.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            comp.put("promedio", Math.round(promedioCompetencia * 10.0) / 10.0);
            notaFinal += promedioCompetencia;
            contador++;
        }

        if (contador > 0) {
            notaFinal = Math.round((notaFinal / contador) * 10.0) / 10.0;
        }

        // Construir respuesta
        return Map.of(
            "alumno_id", alumnoId,
            "grupo_id", grupoId,
            "trimestre", trimestre,
            "nota_final", notaFinal,
            "calificaciones_por_competencia", calificacionesPorCompetencia.values(),
            "cantidad_planeaciones", planeaciones.size(),
            "cantidad_competencias", calificacionesPorCompetencia.size(),
            "trazabilidad", Map.of(
                "planeaciones_del_periodo", planeaciones.size(),
                "aprendizajes_evaluados", calificacionesPorAprendizaje.size(),
                "timestamp", System.currentTimeMillis()
            )
        );
    }

    /**
     * FASE 4: Obtener cobertura curricular de un grupo por trimestre.
     * Muestra: % aprendizajes planeados vs evaluados vs logrados.
     *
     * @param grupoId UUID del grupo
     * @param trimestre Trimestre (1-3)
     * @return Map con estadísticas
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCoberturaCurricularGrupo(UUID grupoId, Integer trimestre) {
        // Contar planeaciones
        Integer planeacionesTotal = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT pc.ref)
            FROM ades_planeacion_clases pc
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
            """, Integer.class, grupoId.toString(), trimestre);

        // Contar aprendizajes planeados
        Integer aprendizajesPlanificados = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT pae.ref)
            FROM ades_planeacion_aprendizajes pae
            JOIN ades_planeacion_clases pc ON pc.ref = pae.planeacion_clase_id
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
            """, Integer.class, grupoId.toString(), trimestre);

        // Contar aprendizajes con calificaciones (evaluados)
        Integer aprendizajesEvaluados = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT ae.ref)
            FROM ades_planeacion_aprendizajes pae
            JOIN ades_aprendizajes_esperados ae ON ae.ref = pae.aprendizaje_esperado_id
            JOIN ades_planeacion_clases pc ON pc.ref = pae.planeacion_clase_id
            LEFT JOIN ades_calificaciones_tareas ct ON ct.aprendizajes_esperados::text[] @> ARRAY[ae.ref::text]
            LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.aprendizajes_esperados::text[] @> ARRAY[ae.ref::text]
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
              AND (ct.ref IS NOT NULL OR ce.ref IS NOT NULL)
            """, Integer.class, grupoId.toString(), trimestre);

        // Calcular porcentajes
        double pctCobertura = aprendizajesPlanificados > 0
            ? (aprendizajesEvaluados * 100.0) / aprendizajesPlanificados
            : 0.0;

        return Map.of(
            "grupo_id", grupoId,
            "trimestre", trimestre,
            "planeaciones_total", planeacionesTotal,
            "aprendizajes_planificados", aprendizajesPlanificados,
            "aprendizajes_evaluados", aprendizajesEvaluados,
            "porcentaje_cobertura", Math.round(pctCobertura * 10.0) / 10.0
        );
    }

    /**
     * FASE 4: Obtener estadísticas de cobertura por competencia y materia.
     * Útil para dashboard granular.
     *
     * @param grupoId UUID del grupo
     * @param trimestre Trimestre
     * @return List de estadísticas por competencia
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCoberturaPorCompetencia(UUID grupoId, Integer trimestre) {
        return jdbc.queryForList("""
            SELECT
                c.ref as competencia_id,
                c.codigo as competencia_codigo,
                c.nombre as competencia_nombre,
                m.nombre_materia,
                COUNT(DISTINCT ae.ref) as aprendizajes_planificados,
                COUNT(DISTINCT CASE
                    WHEN ct.ref IS NOT NULL OR ce.ref IS NOT NULL THEN ae.ref
                END) as aprendizajes_evaluados,
                ROUND(
                    COUNT(DISTINCT CASE WHEN ct.ref IS NOT NULL OR ce.ref IS NOT NULL THEN ae.ref END)::NUMERIC /
                    NULLIF(COUNT(DISTINCT ae.ref), 0) * 100, 1
                ) as porcentaje_cobertura
            FROM ades_competencias c
            JOIN ades_aprendizajes_esperados ae ON ae.competencia_id = c.ref
            JOIN ades_materias m ON m.ref = ae.materia_id
            JOIN ades_planeacion_aprendizajes pae ON pae.aprendizaje_esperado_id = ae.ref
            JOIN ades_planeacion_clases pc ON pc.ref = pae.planeacion_clase_id
            LEFT JOIN ades_calificaciones_tareas ct ON ct.aprendizajes_esperados::text[] @> ARRAY[ae.ref::text]
            LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.aprendizajes_esperados::text[] @> ARRAY[ae.ref::text]
            WHERE pc.grupo_id = ?::uuid
              AND pc.numero_trimestre = ?
              AND pc.is_active = TRUE
              AND ae.activo = TRUE
            GROUP BY c.ref, c.codigo, c.nombre, m.nombre_materia
            ORDER BY porcentaje_cobertura DESC, c.nombre
            """, grupoId.toString(), trimestre);
    }
}

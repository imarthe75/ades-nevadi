package mx.ades.modules.planeacion.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FASE 4: Queries para calificar tareas y exámenes desde planeación
 *
 * Proporciona:
 * - Listado de tareas/exámenes pendientes de calificar
 * - Detalles de tarea/examen con aprendizajes esperados
 * - Calificaciones históricas
 */
@Service
@RequiredArgsConstructor
public class CalificacionesDesdeplanneacionQueryService {

    private final JdbcTemplate jdbc;

    /**
     * FASE 4: Obtener tareas pendientes de calificar para un grupo.
     *
     * @param grupoId UUID del grupo
     * @return List de tareas con información de entregas
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTareasPendientesCalificar(UUID grupoId) {
        return jdbc.queryForList("""
            SELECT
                t.ref as tarea_id,
                t.titulo as nombre_tarea,
                t.descripcion,
                t.fecha_entrega,
                t.puntaje_maximo,
                m.nombre_materia,
                COUNT(DISTINCT et.ref) as total_entregas,
                COUNT(DISTINCT CASE WHEN ct.ref IS NULL THEN et.ref END) as entregas_sin_calificar,
                COUNT(DISTINCT CASE WHEN ct.ref IS NOT NULL THEN et.ref END) as entregas_calificadas
            FROM ades_tareas t
            JOIN ades_materias m ON m.ref = t.materia_id
            LEFT JOIN ades_entregas_tareas et ON et.tarea_id = t.ref AND et.is_active = TRUE
            LEFT JOIN ades_calificaciones_tareas ct ON ct.tarea_id = t.ref AND ct.is_active = TRUE
            WHERE t.grupo_id = ?::uuid
              AND t.is_active = TRUE
            GROUP BY t.ref, t.titulo, t.descripcion, t.fecha_entrega, t.puntaje_maximo, m.nombre_materia
            HAVING COUNT(DISTINCT CASE WHEN ct.ref IS NULL THEN et.ref END) > 0
            ORDER BY t.fecha_entrega DESC
            """, grupoId.toString());
    }

    /**
     * FASE 4: Obtener detalles de una tarea con aprendizajes y entregas.
     *
     * @param tareaId UUID de la tarea
     * @return Map con detalles completos
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDetallesTarea(UUID tareaId) {
        Map<String, Object> tarea = jdbc.queryForMap("""
            SELECT
                t.ref as tarea_id,
                t.titulo,
                t.descripcion,
                t.fecha_entrega,
                t.puntaje_maximo,
                t.planeacion_clase_id,
                g.ref as grupo_id,
                g.nombre_grupo,
                m.nombre_materia
            FROM ades_tareas t
            JOIN ades_grupos g ON g.ref = t.grupo_id
            JOIN ades_materias m ON m.ref = t.materia_id
            WHERE t.ref = ?::uuid AND t.is_active = TRUE
            """, tareaId.toString());

        // Obtener aprendizajes esperados
        List<Map<String, Object>> aprendizajes = jdbc.queryForList("""
            SELECT
                ae.ref as aprendizaje_id,
                ae.codigo,
                ae.descripcion,
                c.nombre as competencia_nombre
            FROM ades_aprendizajes_esperados ae
            LEFT JOIN ades_competencias c ON c.ref = ae.competencia_id
            WHERE ae.ref = ANY(
                SELECT UNNEST(aprendizajes_esperados)
                FROM ades_tareas WHERE ref = ?::uuid
            )
            """, tareaId.toString());

        // Obtener entregas sin calificar
        List<Map<String, Object>> entregas = jdbc.queryForList("""
            SELECT
                et.ref as entrega_id,
                et.alumno_id,
                est.nombre_alumno,
                et.fecha_entrega as fecha_entrega_alumno,
                et.es_tarde,
                ct.ref as calificacion_id,
                ct.calificacion,
                ct.comentarios
            FROM ades_entregas_tareas et
            JOIN ades_estudiantes est ON est.ref = et.alumno_id
            LEFT JOIN ades_calificaciones_tareas ct ON ct.tarea_id = et.tarea_id AND ct.alumno_id = et.alumno_id
            WHERE et.tarea_id = ?::uuid AND et.is_active = TRUE
            ORDER BY est.nombre_alumno
            """, tareaId.toString());

        tarea.put("aprendizajes", aprendizajes);
        tarea.put("entregas", entregas);
        tarea.put("cantidad_aprendizajes", aprendizajes.size());
        tarea.put("cantidad_entregas", entregas.size());

        return tarea;
    }

    /**
     * FASE 4: Obtener exámenes/evaluaciones pendientes de calificar.
     *
     * @param grupoId UUID del grupo
     * @return List de evaluaciones con conteos
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getExamenesPendientesCalificar(UUID grupoId) {
        return jdbc.queryForList("""
            SELECT
                e.ref as evaluacion_id,
                e.nombre as nombre_evaluacion,
                e.descripcion,
                e.fecha,
                e.puntaje_maximo,
                m.nombre_materia,
                COUNT(DISTINCT g.ref) as total_alumnos,
                COUNT(DISTINCT CASE WHEN ce.ref IS NULL THEN g.ref END) as sin_calificar,
                COUNT(DISTINCT CASE WHEN ce.ref IS NOT NULL THEN g.ref END) as calificados
            FROM ades_evaluaciones e
            JOIN ades_grupos g ON g.ref = e.grupo_id
            LEFT JOIN ades_materias m ON m.ref = g.materia_id
            LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = e.ref AND ce.is_active = TRUE
            WHERE e.grupo_id = ?::uuid
              AND e.is_active = TRUE
            GROUP BY e.ref, e.nombre, e.descripcion, e.fecha, e.puntaje_maximo, m.nombre_materia
            HAVING COUNT(DISTINCT CASE WHEN ce.ref IS NULL THEN g.ref END) > 0
            ORDER BY e.fecha DESC
            """, grupoId.toString());
    }

    /**
     * FASE 4: Obtener detalles de una evaluación con aprendizajes y estudiantes.
     *
     * @param evaluacionId UUID de la evaluación
     * @return Map con detalles completos
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDetallesEvaluacion(UUID evaluacionId) {
        Map<String, Object> evaluacion = jdbc.queryForMap("""
            SELECT
                e.ref as evaluacion_id,
                e.nombre,
                e.descripcion,
                e.fecha,
                e.puntaje_maximo,
                e.planeacion_clase_id,
                g.ref as grupo_id,
                g.nombre_grupo,
                m.nombre_materia
            FROM ades_evaluaciones e
            JOIN ades_grupos g ON g.ref = e.grupo_id
            JOIN ades_materias m ON m.ref = g.materia_id
            WHERE e.ref = ?::uuid AND e.is_active = TRUE
            """, evaluacionId.toString());

        // Obtener aprendizajes esperados
        List<Map<String, Object>> aprendizajes = jdbc.queryForList("""
            SELECT
                ae.ref as aprendizaje_id,
                ae.codigo,
                ae.descripcion,
                c.nombre as competencia_nombre
            FROM ades_aprendizajes_esperados ae
            LEFT JOIN ades_competencias c ON c.ref = ae.competencia_id
            WHERE ae.ref = ANY(
                SELECT UNNEST(aprendizajes_esperados)
                FROM ades_evaluaciones WHERE ref = ?::uuid
            )
            """, evaluacionId.toString());

        // Obtener estudiantes sin calificar
        List<Map<String, Object>> estudiantes = jdbc.queryForList("""
            SELECT
                est.ref as estudiante_id,
                est.nombre_alumno,
                est.numero_control,
                ce.ref as calificacion_id,
                ce.calificacion,
                ce.comentarios,
                ce.es_acreditado
            FROM ades_estudiantes est
            JOIN ades_grupos g ON g.ref = est.grupo_id
            LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = ?::uuid AND ce.estudiante_id = est.ref
            WHERE g.ref = (SELECT grupo_id FROM ades_evaluaciones WHERE ref = ?::uuid)
              AND est.is_active = TRUE
            ORDER BY est.nombre_alumno
            """, evaluacionId.toString(), evaluacionId.toString());

        evaluacion.put("aprendizajes", aprendizajes);
        evaluacion.put("estudiantes", estudiantes);
        evaluacion.put("cantidad_aprendizajes", aprendizajes.size());
        evaluacion.put("cantidad_estudiantes", estudiantes.size());

        return evaluacion;
    }
}

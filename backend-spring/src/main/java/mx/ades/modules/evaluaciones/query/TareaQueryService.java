package mx.ades.modules.evaluaciones.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS — lado lectura para Tareas.
 * Consolidado desde TareaService (queries) y TareaEntregaService (queries).
 */
@Service
@RequiredArgsConstructor
public class TareaQueryService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> actividadesDeGrupo(UUID grupoId, UUID materiaId, UUID periodoId, String tipoItem) {
        StringBuilder sql = new StringBuilder("""
            SELECT t.id, t.titulo, t.descripcion, t.tipo_item,
                   t.fecha_asignacion, t.fecha_entrega, t.fecha_examen,
                   t.puntaje_maximo, t.permite_entrega_tarde,
                   t.instrucciones_url,
                   m.nombre_materia,
                   pe.nombre_periodo,
                   tm.nombre_tema,
                   te_stats.total_alumnos, te_stats.entregadas, te_stats.calificadas
              FROM ades_tareas t
              JOIN ades_materias m ON m.id = t.materia_id
              LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
              LEFT JOIN ades_temas tm ON tm.id = t.tema_id
              LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS total_alumnos,
                         COUNT(*) FILTER (WHERE te.estatus_entrega IN ('ENTREGADA','CALIFICADA')) AS entregadas,
                         COUNT(*) FILTER (WHERE te.estatus_entrega = 'CALIFICADA') AS calificadas
                    FROM ades_tareas_entregas te
                   WHERE te.tarea_id = t.id
              ) te_stats ON TRUE
             WHERE t.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        if (grupoId   != null) { sql.append(" AND t.grupo_id = ?::uuid"); params.add(grupoId.toString()); }
        if (materiaId != null) { sql.append(" AND t.materia_id = ?::uuid"); params.add(materiaId.toString()); }
        if (periodoId != null) { sql.append(" AND t.periodo_evaluacion_id = ?::uuid"); params.add(periodoId.toString()); }
        if (tipoItem  != null && !tipoItem.isBlank()) { sql.append(" AND t.tipo_item = ?"); params.add(tipoItem); }
        sql.append(" ORDER BY t.fecha_entrega, t.tipo_item");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> entregasDeActividad(UUID tareaId) {
        return jdbc.queryForList("""
            SELECT te.id, te.estudiante_id, te.estatus_entrega,
                   te.fecha_entrega, te.es_tarde,
                   te.calificacion_obtenida, te.comentario_profesor,
                   te.archivo_url,
                   p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                   est.numero_matricula
              FROM ades_tareas_entregas te
              JOIN ades_estudiantes est ON est.id = te.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
             WHERE te.tarea_id = ?::uuid
             ORDER BY p.apellido_paterno, p.nombre
            """, tareaId.toString());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> entregasDelAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, Boolean soloPendientes) {
        StringBuilder sql = new StringBuilder("""
            SELECT te.id, te.tarea_id, te.estatus_entrega,
                   te.fecha_entrega, te.es_tarde,
                   te.calificacion_obtenida, te.comentario_profesor,
                   te.archivo_url, te.fecha_calificacion_docente,
                   t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, t.puntaje_maximo,
                   m.nombre_materia, pe.nombre_periodo,
                   (t.fecha_entrega < CURRENT_DATE AND te.estatus_entrega = 'PENDIENTE') AS vencida
              FROM ades_tareas_entregas te
              JOIN ades_tareas t ON t.id = te.tarea_id
              JOIN ades_materias m ON m.id = t.materia_id
              LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id
             WHERE te.estudiante_id = ?::uuid AND te.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        params.add(alumnoId.toString());
        if (periodoId != null) { sql.append(" AND t.periodo_evaluacion_id = ?::uuid"); params.add(periodoId.toString()); }
        if (materiaId != null) { sql.append(" AND t.materia_id = ?::uuid"); params.add(materiaId.toString()); }
        if (Boolean.TRUE.equals(soloPendientes)) { sql.append(" AND te.estatus_entrega = 'PENDIENTE'"); }
        sql.append(" ORDER BY t.fecha_entrega DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional
    public Map<String, Object> actualizarTarea(UUID actividadId, Map<String, Object> body) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.containsKey("titulo"))         { sets.add("titulo = ?");          params.add(body.get("titulo")); }
        if (body.containsKey("descripcion"))    { sets.add("descripcion = ?");      params.add(body.get("descripcion")); }
        if (body.containsKey("fecha_entrega")) {
            Object raw = body.get("fecha_entrega");
            if (raw != null) {
                try { LocalDate.parse(raw.toString()); }
                catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "fecha_entrega inválida. Formato esperado: YYYY-MM-DD");
                }
            }
            sets.add("fecha_entrega = ?::date"); params.add(raw);
        }
        if (body.containsKey("puntaje_maximo")) {
            Object raw = body.get("puntaje_maximo");
            if (raw instanceof Number n && n.doubleValue() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "puntaje_maximo no puede ser negativo");
            }
            sets.add("puntaje_maximo = ?"); params.add(raw);
        }
        if (body.containsKey("permite_entrega_tarde")) { sets.add("permite_entrega_tarde = ?"); params.add(body.get("permite_entrega_tarde")); }
        if (!sets.isEmpty()) {
            params.add(actividadId.toString());
            int rows = jdbc.update(
                "UPDATE ades_tareas SET " + String.join(", ", sets) + " WHERE id = ?::uuid AND is_active = TRUE",
                params.toArray());
            if (rows == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", actividadId);
        result.put("updated", true);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendientesDelGrupo(UUID grupoId, UUID materiaId) {
        StringBuilder sql = new StringBuilder("""
            SELECT te.id, te.estudiante_id, te.estatus_entrega,
                   te.fecha_entrega, te.archivo_url, te.comentario_alumno,
                   t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, t.id AS actividad_id,
                   m.nombre_materia,
                   p.nombre || ' ' || p.apellido_paterno AS alumno_nombre,
                   est.numero_matricula
              FROM ades_tareas_entregas te
              JOIN ades_tareas t ON t.id = te.tarea_id
              JOIN ades_materias m ON m.id = t.materia_id
              JOIN ades_estudiantes est ON est.id = te.estudiante_id
              JOIN ades_personas p ON p.id = est.persona_id
             WHERE t.grupo_id = ?::uuid AND te.estatus_entrega = 'ENTREGADA' AND te.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        params.add(grupoId.toString());
        if (materiaId != null) { sql.append(" AND t.materia_id = ?::uuid"); params.add(materiaId.toString()); }
        sql.append(" ORDER BY t.fecha_entrega, p.apellido_paterno");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}

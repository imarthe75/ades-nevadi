package mx.ades.modules.evaluaciones.query;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo evaluaciones.
 * <p>Expone listado de evaluaciones por grupo, calificaciones por evaluación,
 * NEE de alumnos, asignaciones de aula y actas SEP.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
public class EvaluacionQueryService {

    private static final String EVAL_SELECT =
        "SELECT e.id, e.nombre_evaluacion, e.descripcion, e.grupo_id, e.materia_id, " +
        "  e.periodo_evaluacion_id, e.fecha_evaluacion, e.tipo_evaluacion, e.puntaje_maximo, " +
        "  e.is_active, e.row_version, " +
        "  g.nombre_grupo, " +
        "  m.nombre_materia, " +
        "  pe.nombre_periodo, pe.numero_periodo, " +
        "  COUNT(c.id) AS total_calificados, " +
        "  ROUND(AVG(c.calificacion)::numeric, 2) AS promedio, " +
        "  COUNT(c.id) FILTER (WHERE c.calificacion >= 6) AS aprobados, " +
        "  COUNT(c.id) FILTER (WHERE c.calificacion < 6) AS reprobados " +
        "FROM ades_evaluaciones e " +
        "JOIN ades_grupos g ON g.id = e.grupo_id " +
        "JOIN ades_materias m ON m.id = e.materia_id " +
        "JOIN ades_periodos_evaluacion pe ON pe.id = e.periodo_evaluacion_id " +
        "LEFT JOIN ades_calificaciones_evaluaciones c ON c.evaluacion_id = e.id AND c.is_active = TRUE ";

    private final JdbcTemplate jdbc;

    public EvaluacionQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listar(UUID grupoId, UUID cicloId) {
        StringBuilder sql = new StringBuilder(EVAL_SELECT + "WHERE e.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        if (grupoId != null) {
            sql.append("AND e.grupo_id = ? ");
            params.add(grupoId);
        }
        if (cicloId != null) {
            sql.append("AND g.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        sql.append("GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo ");
        sql.append("ORDER BY e.fecha_evaluacion DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public UUID grupoIdDeEvaluacion(UUID evalId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT grupo_id FROM ades_evaluaciones WHERE id = ?", evalId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada");
        return (UUID) rows.get(0).get("grupo_id");
    }

    public List<Map<String, Object>> calificacionesPorEvaluacion(UUID evalId, UUID grupoId) {
        return jdbc.queryForList(
            "SELECT est.id AS estudiante_id, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_alumno, " +
            "  est.matricula, " +
            "  c.id AS calificacion_id, c.calificacion, c.comentarios " +
            "FROM ades_inscripciones ins " +
            "JOIN ades_estudiantes est ON est.id = ins.estudiante_id " +
            "JOIN ades_personas pe ON pe.id = est.persona_id " +
            "LEFT JOIN ades_calificaciones_evaluaciones c ON c.evaluacion_id = ? AND c.estudiante_id = est.id AND c.is_active = TRUE " +
            "WHERE ins.grupo_id = ? AND ins.is_active = TRUE " +
            "ORDER BY pe.apellido_paterno, pe.nombre",
            evalId, grupoId);
    }

    public List<Map<String, Object>> listarNee(UUID plantelId, String tipoNee) {
        StringBuilder sql = new StringBuilder(
            "SELECT n.id, n.tipo_nee, n.descripcion, n.apoyos_requeridos, " +
            "n.fecha_deteccion, n.profesional_detecta, " +
            "p.nombre || ' ' || p.apellido_paterno as alumno, " +
            "e.matricula AS numero_control " +
            "FROM ades_nee n " +
            "JOIN ades_estudiantes e ON e.id = n.alumno_id " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "WHERE n.activa = TRUE");
        List<Object> params = new ArrayList<>();
        if (tipoNee != null && !tipoNee.isBlank()) { sql.append(" AND n.tipo_nee = ?"); params.add(tipoNee); }
        if (plantelId != null) { sql.append(" AND e.plantel_id = ?"); params.add(plantelId); }
        sql.append(" ORDER BY p.apellido_paterno, p.nombre");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarAsignacionesAula(UUID aulaId, LocalDate fecha) {
        StringBuilder sql = new StringBuilder(
            "SELECT aa.id, aa.fecha, aa.hora_inicio, aa.hora_fin, aa.observaciones, " +
            "a.nombre_aula, a.clave_aula, " +
            "cl.tema_visto as clase_desc " +
            "FROM ades_asignaciones_aula aa " +
            "JOIN ades_aulas a ON a.id = aa.aula_id " +
            "LEFT JOIN ades_clases cl ON cl.id = aa.clase_id " +
            "WHERE aa.is_active = TRUE");
        List<Object> params = new ArrayList<>();
        if (aulaId != null) { sql.append(" AND aa.aula_id = ?"); params.add(aulaId); }
        if (fecha != null) { sql.append(" AND aa.fecha = ?"); params.add(fecha); }
        sql.append(" ORDER BY aa.fecha, aa.hora_inicio");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> actasSep(UUID grupoId, String periodo) {
        return jdbc.queryForList(
            "SELECT p.nombre || ' ' || p.apellido_paterno AS alumno, " +
            "e.matricula AS numero_control, m.nombre_materia, " +
            "COALESCE(c.calificacion_final, 0) as calificacion, " +
            "COALESCE(c.asistencia_porcentaje, 0) as asistencia " +
            "FROM ades_inscripciones i " +
            "JOIN ades_estudiantes e ON e.id = i.estudiante_id " +
            "JOIN ades_personas p ON p.id = e.persona_id " +
            "LEFT JOIN ades_calificaciones c ON c.inscripcion_id = i.id " +
            "LEFT JOIN ades_materias m ON m.id = c.materia_id " +
            "WHERE i.grupo_id = ? AND i.is_active = TRUE " +
            "ORDER BY p.apellido_paterno, p.nombre, m.nombre_materia",
            grupoId);
    }

    public List<Map<String, Object>> fetchGrupo(UUID grupoId) {
        // plantel_id incluido (vía ades_grados) para permitir el scoping por plantel de
        // no-admins en EvaluacionAvanzadaController#generarActaSep (BOLA fix).
        return jdbc.queryForList(
            "SELECT g.id, g.nombre_grupo, gr.plantel_id " +
            "FROM ades_grupos g JOIN ades_grados gr ON gr.id = g.grado_id " +
            "WHERE g.id = ? AND g.is_active = TRUE", grupoId);
    }
}

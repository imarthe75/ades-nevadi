package mx.ades.modules.gradebook.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ActividadesQueryService {

    private final JdbcTemplate jdbc;

    public ActividadesQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> actividadesDeGrupo(UUID grupoId, UUID materiaId, UUID periodoId, String tipoItem) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.titulo, t.descripcion, t.tipo_item, " +
                "t.fecha_asignacion, t.fecha_entrega, t.fecha_examen, " +
                "t.puntaje_maximo, t.permite_entrega_tarde, " +
                "t.instrucciones_url, " +
                "m.nombre_materia, " +
                "te_stats.total_alumnos, " +
                "te_stats.entregadas, " +
                "te_stats.calificadas, " +
                "pe.nombre_periodo, " +
                "tm.nombre_tema " +
                "FROM ades_tareas t " +
                "JOIN ades_materias m ON m.id = t.materia_id " +
                "LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id " +
                "LEFT JOIN ades_temas tm ON tm.id = t.tema_id " +
                "LEFT JOIN LATERAL ( " +
                "    SELECT COUNT(*) AS total_alumnos, " +
                "           COUNT(*) FILTER (WHERE te.estatus_entrega IN ('ENTREGADA','CALIFICADA')) AS entregadas, " +
                "           COUNT(*) FILTER (WHERE te.estatus_entrega = 'CALIFICADA') AS calificadas " +
                "      FROM ades_tareas_entregas te " +
                "     WHERE te.tarea_id = t.id " +
                ") te_stats ON TRUE " +
                "WHERE t.grupo_id = ? AND t.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (materiaId != null) {
            sql.append("AND t.materia_id = ? ");
            params.add(materiaId);
        }
        if (periodoId != null) {
            sql.append("AND t.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }
        if (tipoItem != null && !tipoItem.isBlank()) {
            sql.append("AND t.tipo_item = ? ");
            params.add(tipoItem);
        }

        sql.append("ORDER BY t.fecha_entrega, t.tipo_item");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> entregasDeActividad(UUID actividadId) {
        String sql = "SELECT te.id, te.estudiante_id, te.estatus_entrega, " +
                "te.fecha_entrega, te.es_tarde, " +
                "te.calificacion_obtenida, te.comentario_profesor, " +
                "te.archivo_url, " +
                "COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "est.matricula " +
                "FROM ades_tareas_entregas te " +
                "JOIN ades_estudiantes est ON est.id = te.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "WHERE te.tarea_id = ? " +
                "ORDER BY p.apellido_paterno, p.nombre";
        return jdbc.queryForList(sql, actividadId);
    }
}

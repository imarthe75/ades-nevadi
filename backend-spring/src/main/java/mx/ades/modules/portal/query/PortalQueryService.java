package mx.ades.modules.portal.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalQueryService {

    private final JdbcTemplate jdbc;

    public String getCicloActivo(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ins.ciclo_escolar_id FROM ades_inscripciones ins " +
                "WHERE ins.estudiante_id = ? " +
                "ORDER BY ins.fecha_creacion DESC LIMIT 1",
                estudianteId
        );
        if (rows.isEmpty()) return null;
        return rows.get(0).get("ciclo_escolar_id").toString();
    }

    public List<Map<String, Object>> buscarAlumnos(String q, UUID plantelId, UUID cicloId) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT e.id, e.matricula, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "g.nombre_grupo, " +
                "pl.nombre_plantel AS nombre_plantel, " +
                "ne.nombre_nivel AS nivel " +
                "FROM ades_estudiantes e " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_inscripciones ins ON ins.estudiante_id = e.id " +
                "JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
                "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE e.is_active = TRUE " +
                "AND (p.nombre ILIKE ? " +
                "OR p.apellido_paterno ILIKE ? " +
                "OR p.apellido_materno ILIKE ? " +
                "OR e.matricula ILIKE ? " +
                "OR (p.nombre || ' ' || p.apellido_paterno) ILIKE ?) "
        );
        List<Object> params = new ArrayList<>();
        String qp = "%" + q + "%";
        params.add(qp); params.add(qp); params.add(qp); params.add(qp); params.add(qp);
        if (plantelId != null) { sql.append("AND gr.plantel_id = ? "); params.add(plantelId); }
        if (cicloId != null) { sql.append("AND ins.ciclo_escolar_id = ? "); params.add(cicloId); }
        sql.append("ORDER BY p.apellido_paterno, p.nombre LIMIT 20");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> resumenAlumno(UUID estudianteId, UUID cicloRef) {
        List<Map<String, Object>> alumnoRows = jdbc.queryForList(
                "SELECT e.id, e.matricula, e.fecha_ingreso, " +
                "p.nombre, p.apellido_paterno, p.apellido_materno, p.foto_url, " +
                "p.fecha_nacimiento, p.genero, " +
                "g.nombre_grupo, " +
                "ne.nombre_nivel AS nivel, " +
                "pl.nombre_plantel AS plantel, " +
                "ce.nombre_ciclo AS ciclo " +
                "FROM ades_estudiantes e " +
                "JOIN ades_personas p ON p.id = e.persona_id " +
                "JOIN ades_inscripciones ins ON ins.estudiante_id = e.id AND ins.ciclo_escolar_id = ? " +
                "JOIN ades_grupos g ON g.id = ins.grupo_id " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "JOIN ades_planteles pl ON pl.id = gr.plantel_id " +
                "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "JOIN ades_ciclos_escolares ce ON ce.id = ins.ciclo_escolar_id " +
                "WHERE e.id = ? AND e.is_active = TRUE LIMIT 1",
                cicloRef, estudianteId
        );
        if (alumnoRows.isEmpty()) return null;
        Map<String, Object> alumno = alumnoRows.get(0);

        String nombreCompleto = String.format("%s %s %s",
                alumno.get("nombre"),
                alumno.get("apellido_paterno"),
                alumno.get("apellido_materno") != null ? alumno.get("apellido_materno") : ""
        ).trim();

        Double promedio = jdbc.queryForObject(
                "SELECT COALESCE(ROUND(AVG(cp.calificacion_final), 2), 0.0) " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "WHERE cp.estudiante_id = ? AND pe.ciclo_escolar_id = ?",
                Double.class, estudianteId, cicloRef
        );

        List<Map<String, Object>> asist = jdbc.queryForList(
                "SELECT COUNT(a.id) AS total, " +
                "COUNT(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 END) AS presentes " +
                "FROM ades_asistencias a " +
                "JOIN ades_clases cl ON cl.id = a.clase_id " +
                "WHERE a.estudiante_id = ? " +
                "AND cl.grupo_id IN (SELECT grupo_id FROM ades_inscripciones WHERE estudiante_id = ? AND ciclo_escolar_id = ?)",
                estudianteId, estudianteId, cicloRef
        );
        double pctAsistencia = 0.0;
        if (!asist.isEmpty()) {
            long total = ((Number) asist.get(0).get("total")).longValue();
            long presentes = ((Number) asist.get(0).get("presentes")).longValue();
            if (total > 0) pctAsistencia = Math.round(1000.0 * presentes / total) / 10.0;
        }

        Long tareasPendientes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tareas t " +
                "WHERE t.is_active = TRUE " +
                "AND t.grupo_id IN (SELECT grupo_id FROM ades_inscripciones WHERE estudiante_id = ? AND ciclo_escolar_id = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM ades_tareas_entregas te WHERE te.tarea_id = t.id AND te.estudiante_id = ?)",
                Long.class, estudianteId, cicloRef, estudianteId
        );

        Long badgesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_badge_otorgados WHERE estudiante_id = ? AND ciclo_id = ?",
                Long.class, estudianteId, cicloRef
        );

        List<Map<String, Object>> alertas = jdbc.queryForList(
                "SELECT tipo_alerta, nivel_riesgo, descripcion " +
                "FROM ades_alertas_academicas " +
                "WHERE estudiante_id = ? AND atendida = FALSE AND is_active = TRUE " +
                "ORDER BY nivel_riesgo DESC LIMIT 5",
                estudianteId
        );

        List<Map<String, Object>> badges = jdbc.queryForList(
                "SELECT b.nombre, b.icono, b.color, b.tipo, o.fecha_otorgado, o.motivo " +
                "FROM ades_badge_otorgados o " +
                "JOIN ades_badges b ON b.id = o.badge_id " +
                "WHERE o.estudiante_id = ? AND o.ciclo_id = ? " +
                "ORDER BY o.fecha_otorgado DESC",
                estudianteId, cicloRef
        );

        List<Map<String, Object>> learningPaths = jdbc.queryForList(
                "SELECT lp.nombre, la.pct_completado, la.estatus, la.fcinicio, la.fccompletado " +
                "FROM ades_lp_asignaciones la " +
                "JOIN ades_learning_paths lp ON lp.id = la.path_id " +
                "WHERE la.estudiante_id = ? " +
                "ORDER BY la.fecha_creacion DESC LIMIT 5",
                estudianteId
        );

        Map<String, Object> alumnoData = new HashMap<>();
        alumnoData.put("id", estudianteId);
        alumnoData.put("nombre", nombreCompleto);
        alumnoData.put("matricula", alumno.get("matricula"));
        alumnoData.put("grupo", alumno.get("nombre_grupo"));
        alumnoData.put("nivel", alumno.get("nivel"));
        alumnoData.put("plantel", alumno.get("plantel"));
        alumnoData.put("ciclo", alumno.get("ciclo"));
        alumnoData.put("foto_url", alumno.get("foto_url"));
        alumnoData.put("fecha_nacimiento", alumno.get("fecha_nacimiento") != null ? alumno.get("fecha_nacimiento").toString() : null);
        alumnoData.put("genero", alumno.get("genero"));

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("promedio_general", promedio);
        kpis.put("pct_asistencia", pctAsistencia);
        kpis.put("tareas_pendientes", tareasPendientes);
        kpis.put("badges_count", badgesCount);

        Map<String, Object> response = new HashMap<>();
        response.put("alumno", alumnoData);
        response.put("kpis", kpis);
        response.put("alertas", alertas);
        response.put("badges", badges);
        response.put("learning_paths", learningPaths);
        return response;
    }

    public List<Map<String, Object>> calificacionesAlumno(UUID estudianteId, UUID cicloRef) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT m.nombre_materia AS materia, " +
                "pe.nombre_periodo AS periodo, " +
                "pe.numero_periodo AS orden, " +
                "cp.calificacion_final AS calificacion, " +
                "cp.es_acreditado, cp.observaciones " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "JOIN ades_materias m ON m.id = cp.materia_id " +
                "WHERE cp.estudiante_id = ? AND pe.ciclo_escolar_id = ? " +
                "ORDER BY m.nombre_materia, pe.numero_periodo",
                estudianteId, cicloRef
        );

        Map<String, Map<String, Object>> materias = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String m = (String) r.get("materia");
            materias.computeIfAbsent(m, k -> {
                Map<String, Object> md = new HashMap<>();
                md.put("materia", k);
                md.put("periodos", new ArrayList<Map<String, Object>>());
                md.put("promedio", 0.0);
                return md;
            });
            Map<String, Object> pData = new HashMap<>();
            pData.put("periodo", r.get("periodo"));
            pData.put("orden", r.get("orden"));
            pData.put("calificacion", r.get("calificacion") != null ? ((Number) r.get("calificacion")).doubleValue() : null);
            pData.put("acreditado", r.get("es_acreditado"));
            pData.put("observaciones", r.get("observaciones"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> periodos = (List<Map<String, Object>>) materias.get(m).get("periodos");
            periodos.add(pData);
        }

        for (Map<String, Object> v : materias.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> periodos = (List<Map<String, Object>>) v.get("periodos");
            double sum = 0.0; int count = 0;
            for (Map<String, Object> p : periodos) {
                Double cal = (Double) p.get("calificacion");
                if (cal != null) { sum += cal; count++; }
            }
            if (count > 0) v.put("promedio", Math.round(100.0 * sum / count) / 100.0);
        }
        return new ArrayList<>(materias.values());
    }

    public Map<String, Object> asistenciasAlumno(UUID estudianteId, UUID cicloRef) {
        String grupoFilter = "cl.grupo_id IN (SELECT grupo_id FROM ades_inscripciones WHERE estudiante_id = ? AND ciclo_escolar_id = ?)";

        List<Map<String, Object>> res = jdbc.queryForList(
                "SELECT COUNT(*) AS total, " +
                "COUNT(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 END) AS presentes, " +
                "COUNT(CASE WHEN a.estatus_asistencia = 'AUSENTE' THEN 1 END) AS ausentes, " +
                "COUNT(CASE WHEN a.estatus_asistencia = 'TARDE' THEN 1 END) AS tardes " +
                "FROM ades_asistencias a " +
                "JOIN ades_clases cl ON cl.id = a.clase_id " +
                "WHERE a.estudiante_id = ? AND " + grupoFilter,
                estudianteId, estudianteId, cicloRef
        );

        Map<String, Object> resumen = new HashMap<>();
        if (!res.isEmpty()) {
            Map<String, Object> rr = res.get(0);
            long total = ((Number) rr.get("total")).longValue();
            long presentes = ((Number) rr.get("presentes")).longValue();
            long ausentes = ((Number) rr.get("ausentes")).longValue();
            long tardes = ((Number) rr.get("tardes")).longValue();
            resumen.put("total", total);
            resumen.put("presentes", presentes);
            resumen.put("ausentes", ausentes);
            resumen.put("tardes", tardes);
            resumen.put("pct_asistencia", total > 0 ? Math.round(1000.0 * presentes / total) / 10.0 : 0.0);
        }

        List<Map<String, Object>> det = jdbc.queryForList(
                "SELECT cl.fecha_clase AS fecha, " +
                "m.nombre_materia AS materia, " +
                "a.estatus_asistencia AS estado, " +
                "a.observacion " +
                "FROM ades_asistencias a " +
                "JOIN ades_clases cl ON cl.id = a.clase_id " +
                "JOIN ades_materias m ON m.id = cl.materia_id " +
                "WHERE a.estudiante_id = ? AND " + grupoFilter + " " +
                "ORDER BY cl.fecha_clase DESC LIMIT 100",
                estudianteId, estudianteId, cicloRef
        );

        Map<String, Object> response = new HashMap<>();
        response.put("resumen", resumen);
        response.put("detalle", det);
        return response;
    }

    public List<Map<String, Object>> tareasAlumno(UUID estudianteId, UUID cicloRef, boolean soloPendientes) {
        String pendienteFilter = soloPendientes ?
                "AND NOT EXISTS (SELECT 1 FROM ades_tareas_entregas te WHERE te.tarea_id = t.id AND te.estudiante_id = ?) " : "";

        String sql = "SELECT t.id, t.titulo, t.descripcion, " +
                "m.nombre_materia AS materia, " +
                "t.fecha_asignacion, t.fecha_entrega, t.puntaje_maximo, " +
                "te.id AS entrega_id, " +
                "te.fecha_entrega AS fecha_entregado, " +
                "te.es_tarde, te.estatus_entrega, " +
                "ct.calificacion AS calificacion_tarea " +
                "FROM ades_tareas t " +
                "JOIN ades_materias m ON m.id = t.materia_id " +
                "LEFT JOIN ades_tareas_entregas te ON te.tarea_id = t.id AND te.estudiante_id = ? " +
                "LEFT JOIN ades_calificaciones_tareas ct ON ct.tarea_entrega_id = te.id " +
                "WHERE t.is_active = TRUE " +
                "AND t.grupo_id IN (SELECT grupo_id FROM ades_inscripciones WHERE estudiante_id = ? AND ciclo_escolar_id = ?) " +
                pendienteFilter +
                "ORDER BY t.fecha_entrega DESC LIMIT 50";

        List<Object> params = new ArrayList<>();
        params.add(estudianteId);
        params.add(estudianteId);
        params.add(cicloRef);
        if (soloPendientes) params.add(estudianteId);

        return jdbc.queryForList(sql, params.toArray());
    }
}

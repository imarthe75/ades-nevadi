package mx.ades.modules.gradebook.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.util.*;

/** CQRS read-side — no modifica estado, sin lógica de negocio. */
@Service
@RequiredArgsConstructor
public class GradebookQueryService {

    private final JdbcTemplate jdbc;

    /**
     * Resuelve el grupo_id de un registro de calificación de periodo, usado por
     * GradebookController para aplicar el mismo control de acceso docente↔grupo
     * (requireAccesoGrupo) que ya protege al resto de módulos académicos.
     */
    @Transactional(readOnly = true)
    public UUID grupoIdDeCalificacion(UUID calPeriodoId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT grupo_id FROM ades_calificaciones_periodo WHERE id = ?", UUID.class, calPeriodoId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Verifica tutoría activa (padre/madre autenticado) sobre un alumno — replica el
     *  criterio ya usado en PortalFamiliasQueryService#esTutorDeAlumno para no acoplar
     *  este módulo al de portal_familias. */
    @Transactional(readOnly = true)
    public boolean esTutorDeAlumno(String email, UUID alumnoId) {
        if (email == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_tutores_alumnos ta " +
                "JOIN ades_personas p ON p.id = ta.persona_id " +
                "WHERE p.email_personal = ? AND ta.alumno_id = ? AND ta.is_active = TRUE",
                Integer.class, email, alumnoId);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tablaCalificacionesGrupo(UUID periodoId, UUID grupoId, UUID materiaId) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(p.nombre_social, p.nombre) AS nombre,
                   p.apellido_paterno, p.apellido_materno,
                   est.matricula,
                   cp.materia_id,
                   m.nombre_materia,
                   cp.score_por_item,
                   cp.calificacion_calculada,
                   cp.ajuste_manual,
                   cp.calificacion_final,
                   cp.cerrada,
                   cp.fecha_calculo,
                   cp.id AS cal_periodo_id,
                   ne.escala_maxima,
                   ne.minimo_aprobatorio
            FROM ades_inscripciones i
            JOIN ades_estudiantes est ON est.id = i.estudiante_id
            JOIN ades_personas p      ON p.id = est.persona_id
            LEFT JOIN ades_calificaciones_periodo cp
                   ON cp.estudiante_id = i.estudiante_id
                  AND cp.grupo_id = i.grupo_id
                  AND cp.periodo_evaluacion_id = ?
            LEFT JOIN ades_materias m ON m.id = cp.materia_id
            LEFT JOIN ades_grados gr ON gr.id = (
                SELECT g.grado_id FROM ades_grupos g WHERE g.id = i.grupo_id)
            LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
            WHERE i.grupo_id = ? AND i.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>(List.of(periodoId, grupoId));
        if (materiaId != null) { sql.append("AND cp.materia_id = ? "); params.add(materiaId); }
        sql.append("ORDER BY p.apellido_paterno, p.nombre, m.nombre_materia");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> boletaAlumno(UUID alumnoId, UUID periodoId, UUID cicloId) {
        StringBuilder sql = new StringBuilder("""
            SELECT m.nombre_materia,
                   pe.nombre_periodo, pe.numero_periodo,
                   cp.score_por_item,
                   cp.calificacion_calculada,
                   cp.ajuste_manual,
                   cp.calificacion_final,
                   cp.cerrada,
                   ne.escala_maxima,
                   ne.minimo_aprobatorio,
                   (cp.calificacion_final >= ne.minimo_aprobatorio) AS acreditado
            FROM ades_calificaciones_periodo cp
            JOIN ades_materias m           ON m.id = cp.materia_id
            JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
            JOIN ades_grados gr             ON gr.id = (
                SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id)
            JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
            WHERE cp.estudiante_id = ? AND cp.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>(List.of(alumnoId));
        if (periodoId != null) { sql.append("AND cp.periodo_evaluacion_id = ? "); params.add(periodoId); }
        if (cicloId != null)   { sql.append("AND pe.ciclo_escolar_id = ? ");       params.add(cicloId); }
        sql.append("ORDER BY m.nombre_materia, pe.numero_periodo");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> concentradoGrupo(UUID grupoId, UUID periodoId) {
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT COALESCE(p.nombre_social, p.nombre) || ' ' || p.apellido_paterno AS alumno,
                   est.matricula,
                   cp.materia_id,
                   m.nombre_materia,
                   cp.calificacion_final,
                   ne.minimo_aprobatorio,
                   (cp.calificacion_final < ne.minimo_aprobatorio) AS en_riesgo
            FROM ades_calificaciones_periodo cp
            JOIN ades_estudiantes est ON est.id = cp.estudiante_id
            JOIN ades_personas p      ON p.id = est.persona_id
            JOIN ades_materias m      ON m.id = cp.materia_id
            JOIN ades_grados gr       ON gr.id = (
                SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id)
            JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
            WHERE cp.grupo_id = ? AND cp.periodo_evaluacion_id = ? AND cp.is_active = TRUE
            ORDER BY p.apellido_paterno, m.nombre_materia
            """, grupoId, periodoId);

        Map<String, Map<String, Object>> materias = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            String mn = (String) row.get("nombre_materia");
            Double val = row.get("calificacion_final") != null
                    ? ((Number) row.get("calificacion_final")).doubleValue() : null;
            boolean enRiesgo = Boolean.TRUE.equals(row.get("en_riesgo"));

            materias.computeIfAbsent(mn, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("calificaciones", new ArrayList<Double>());
                m.put("en_riesgo", 0);
                return m;
            });
            if (val != null) ((List<Double>) materias.get(mn).get("calificaciones")).add(val);
            if (enRiesgo)   materias.get(mn).put("en_riesgo", (Integer) materias.get(mn).get("en_riesgo") + 1);
        }

        Map<String, Map<String, Object>> promedios = new LinkedHashMap<>();
        for (var entry : materias.entrySet()) {
            List<Double> cals = (List<Double>) entry.getValue().get("calificaciones");
            double sum = cals.stream().mapToDouble(Double::doubleValue).sum();
            Double prom = cals.isEmpty() ? null : Math.round((sum / cals.size()) * 100.0) / 100.0;
            promedios.put(entry.getKey(), Map.of(
                    "promedio", prom != null ? prom : 0.0,
                    "en_riesgo", entry.getValue().get("en_riesgo")));
        }

        Map<String, Object> res = new HashMap<>();
        res.put("detalle", data);
        res.put("promedios_por_materia", promedios);
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> coberturaCurricular(UUID grupoId, UUID materiaId) {
        StringBuilder sql = new StringBuilder("""
            SELECT tm.id, tm.nombre_tema, tm.orden,
                   m.nombre_materia,
                   COUNT(t.id) AS num_actividades,
                   COUNT(t.id) > 0 AS tiene_evidencia
            FROM ades_temas tm
            JOIN ades_materias m ON m.id = tm.materia_id
            LEFT JOIN ades_tareas t ON t.tema_id = tm.id AND t.grupo_id = ? AND t.is_active = TRUE
            WHERE tm.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>(List.of(grupoId));
        if (materiaId != null) { sql.append("AND tm.materia_id = ? "); params.add(materiaId); }
        sql.append("GROUP BY tm.id, tm.nombre_tema, tm.orden, m.nombre_materia ");
        sql.append("ORDER BY m.nombre_materia, tm.orden");

        List<Map<String, Object>> temas = jdbc.queryForList(sql.toString(), params.toArray());
        long conEvidencia = temas.stream().filter(r -> Boolean.TRUE.equals(r.get("tiene_evidencia"))).count();
        double pct = temas.isEmpty() ? 0.0
                : Math.round(((double) conEvidencia / temas.size() * 100.0) * 10.0) / 10.0;

        Map<String, Object> res = new HashMap<>();
        res.put("total_temas", temas.size());
        res.put("con_evidencia", conEvidencia);
        res.put("sin_evidencia", temas.size() - conEvidencia);
        res.put("pct_cobertura", pct);
        res.put("temas", temas);
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detectarInconsistencias(UUID grupoId, UUID periodoId) {
        StringBuilder sql = new StringBuilder("""
            SELECT est.id AS estudiante_id,
                   COALESCE(per.nombre_social, per.nombre) AS nombre,
                   per.apellido_paterno, est.matricula,
                   cp.calificacion_final, cp.es_acreditado,
                   COUNT(e.id) FILTER (WHERE e.calificacion_obtenida IS NOT NULL) AS entregas_calificadas,
                   COUNT(t.id) AS total_actividades
            FROM ades_calificaciones_periodo cp
            JOIN ades_estudiantes est ON est.id = cp.estudiante_id
            JOIN ades_personas per    ON per.id = est.persona_id
            LEFT JOIN ades_tareas t   ON t.grupo_id = ? AND t.is_active = TRUE
            LEFT JOIN ades_tareas_entregas e ON e.tarea_id = t.id AND e.estudiante_id = est.id
            WHERE cp.grupo_id = ? AND cp.is_active = TRUE AND cp.calificacion_final IS NOT NULL
            """);

        List<Object> params = new ArrayList<>(List.of(grupoId, grupoId));
        if (periodoId != null) { sql.append("AND cp.periodo_evaluacion_id = ? "); params.add(periodoId); }
        sql.append("""
            GROUP BY est.id, per.nombre, per.apellido_paterno, est.matricula,
                     cp.calificacion_final, cp.es_acreditado
            HAVING cp.es_acreditado = TRUE
               AND COUNT(e.id) FILTER (WHERE e.calificacion_obtenida IS NOT NULL) = 0
            ORDER BY per.apellido_paterno, per.nombre
            """);

        List<Map<String, Object>> casos = jdbc.queryForList(sql.toString(), params.toArray());
        return Map.of(
                "grupo_id", grupoId.toString(),
                "total_inconsistencias", casos.size(),
                "descripcion", "Alumnos con calificación aprobatoria pero sin entregas calificadas",
                "casos", casos);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> candidatosExtraordinario(UUID grupoId, UUID periodoId) {
        StringBuilder sql = new StringBuilder("""
            SELECT est.id AS estudiante_id,
                   COALESCE(per.nombre_social, per.nombre) AS nombre,
                   per.apellido_paterno, est.matricula,
                   cp.calificacion_final, cp.calificacion_calculada,
                   cp.es_acreditado, ne.minimo_aprobatorio, pe.nombre_periodo
            FROM ades_calificaciones_periodo cp
            JOIN ades_estudiantes est ON est.id = cp.estudiante_id
            JOIN ades_personas per    ON per.id = est.persona_id
            JOIN ades_grupos g        ON g.id = cp.grupo_id
            JOIN ades_grados gr       ON gr.id = g.grado_id
            JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
            LEFT JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
            WHERE cp.grupo_id = ? AND cp.is_active = TRUE
              AND cp.es_acreditado = FALSE AND cp.calificacion_final IS NOT NULL
            """);

        List<Object> params = new ArrayList<>(List.of(grupoId));
        if (periodoId != null) { sql.append("AND cp.periodo_evaluacion_id = ? "); params.add(periodoId); }
        sql.append("ORDER BY cp.calificacion_final ASC, per.apellido_paterno");

        List<Map<String, Object>> candidatos = jdbc.queryForList(sql.toString(), params.toArray());
        return Map.of(
                "grupo_id", grupoId.toString(),
                "total_candidatos", candidatos.size(),
                "descripcion", "Alumnos con calificación reprobatoria — candidatos a examen extraordinario",
                "candidatos", candidatos);
    }

    /** Recalculado masivo usando arrays PostgreSQL para evitar N+1. */
    @Transactional
    public int recalcularPeriodo(UUID periodoId, UUID grupoId, UUID materiaId) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT i.estudiante_id, i.grupo_id, ad.materia_id
            FROM ades_inscripciones i
            JOIN ades_grupos g ON g.id = i.grupo_id
            JOIN ades_asignaciones_docentes ad ON ad.grupo_id = i.grupo_id
            WHERE ad.ciclo_escolar_id = (
                SELECT ciclo_escolar_id FROM ades_periodos_evaluacion WHERE id = ?)
              AND i.is_active = TRUE AND g.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>(List.of(periodoId));
        if (grupoId != null) { sql.append("AND i.grupo_id = ? "); params.add(grupoId); }

        List<Map<String, Object>> combos = jdbc.queryForList(sql.toString(), params.toArray());

        List<UUID> eids = new ArrayList<>(), gids = new ArrayList<>(),
                   mids = new ArrayList<>(), pids = new ArrayList<>();
        for (Map<String, Object> c : combos) {
            UUID mid = (UUID) c.get("materia_id");
            if (materiaId != null && !materiaId.equals(mid)) continue;
            eids.add((UUID) c.get("estudiante_id"));
            gids.add((UUID) c.get("grupo_id"));
            mids.add(mid);
            pids.add(periodoId);
        }

        if (eids.isEmpty()) return 0;

        jdbc.execute((Connection con) -> {
            Array ae = con.createArrayOf("uuid", eids.toArray());
            Array ag = con.createArrayOf("uuid", gids.toArray());
            Array am = con.createArrayOf("uuid", mids.toArray());
            Array ap = con.createArrayOf("uuid", pids.toArray());
            jdbc.update(
                "SELECT calcular_calificacion_periodo(e,g,m,p) " +
                "FROM unnest(?,?,?,?) AS t(e uuid,g uuid,m uuid,p uuid)",
                ae, ag, am, ap);
            return null;
        });

        return eids.size();
    }
}

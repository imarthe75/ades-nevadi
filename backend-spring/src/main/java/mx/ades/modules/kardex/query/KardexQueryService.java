package mx.ades.modules.kardex.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kardex / historial académico UAEMEX (bachillerato CBU, escuela incorporada).
 * Escala 0-10, mínima aprobatoria 6.0 (RGEMS UAEMEX).
 *
 * Registra, por materia, la calificación de ORDINARIO (periodo FINAL) y, si la hubo,
 * la de EXTRAORDINARIO; calcula la calificación DEFINITIVA y el estatus:
 *   - definitiva = ordinario si ordinario >= 6; si no y hay extraordinario, el extra.
 *   - acreditada = definitiva >= 6.0.
 * Los parciales no se incluyen (están en escala distinta y son intermedios).
 */
@Service
public class KardexQueryService {

    private static final BigDecimal MIN_APROBATORIA = new BigDecimal("6.0");

    private final NamedParameterJdbcTemplate jdbc;

    public KardexQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> kardex(UUID estudianteId, UUID cicloId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("est", estudianteId.toString())
                .addValue("ciclo", cicloId != null ? cicloId.toString() : null);

        Map<String, Object> header = jdbc.query(
                """
                SELECT trim(p.nombre || ' ' || p.apellido_paterno || ' ' || COALESCE(p.apellido_materno,'')) AS alumno,
                       e.matricula, p.curp,
                       gr.nombre_grado AS semestre, g.nombre_grupo AS grupo,
                       pl.nombre_plantel AS plantel, c.nombre_ciclo AS ciclo
                FROM ades_estudiantes e
                JOIN ades_personas p           ON p.id = e.persona_id
                JOIN ades_inscripciones i      ON i.estudiante_id = e.id AND i.is_active = TRUE
                JOIN ades_ciclos_escolares c   ON c.id = i.ciclo_escolar_id
                JOIN ades_grupos g             ON g.id = i.grupo_id
                JOIN ades_grados gr            ON gr.id = g.grado_id
                JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
                LEFT JOIN ades_planteles pl    ON pl.id = gr.plantel_id
                WHERE e.id = CAST(:est AS uuid)
                  AND n.autoridad_educativa = 'UAEMEX'
                  AND (CAST(:ciclo AS uuid) IS NULL AND c.es_vigente OR c.id = CAST(:ciclo AS uuid))
                LIMIT 1
                """,
                p, rs -> rs.next() ? Map.of(
                        "alumno",   rs.getString("alumno"),
                        "matricula", rs.getString("matricula") != null ? rs.getString("matricula") : "—",
                        "curp",     rs.getString("curp") != null ? rs.getString("curp") : "—",
                        "semestre", rs.getString("semestre"),
                        "grupo",    rs.getString("grupo"),
                        "plantel",  rs.getString("plantel") != null ? rs.getString("plantel") : "—",
                        "ciclo",    rs.getString("ciclo")
                ) : null);

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT m.nombre_materia AS materia, m.clave_materia AS clave,
                       MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'FINAL')          AS ordinario,
                       MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'EXTRAORDINARIO') AS extraordinario,
                       COALESCE(SUM(cp.inasistencias) FILTER (WHERE pe.tipo_periodo <> 'EXTRAORDINARIO'), 0) AS inasistencias
                FROM ades_calificaciones_periodo cp
                JOIN ades_materias m            ON m.id = cp.materia_id
                JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
                JOIN ades_inscripciones i       ON i.estudiante_id = cp.estudiante_id
                                                AND i.grupo_id = cp.grupo_id AND i.is_active = TRUE
                JOIN ades_ciclos_escolares c    ON c.id = i.ciclo_escolar_id
                JOIN ades_grupos g              ON g.id = i.grupo_id
                JOIN ades_grados gr             ON gr.id = g.grado_id
                JOIN ades_niveles_educativos n  ON n.id = gr.nivel_educativo_id
                WHERE cp.estudiante_id = CAST(:est AS uuid)
                  AND n.autoridad_educativa = 'UAEMEX'
                  AND (CAST(:ciclo AS uuid) IS NULL AND c.es_vigente OR c.id = CAST(:ciclo AS uuid))
                GROUP BY m.nombre_materia, m.clave_materia
                ORDER BY m.nombre_materia
                """,
                p);

        List<Map<String, Object>> materias = new ArrayList<>();
        BigDecimal suma = BigDecimal.ZERO;
        int conAcred = 0, total = 0, reprobadas = 0;

        for (Map<String, Object> r : rows) {
            BigDecimal ordinario = toBd(r.get("ordinario"));
            BigDecimal extra     = toBd(r.get("extraordinario"));
            BigDecimal definitiva = null;
            if (ordinario != null && ordinario.compareTo(MIN_APROBATORIA) >= 0) {
                definitiva = ordinario;
            } else if (extra != null) {
                definitiva = extra;
            } else {
                definitiva = ordinario;
            }
            boolean acreditada = definitiva != null && definitiva.compareTo(MIN_APROBATORIA) >= 0;

            if (definitiva != null) { suma = suma.add(definitiva); total++; }
            if (acreditada) conAcred++; else if (definitiva != null) reprobadas++;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materia", r.get("materia"));
            m.put("clave", r.get("clave"));
            m.put("ordinario", ordinario);
            m.put("extraordinario", extra);
            m.put("definitiva", definitiva);
            m.put("acreditada", acreditada);
            m.put("inasistencias", r.get("inasistencias"));
            materias.add(m);
        }

        BigDecimal promedio = total > 0
                ? suma.divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP) : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alumno", header);
        out.put("escala", "0 a 10 — mínima aprobatoria 6.0 (RGEMS UAEMEX)");
        out.put("materias", materias);
        out.put("promedio_general", promedio);
        out.put("materias_acreditadas", conAcred);
        out.put("materias_reprobadas", reprobadas);
        out.put("total_materias", total);
        return out;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}

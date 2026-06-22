package mx.ades.modules.acta.query;

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
 * Acta de evaluación UAEMEX (preparatoria incorporada, CBU).
 * Documento oficial por grupo×materia que el docente firma y entrega a Control Escolar.
 * Incluye ordinario (FINAL) y extraordinario por alumno; calcula definitiva RGEMS.
 */
@Service
public class ActaQueryService {

    private static final BigDecimal MIN_APROBATORIA = new BigDecimal("6.0");

    private final NamedParameterJdbcTemplate jdbc;

    public ActaQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Grupos UAEMEX vigentes (para LOV). Filtrable por plantel. */
    public List<Map<String, Object>> gruposUaemex(UUID plantelId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("plantel", plantelId != null ? plantelId.toString() : null);
        return jdbc.queryForList(
                """
                SELECT g.id, g.nombre_grupo,
                       gr.nombre_grado    AS semestre,
                       gr.numero_grado,
                       n.nombre_nivel     AS nivel,
                       pl.nombre_plantel  AS plantel,
                       pl.id              AS plantel_id,
                       c.nombre_ciclo     AS ciclo
                FROM ades_grupos g
                JOIN ades_grados gr            ON gr.id = g.grado_id
                JOIN ades_niveles_educativos n  ON n.id  = gr.nivel_educativo_id
                JOIN ades_ciclos_escolares c    ON c.id  = g.ciclo_escolar_id
                LEFT JOIN ades_planteles pl     ON pl.id = gr.plantel_id
                WHERE c.sistema_educativo = 'UAEMEX'
                  AND c.es_vigente = true
                  AND g.is_active   = true
                  AND (CAST(:plantel AS uuid) IS NULL OR gr.plantel_id = CAST(:plantel AS uuid))
                ORDER BY pl.nombre_plantel, gr.numero_grado, g.nombre_grupo
                """,
                p);
    }

    /** Materias impartidas en un grupo (con el docente asignado). */
    public List<Map<String, Object>> materiasGrupo(UUID grupoId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("grupo", grupoId.toString());
        return jdbc.queryForList(
                """
                SELECT DISTINCT ON (m.id)
                       m.id, m.nombre_materia, m.clave_materia,
                       trim(pr_p.nombre||' '||pr_p.apellido_paterno
                            ||' '||COALESCE(pr_p.apellido_materno,'')) AS profesor
                FROM ades_clases cl
                JOIN ades_materias m   ON m.id  = cl.materia_id
                LEFT JOIN ades_profesores pr ON pr.id = cl.profesor_id
                LEFT JOIN ades_personas  pr_p ON pr_p.id = pr.persona_id
                WHERE cl.grupo_id = CAST(:grupo AS uuid)
                  AND cl.is_active = true
                ORDER BY m.id, m.nombre_materia
                """,
                p);
    }

    /** Periodos FINAL y EXTRAORDINARIO del ciclo del grupo. */
    public List<Map<String, Object>> periodosGrupo(UUID grupoId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("grupo", grupoId.toString());
        return jdbc.queryForList(
                """
                SELECT pe.id, pe.nombre_periodo, pe.tipo_periodo, pe.numero_periodo
                FROM ades_periodos_evaluacion pe
                JOIN ades_grupos g ON g.ciclo_escolar_id = pe.ciclo_escolar_id
                WHERE g.id = CAST(:grupo AS uuid)
                  AND pe.tipo_periodo IN ('FINAL','EXTRAORDINARIO')
                ORDER BY pe.numero_periodo
                """,
                p);
    }

    /**
     * Genera el acta de evaluación completa para grupo+materia.
     * Incluye cabecera (grupo, semestre, plantel, ciclo, materia, docente),
     * la lista de alumnos con sus calificaciones y el resumen estadístico.
     */
    public Map<String, Object> acta(UUID grupoId, UUID materiaId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("grupo",   grupoId.toString())
                .addValue("materia", materiaId.toString());

        // ── cabecera ─────────────────────────────────────────────────────────
        Map<String, Object> cabecera = jdbc.query(
                """
                SELECT g.nombre_grupo,
                       gr.nombre_grado  AS semestre,
                       gr.numero_grado,
                       pl.nombre_plantel AS plantel,
                       c.nombre_ciclo   AS ciclo,
                       m.nombre_materia, m.clave_materia,
                       trim(pr_p.nombre||' '||pr_p.apellido_paterno
                            ||' '||COALESCE(pr_p.apellido_materno,'')) AS docente
                FROM ades_grupos g
                JOIN ades_grados gr            ON gr.id = g.grado_id
                JOIN ades_ciclos_escolares c    ON c.id  = g.ciclo_escolar_id
                LEFT JOIN ades_planteles pl     ON pl.id = gr.plantel_id
                CROSS JOIN (
                  SELECT m2.nombre_materia, m2.clave_materia,
                         cl.profesor_id
                  FROM ades_clases cl
                  JOIN ades_materias m2 ON m2.id = cl.materia_id
                  WHERE cl.grupo_id  = CAST(:grupo   AS uuid)
                    AND cl.materia_id = CAST(:materia AS uuid)
                    AND cl.is_active  = true
                  LIMIT 1
                ) AS m(nombre_materia, clave_materia, profesor_id)
                LEFT JOIN ades_profesores pr  ON pr.id  = m.profesor_id
                LEFT JOIN ades_personas  pr_p ON pr_p.id = pr.persona_id
                WHERE g.id = CAST(:grupo AS uuid)
                LIMIT 1
                """,
                p,
                rs -> rs.next() ? Map.of(
                        "grupo",          rs.getString("nombre_grupo"),
                        "semestre",       rs.getString("semestre"),
                        "numero_grado",   rs.getInt("numero_grado"),
                        "plantel",        rs.getString("plantel") != null ? rs.getString("plantel") : "—",
                        "ciclo",          rs.getString("ciclo"),
                        "materia",        rs.getString("nombre_materia"),
                        "clave_materia",  rs.getString("clave_materia") != null ? rs.getString("clave_materia") : "—",
                        "docente",        rs.getString("docente") != null ? rs.getString("docente") : "—"
                ) : null);

        if (cabecera == null) {
            return Map.of("error", "Grupo o materia no encontrados");
        }

        // ── alumnos ───────────────────────────────────────────────────────────
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT
                  trim(p.apellido_paterno||' '||COALESCE(p.apellido_materno,'')||', '||p.nombre) AS alumno,
                  e.matricula,
                  p.curp,
                  MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'FINAL')          AS ordinario,
                  MAX(cp.calificacion_final) FILTER (WHERE pe.tipo_periodo = 'EXTRAORDINARIO') AS extraordinario,
                  COALESCE(SUM(cp.inasistencias)
                             FILTER (WHERE pe.tipo_periodo <> 'EXTRAORDINARIO'), 0)            AS inasistencias
                FROM ades_inscripciones i
                JOIN ades_estudiantes e ON e.id = i.estudiante_id
                JOIN ades_personas    p ON p.id = e.persona_id
                LEFT JOIN ades_calificaciones_periodo cp
                  ON  cp.grupo_id      = i.grupo_id
                  AND cp.estudiante_id = i.estudiante_id
                  AND cp.materia_id    = CAST(:materia AS uuid)
                LEFT JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
                  AND pe.tipo_periodo IN ('FINAL','EXTRAORDINARIO')
                WHERE i.grupo_id  = CAST(:grupo AS uuid)
                  AND i.is_active = true
                GROUP BY e.id, p.apellido_paterno, p.apellido_materno, p.nombre,
                         e.matricula, p.curp
                ORDER BY p.apellido_paterno, p.apellido_materno, p.nombre
                """,
                p);

        List<Map<String, Object>> alumnos = new ArrayList<>();
        int acreditados = 0, reprobados = 0, sinCalif = 0;
        BigDecimal suma = BigDecimal.ZERO;
        int conDef = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            BigDecimal ordinario = toBd(r.get("ordinario"));
            BigDecimal extra     = toBd(r.get("extraordinario"));
            BigDecimal definitiva;

            if (ordinario == null && extra == null) {
                definitiva = null;
                sinCalif++;
            } else if (ordinario != null && ordinario.compareTo(MIN_APROBATORIA) >= 0) {
                definitiva = ordinario;
            } else if (extra != null) {
                definitiva = extra;
            } else {
                definitiva = ordinario;
            }

            boolean acreditada = definitiva != null && definitiva.compareTo(MIN_APROBATORIA) >= 0;
            if (definitiva != null) {
                suma = suma.add(definitiva);
                conDef++;
                if (acreditada) acreditados++; else reprobados++;
            }

            Map<String, Object> a = new LinkedHashMap<>();
            a.put("num",            i + 1);
            a.put("alumno",         r.get("alumno"));
            a.put("matricula",      r.get("matricula") != null ? r.get("matricula") : "—");
            a.put("curp",           r.get("curp") != null ? r.get("curp") : "—");
            a.put("ordinario",      ordinario);
            a.put("extraordinario", extra);
            a.put("definitiva",     definitiva);
            a.put("acreditada",     acreditada);
            a.put("inasistencias",  r.get("inasistencias"));
            alumnos.add(a);
        }

        BigDecimal promedioGrupal = conDef > 0
                ? suma.divide(BigDecimal.valueOf(conDef), 1, RoundingMode.HALF_UP) : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cabecera",         cabecera);
        out.put("alumnos",          alumnos);
        out.put("total_alumnos",    alumnos.size());
        out.put("acreditados",      acreditados);
        out.put("reprobados",       reprobados);
        out.put("sin_calificacion", sinCalif);
        out.put("promedio_grupal",  promedioGrupal);
        out.put("escala",           "0 a 10 — mínima aprobatoria 6.0 (RGEMS UAEMEX)");
        return out;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}

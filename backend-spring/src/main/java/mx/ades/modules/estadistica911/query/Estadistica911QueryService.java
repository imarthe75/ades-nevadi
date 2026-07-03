package mx.ades.modules.estadistica911.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pre-cálculo del Formato 911 de inicio de cursos (educación básica SEP).
 * Genera las cifras que el personal transcribe a la plataforma oficial f911:
 *   - IV.1: alumnado por grado, sexo, tipo de ingreso (nuevo/repetidor) y edad.
 *   - IV.2: número de grupos por grado.
 * Solo niveles SEP (primaria/secundaria). Scoping por plantel.
 *
 * "Repetidor" se infiere del historial: existe una inscripción previa del mismo
 * estudiante en el mismo número de grado en un ciclo anterior.
 *
 * Edad: cumplida al 31 de diciembre del año de inicio del ciclo (criterio 911).
 */
@Service
public class Estadistica911QueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public Estadistica911QueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** IV.1 — matriz desglosada (una fila por nivel/grado/sexo/ingreso/edad). */
    public List<Map<String, Object>> matriz(UUID plantelId, UUID cicloId) {
        String sql = """
            SELECT n.nombre_nivel                           AS nivel,
                   gr.numero_grado                          AS grado,
                   p.genero                                 AS sexo,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM ades_inscripciones i2
                       JOIN ades_grupos g2  ON g2.id  = i2.grupo_id
                       JOIN ades_grados gr2 ON gr2.id = g2.grado_id
                       JOIN ades_ciclos_escolares c2 ON c2.id = i2.ciclo_escolar_id
                       WHERE i2.estudiante_id = i.estudiante_id
                         AND gr2.numero_grado = gr.numero_grado
                         AND c2.fecha_inicio  < c.fecha_inicio
                   ) THEN 'REPETIDOR' ELSE 'NUEVO_INGRESO' END AS tipo_ingreso,
                   date_part('year', age(
                       make_date(date_part('year', c.fecha_inicio)::int, 12, 31),
                       p.fecha_nacimiento))::int             AS edad,
                   COUNT(*)                                 AS alumnos
            FROM ades_inscripciones i
            JOIN ades_grupos g            ON g.id  = i.grupo_id
            JOIN ades_grados gr           ON gr.id = g.grado_id
            JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
            JOIN ades_ciclos_escolares c  ON c.id = i.ciclo_escolar_id
            JOIN ades_estudiantes e       ON e.id = i.estudiante_id
            JOIN ades_personas p          ON p.id = e.persona_id
            WHERE i.is_active = TRUE
              AND n.autoridad_educativa = 'SEP'
              AND (CAST(:ciclo AS uuid) IS NULL AND c.es_vigente
                   OR c.id = CAST(:ciclo AS uuid))
              AND (CAST(:plantel AS uuid) IS NULL OR gr.plantel_id = CAST(:plantel AS uuid))
            GROUP BY n.nombre_nivel, gr.numero_grado, p.genero, tipo_ingreso, edad
            ORDER BY n.nombre_nivel, gr.numero_grado, p.genero, edad
            """;
        return jdbc.queryForList(sql, params(plantelId, cicloId));
    }

    /** IV.2 — número de grupos por grado. */
    public List<Map<String, Object>> gruposPorGrado(UUID plantelId, UUID cicloId) {
        String sql = """
            SELECT n.nombre_nivel        AS nivel,
                   gr.numero_grado       AS grado,
                   COUNT(DISTINCT g.id)  AS grupos
            FROM ades_grupos g
            JOIN ades_grados gr           ON gr.id = g.grado_id
            JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
            JOIN ades_ciclos_escolares c  ON c.id = g.ciclo_escolar_id
            WHERE n.autoridad_educativa = 'SEP'
              AND (CAST(:ciclo AS uuid) IS NULL AND c.es_vigente
                   OR c.id = CAST(:ciclo AS uuid))
              AND (CAST(:plantel AS uuid) IS NULL OR gr.plantel_id = CAST(:plantel AS uuid))
            GROUP BY n.nombre_nivel, gr.numero_grado
            ORDER BY n.nombre_nivel, gr.numero_grado
            """;
        return jdbc.queryForList(sql, params(plantelId, cicloId));
    }

    /**
     * Sección IX — Alumnado con discapacidad por tipo, grado y sexo.
     * Fuente: {@code ades_condiciones_cronicas} con {@code tipo_condicion LIKE 'DISCAPACIDAD_%'}.
     * Solo niveles SEP. Una fila por nivel/grado/tipo_discapacidad/sexo con su conteo.
     */
    public List<Map<String, Object>> discapacidadPorGrado(UUID plantelId, UUID cicloId) {
        String sql = """
            SELECT n.nombre_nivel                   AS nivel,
                   gr.numero_grado                  AS grado,
                   cc.tipo_condicion                AS tipo_discapacidad,
                   p.genero                         AS sexo,
                   COUNT(DISTINCT e.id)             AS alumnos
            FROM ades_condiciones_cronicas cc
            JOIN ades_estudiantes e         ON e.id  = cc.alumno_id
            JOIN ades_inscripciones i       ON i.estudiante_id = e.id AND i.is_active = true
            JOIN ades_grupos g              ON g.id  = i.grupo_id
            JOIN ades_grados gr             ON gr.id = g.grado_id
            JOIN ades_niveles_educativos n  ON n.id  = gr.nivel_educativo_id
            JOIN ades_ciclos_escolares c    ON c.id  = i.ciclo_escolar_id
            JOIN ades_personas p            ON p.id  = e.persona_id
            WHERE cc.tipo_condicion LIKE 'DISCAPACIDAD_%'
              AND cc.activa = true
              AND n.autoridad_educativa = 'SEP'
              AND (CAST(:ciclo AS uuid) IS NULL AND c.es_vigente
                   OR c.id = CAST(:ciclo AS uuid))
              AND (CAST(:plantel AS uuid) IS NULL OR gr.plantel_id = CAST(:plantel AS uuid))
            GROUP BY n.nombre_nivel, gr.numero_grado, cc.tipo_condicion, p.genero
            ORDER BY n.nombre_nivel, gr.numero_grado, cc.tipo_condicion, p.genero
            """;
        return jdbc.queryForList(sql, params(plantelId, cicloId));
    }

    private MapSqlParameterSource params(UUID plantelId, UUID cicloId) {
        return new MapSqlParameterSource()
                .addValue("plantel", plantelId != null ? plantelId.toString() : null)
                .addValue("ciclo",   cicloId   != null ? cicloId.toString()   : null);
    }
}

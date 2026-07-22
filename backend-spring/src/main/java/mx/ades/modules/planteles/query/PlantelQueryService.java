package mx.ades.modules.planteles.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.planteles.Plantel;
import mx.ades.modules.planteles.PlantelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PlantelQueryService {

    private final JdbcTemplate jdbc;
    private final PlantelRepository repository;

    public List<Map<String, Object>> stats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Plantel p : repository.findAll()) {
            if (!Boolean.TRUE.equals(p.getIsActive())) continue;
            UUID pid = p.getId();

            Long totalAlumnos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_estudiantes WHERE plantel_id = ? AND is_active = true",
                Long.class, pid);
            Long totalProfesores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_profesores WHERE plantel_id = ? AND is_active = true",
                Long.class, pid);
            Long totalGrupos = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT g.id) FROM ades_grupos g " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "JOIN ades_ciclos_escolares c ON c.id = g.ciclo_escolar_id " +
                "WHERE gr.plantel_id = ? AND g.is_active = true AND c.es_vigente = true",
                Long.class, pid);
            List<Map<String, Object>> niveles = jdbc.queryForList(
                "SELECT n.id AS nivel_educativo_id, n.nombre_nivel, " +
                "COUNT(DISTINCT gr.id) AS grados, COUNT(DISTINCT g.id) AS grupos_activos " +
                "FROM ades_niveles_educativos n " +
                "JOIN ades_grados gr ON gr.nivel_educativo_id = n.id " +
                // El conteo de grupos por nivel DEBE restringirse al ciclo vigente igual que
                // total_grupos (arriba); antes la condición es_vigente vivía solo en el ON del
                // LEFT JOIN de c, así que COUNT(g.id) sumaba grupos de TODOS los ciclos
                // (histórico incluido) y el desglose no cuadraba con el total del plantel.
                "LEFT JOIN ades_grupos g ON g.grado_id = gr.id AND g.is_active = true " +
                "  AND EXISTS (SELECT 1 FROM ades_ciclos_escolares c " +
                "              WHERE c.id = g.ciclo_escolar_id AND c.es_vigente = true) " +
                "WHERE gr.plantel_id = ? AND n.is_active = true " +
                "GROUP BY n.id, n.nombre_nivel ORDER BY n.nombre_nivel",
                pid);

            result.add(Map.of(
                "id", pid,
                "nombre_plantel", p.getNombrePlantel(),
                "clave_ct", p.getClaveCt() != null ? p.getClaveCt() : "",
                "total_alumnos", totalAlumnos != null ? totalAlumnos : 0L,
                "total_profesores", totalProfesores != null ? totalProfesores : 0L,
                "total_grupos", totalGrupos != null ? totalGrupos : 0L,
                "niveles", niveles
            ));
        }
        return result;
    }

    public List<Map<String, Object>> nivelesPorPlantel(UUID plantelId) {
        return jdbc.queryForList(
            "SELECT DISTINCT ne.id, ne.nombre_nivel, ne.autoridad_educativa, " +
            "ne.tipo_ciclo, ne.num_periodos_eval, ne.escala_maxima, ne.minimo_aprobatorio " +
            "FROM ades_niveles_educativos ne " +
            "JOIN ades_grados gr ON gr.nivel_educativo_id = ne.id " +
            "WHERE gr.plantel_id = ? AND gr.is_active = TRUE AND ne.is_active = TRUE " +
            "ORDER BY ne.nombre_nivel", plantelId);
    }

    /** Claves oficiales (CCT SEP / incorporación UAEMEX) por nivel educativo del plantel. */
    public List<Map<String, Object>> clavesPorPlantel(UUID plantelId) {
        return jdbc.queryForList(
            "SELECT c.id, c.nivel_educativo_id, ne.nombre_nivel, c.tipo_clave, c.clave, " +
            "c.vigente_desde, c.observaciones " +
            "FROM ades_plantel_nivel_clave c " +
            "JOIN ades_niveles_educativos ne ON ne.id = c.nivel_educativo_id " +
            "WHERE c.plantel_id = ? AND c.is_active = TRUE " +
            "ORDER BY ne.nombre_nivel, c.tipo_clave", plantelId);
    }
}

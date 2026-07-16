package mx.ades.modules.eval_docente.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador JDBC que implementa {@link EvalDocenteRepositoryPort}.
 * <p>Persiste en {@code ades_evaluacion_docente} y {@code ades_eval_docente_criterios}.
 * El recálculo de calificación global usa promedio ponderado sobre {@code ades_criterios_eval_docente}.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class EvalDocentePersistenceAdapter implements EvalDocenteRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insertEvaluacion(CrearEvaluacionUseCase.Command cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_evaluacion_docente " +
            "(id, profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador, comentarios, " +
            "estatus, usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'BORRADOR', ?, ?)",
            id, cmd.profesorId(), cmd.cicloEscolarId(), cmd.evaluadorId(),
            cmd.tipoEvaluador(), cmd.comentarios(), cmd.usuario(), cmd.usuario());
        return id;
    }

    @Override
    public Map<String, Object> fetchEvaluacion(UUID evalId) {
        return jdbc.queryForMap(
            "SELECT id, profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador, " +
            "fecha_evaluacion, calificacion_global, comentarios, estatus " +
            "FROM ades_evaluacion_docente WHERE id = ?", evalId);
    }

    @Override
    public Optional<String> findEstatus(UUID evalId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT estatus FROM ades_evaluacion_docente WHERE id = ?", evalId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0).get("estatus"));
    }

    @Override
    public void upsertCriterio(UUID evalId, GuardarCriteriosUseCase.CriterioCalificacion criterio) {
        jdbc.update(
            "INSERT INTO ades_eval_docente_criterios (evaluacion_id, criterio_id, calificacion, observacion) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (evaluacion_id, criterio_id) " +
            "DO UPDATE SET calificacion = EXCLUDED.calificacion, observacion = EXCLUDED.observacion",
            evalId, criterio.criterioId(), criterio.calificacion(), criterio.observacion());
    }

    @Override
    public void recalcularGlobal(UUID evalId) {
        jdbc.update(
            "UPDATE ades_evaluacion_docente " +
            "SET calificacion_global = (" +
            "  SELECT ROUND(SUM(edc.calificacion * cr.peso_porcentual) / SUM(cr.peso_porcentual), 2) " +
            "  FROM ades_eval_docente_criterios edc " +
            "  JOIN ades_criterios_eval_docente cr ON cr.id = edc.criterio_id " +
            "  WHERE edc.evaluacion_id = ?" +
            ") WHERE id = ?",
            evalId, evalId);
    }

    @Override
    public int enviar(UUID evalId, String usuario) {
        return jdbc.update(
            "UPDATE ades_evaluacion_docente SET estatus = 'ENVIADA', usuario_modificacion = ? " +
            "WHERE id = ? AND estatus = 'BORRADOR'",
            usuario, evalId);
    }

    @Override
    public List<Map<String, Object>> listarCriterios() {
        return jdbc.queryForList(
            "SELECT id, nombre_criterio, descripcion, categoria, peso_porcentual, escala_min, escala_max " +
            "FROM ades_criterios_eval_docente WHERE is_active = TRUE ORDER BY categoria, nombre_criterio");
    }

    @Override
    public Map<String, Object> resumenProfesor(UUID profesorId, UUID cicloId) {
        // Build dynamic WHERE — cicloId is optional
        List<Object> baseParams = new ArrayList<>();
        baseParams.add(profesorId);
        String cicloFilter = "";
        if (cicloId != null) {
            cicloFilter = "AND ciclo_escolar_id = ? ";
            baseParams.add(cicloId);
        }

        // Summary per tipo_evaluador
        String sumSql =
            "SELECT tipo_evaluador, " +
            "AVG(calificacion_global) AS promedio_global, " +
            "COUNT(*) AS total_evaluaciones, " +
            "MAX(fecha_evaluacion)::text AS ultima_fecha " +
            "FROM ades_evaluacion_docente " +
            "WHERE profesor_id = ? " + cicloFilter +
            "AND is_active = TRUE AND estatus != 'BORRADOR' " +
            "GROUP BY tipo_evaluador";
        List<Map<String, Object>> sumRows = jdbc.queryForList(sumSql, baseParams.toArray());

        List<Map<String, Object>> porTipo = new ArrayList<>();
        double sumaPromedios = 0.0;
        for (Map<String, Object> r : sumRows) {
            double prom = r.get("promedio_global") != null
                    ? ((Number) r.get("promedio_global")).doubleValue() : 0.0;
            double roundedProm = Math.round(prom * 100.0) / 100.0;
            Map<String, Object> item = new HashMap<>();
            item.put("tipo_evaluador",     r.get("tipo_evaluador"));
            item.put("promedio_global",    roundedProm);
            item.put("total_evaluaciones", ((Number) r.get("total_evaluaciones")).longValue());
            item.put("ultima_fecha",       r.get("ultima_fecha") != null
                    ? r.get("ultima_fecha").toString() : null);
            porTipo.add(item);
            sumaPromedios += roundedProm;
        }

        // Individual evaluaciones list (last 50) — cast date to text to avoid Jackson timestamp issues
        List<Object> listParams = new ArrayList<>(baseParams);
        String listSql =
            "SELECT id::text, tipo_evaluador, fecha_evaluacion::text AS fecha_evaluacion, " +
            "calificacion_global, estatus " +
            "FROM ades_evaluacion_docente " +
            "WHERE profesor_id = ? " + cicloFilter +
            "AND is_active = TRUE AND estatus != 'BORRADOR' " +
            "ORDER BY fecha_evaluacion DESC LIMIT 50";
        List<Map<String, Object>> evaluaciones = jdbc.queryForList(listSql, listParams.toArray());

        double promedioGlobal = porTipo.isEmpty() ? 0.0
                : Math.round((sumaPromedios / porTipo.size()) * 100.0) / 100.0;

        Map<String, Object> res = new HashMap<>();
        res.put("profesor_id",  profesorId.toString());
        res.put("por_tipo",     porTipo);
        res.put("evaluaciones", evaluaciones);
        res.put("promedio_global", promedioGlobal);
        return res;
    }

    @Override
    public UUID plantelDeProfesor(UUID profesorId) {
        List<UUID> rows = jdbc.queryForList(
            "SELECT plantel_id FROM ades_profesores WHERE id = ?", UUID.class, profesorId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public UUID evaluacionIdDePlanMejora(UUID planMejoraId) {
        List<UUID> rows = jdbc.queryForList(
            "SELECT evaluacion_id FROM ades_planes_mejora_docente WHERE id = ?", UUID.class, planMejoraId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}

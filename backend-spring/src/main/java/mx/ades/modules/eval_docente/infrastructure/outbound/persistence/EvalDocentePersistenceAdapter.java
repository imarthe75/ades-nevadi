package mx.ades.modules.eval_docente.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

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
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT tipo_evaluador, AVG(calificacion_global) AS promedio, COUNT(*) AS total " +
            "FROM ades_evaluacion_docente " +
            "WHERE profesor_id = ? AND ciclo_escolar_id = ? AND is_active = TRUE AND estatus != 'BORRADOR' " +
            "GROUP BY tipo_evaluador",
            profesorId, cicloId);

        Map<String, Double> porTipo = new HashMap<>();
        long totalEval = 0;
        double sumaPromedios = 0.0;

        for (Map<String, Object> r : rows) {
            String tipo = (String) r.get("tipo_evaluador");
            double prom = r.get("promedio") != null ? ((Number) r.get("promedio")).doubleValue() : 0.0;
            long count = r.get("total") != null ? ((Number) r.get("total")).longValue() : 0;
            double roundedProm = Math.round(prom * 100.0) / 100.0;
            porTipo.put(tipo, roundedProm);
            totalEval += count;
            sumaPromedios += roundedProm;
        }

        Double promedioGlobal = porTipo.isEmpty() ? null
                : Math.round((sumaPromedios / porTipo.size()) * 100.0) / 100.0;

        Map<String, Object> res = new HashMap<>();
        res.put("profesor_id", profesorId.toString());
        res.put("ciclo_escolar_id", cicloId.toString());
        res.put("total_evaluaciones", totalEval);
        res.put("promedio_global", promedioGlobal);
        res.put("por_tipo", porTipo);
        return res;
    }
}

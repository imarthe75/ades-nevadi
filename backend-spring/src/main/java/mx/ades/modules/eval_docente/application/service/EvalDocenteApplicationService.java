package mx.ades.modules.eval_docente.application.service;

import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.EnviarEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;

import java.util.Map;
import java.util.UUID;

public class EvalDocenteApplicationService
        implements CrearEvaluacionUseCase, GuardarCriteriosUseCase, EnviarEvaluacionUseCase {

    private final EvalDocenteRepositoryPort repository;

    public EvalDocenteApplicationService(EvalDocenteRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> crear(CrearEvaluacionUseCase.Command cmd) {
        UUID id = repository.insertEvaluacion(cmd);
        return repository.fetchEvaluacion(id);
    }

    @Override
    public Map<String, Object> guardar(GuardarCriteriosUseCase.Command cmd) {
        String estatus = repository.findEstatus(cmd.evalId())
                .orElseThrow(() -> new IllegalArgumentException("Evaluación no encontrada: " + cmd.evalId()));

        if ("APROBADA".equalsIgnoreCase(estatus)) {
            throw new IllegalStateException("La evaluación ya está aprobada");
        }

        for (GuardarCriteriosUseCase.CriterioCalificacion c : cmd.criterios()) {
            repository.upsertCriterio(cmd.evalId(), c);
        }
        repository.recalcularGlobal(cmd.evalId());

        return Map.of("ok", true, "eval_id", cmd.evalId().toString());
    }

    @Override
    public Map<String, Object> enviar(EnviarEvaluacionUseCase.Command cmd) {
        int updated = repository.enviar(cmd.evalId(), cmd.usuario());
        if (updated == 0) {
            throw new IllegalStateException("No se pudo enviar la evaluación o ya fue enviada");
        }
        return Map.of("ok", true);
    }
}

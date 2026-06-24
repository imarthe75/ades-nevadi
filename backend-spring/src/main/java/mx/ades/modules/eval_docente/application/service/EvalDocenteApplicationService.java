package mx.ades.modules.eval_docente.application.service;

import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.EnviarEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: evaluación docente 360° con 4 tipos y 7 criterios ponderados.
 * Implementa {@link CrearEvaluacionUseCase}, {@link GuardarCriteriosUseCase}
 * y {@link EnviarEvaluacionUseCase} coordinando el dominio de evaluación docente
 * con el puerto de repositorio. Soporta tipos AUTO/PAR/COORDINADOR/DIRECTOR
 * en escala 1-5, con recálculo automático del puntaje global al guardar criterios.
 *
 * @author ADES
 * @since 2026
 */
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

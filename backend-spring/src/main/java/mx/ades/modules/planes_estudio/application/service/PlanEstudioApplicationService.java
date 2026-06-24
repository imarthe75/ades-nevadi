package mx.ades.modules.planes_estudio.application.service;

import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;
import mx.ades.modules.planes_estudio.domain.port.out.PlanEstudioRepositoryPort;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: asignación y mantenimiento de materias en los planes de estudio.
 * Implementa {@link AsignarMateriaUseCase} coordinando el dominio de planes de
 * estudio con el puerto de repositorio, gestionando la relación entre planes,
 * materias, horas semanales y carácter obligatorio para los tres niveles educativos.
 *
 * @author ADES
 * @since 2026
 */
public class PlanEstudioApplicationService implements AsignarMateriaUseCase {

    private final PlanEstudioRepositoryPort repo;

    public PlanEstudioApplicationService(PlanEstudioRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID asignar(Command cmd) {
        return repo.insert(cmd);
    }

    public Map<String, Object> detalle(UUID id) {
        return repo.fetchById(id);
    }

    public void patch(UUID id, Map<String, Object> body) {
        if (body.containsKey("horas_semana")) {
            repo.patchHorasSemana(id, ((Number) body.get("horas_semana")).doubleValue());
        }
        if (body.containsKey("es_obligatoria")) {
            repo.patchObligatoria(id, (Boolean) body.get("es_obligatoria"));
        }
        if (body.containsKey("orden")) {
            repo.patchOrden(id, ((Number) body.get("orden")).intValue());
        }
    }

    public void eliminar(UUID id) {
        int rows = repo.softDelete(id);
        if (rows == 0) throw new IllegalStateException("Plan de estudio no encontrado: " + id);
    }
}

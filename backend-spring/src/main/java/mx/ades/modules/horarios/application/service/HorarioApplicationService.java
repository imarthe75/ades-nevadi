package mx.ades.modules.horarios.application.service;

import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.domain.port.out.HorarioWriteRepositoryPort;

import java.util.UUID;

/**
 * Caso de uso: creación y actualización de horarios de clases.
 * Implementa {@link CrearHorarioUseCase} y {@link ActualizarHorarioUseCase}
 * coordinando el dominio de horarios con el puerto de escritura de repositorio.
 * Soporta el round-trip XML con aSc TimeTables mediante mapeo UUID↔aSc IDs
 * para importación y exportación de horarios de los tres planteles.
 *
 * @author ADES
 * @since 2026
 */
public class HorarioApplicationService
        implements CrearHorarioUseCase, ActualizarHorarioUseCase {

    private final HorarioWriteRepositoryPort repo;

    public HorarioApplicationService(HorarioWriteRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID crear(CrearHorarioUseCase.Command cmd) {
        return repo.insert(cmd);
    }

    @Override
    public void actualizar(ActualizarHorarioUseCase.Command cmd) {
        if (!repo.exists(cmd.horarioId())) {
            throw new IllegalStateException("Horario no encontrado: " + cmd.horarioId());
        }
        repo.update(cmd);
    }

    public void eliminar(UUID horarioId) {
        repo.softDelete(horarioId);
    }
}

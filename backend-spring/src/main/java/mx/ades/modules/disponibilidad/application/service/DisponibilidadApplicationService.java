package mx.ades.modules.disponibilidad.application.service;

import mx.ades.modules.disponibilidad.DisponibilidadDocente;
import mx.ades.modules.disponibilidad.domain.port.in.EliminarSlotUseCase;
import mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase;
import mx.ades.modules.disponibilidad.domain.port.out.DisponibilidadRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class DisponibilidadApplicationService
        implements GuardarDisponibilidadUseCase, EliminarSlotUseCase {

    private final DisponibilidadRepositoryPort repo;

    public DisponibilidadApplicationService(DisponibilidadRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public void guardar(GuardarDisponibilidadUseCase.Command cmd) {
        repo.softDeleteByProfesor(cmd.profesorId(), cmd.cicloEscolarId());
        for (GuardarDisponibilidadUseCase.Slot slot : cmd.slots()) {
            repo.createSlot(cmd.profesorId(), cmd.cicloEscolarId(), slot, cmd.usuario());
        }
        if (cmd.horasSemanaMax() != null || cmd.horasFrenteGrupo() != null) {
            repo.updateProfesorHoras(cmd.profesorId(), cmd.horasSemanaMax(), cmd.horasFrenteGrupo(), cmd.usuario());
        }
    }

    @Override
    public void eliminar(UUID slotId) {
        DisponibilidadDocente slot = repo.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot no encontrado"));
        slot.setIsActive(false);
        repo.save(slot);
    }
}

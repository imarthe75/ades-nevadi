package mx.ades.modules.medico.application.service;

import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.out.PersonalSaludRepositoryPort;

import java.util.Map;
import java.util.UUID;

public class PersonalSaludApplicationService
        implements RegistrarPersonalSaludUseCase, ActualizarPersonalSaludUseCase {

    private final PersonalSaludRepositoryPort repo;

    public PersonalSaludApplicationService(PersonalSaludRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarPersonalSaludUseCase.Command cmd) {
        return repo.insert(cmd);
    }

    @Override
    public void actualizar(ActualizarPersonalSaludUseCase.Command cmd) {
        repo.update(cmd.saludId(), cmd);
    }

    public void desactivar(UUID saludId) {
        int n = repo.softDelete(saludId);
        if (n == 0) throw new IllegalArgumentException("Personal de salud no encontrado: " + saludId);
    }

    public Map<String, Object> detalle(UUID saludId) {
        return repo.fetchById(saludId);
    }
}

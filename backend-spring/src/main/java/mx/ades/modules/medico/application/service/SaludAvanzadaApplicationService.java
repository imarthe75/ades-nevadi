package mx.ades.modules.medico.application.service;

import mx.ades.modules.medico.domain.port.in.*;
import mx.ades.modules.medico.domain.port.out.SaludAvanzadaRepositoryPort;

import java.util.UUID;

public class SaludAvanzadaApplicationService
        implements RegistrarMedicamentoUseCase, SuspenderMedicamentoUseCase,
                   GenerarActaIncidenteUseCase, RegistrarPsicosocialUseCase,
                   RegistrarTutoriaUseCase {

    private final SaludAvanzadaRepositoryPort repo;

    public SaludAvanzadaApplicationService(SaludAvanzadaRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarMedicamentoUseCase.Command cmd) {
        return repo.insertMedicamento(cmd);
    }

    @Override
    public void suspender(SuspenderMedicamentoUseCase.Command cmd) {
        repo.suspenderMedicamento(cmd);
    }

    @Override
    public UUID generar(GenerarActaIncidenteUseCase.Command cmd) {
        if (!repo.existeIncidente(cmd.incidenteId())) {
            throw new IllegalStateException("Incidente médico no encontrado: " + cmd.incidenteId());
        }
        return repo.insertActaIncidente(cmd);
    }

    @Override
    public UUID registrar(RegistrarPsicosocialUseCase.Command cmd) {
        return repo.insertPsicosocial(cmd);
    }

    @Override
    public UUID registrar(RegistrarTutoriaUseCase.Command cmd) {
        return repo.insertTutoria(cmd);
    }
}

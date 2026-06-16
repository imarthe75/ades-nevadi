package mx.ades.modules.compliance.application.service;

import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;
import mx.ades.modules.compliance.domain.port.out.ComplianceRepositoryPort;

import java.util.UUID;

public class ComplianceApplicationService
        implements RegistrarNormativaUseCase, RegistrarRetencionUseCase, CrearAlertaUseCase {

    private final ComplianceRepositoryPort repo;

    public ComplianceApplicationService(ComplianceRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarNormativaUseCase.Command cmd) {
        return repo.insertNormativa(cmd);
    }

    @Override
    public UUID registrar(RegistrarRetencionUseCase.Command cmd) {
        return repo.insertRetencion(cmd);
    }

    @Override
    public UUID crear(CrearAlertaUseCase.Command cmd) {
        return repo.insertAlerta(cmd);
    }
}

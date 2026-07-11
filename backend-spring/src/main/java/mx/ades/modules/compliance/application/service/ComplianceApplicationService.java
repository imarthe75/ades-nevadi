package mx.ades.modules.compliance.application.service;

import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;
import mx.ades.modules.compliance.domain.port.out.ComplianceRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: gestión de cumplimiento normativo, retenciones y alertas regulatorias.
 * Implementa {@link RegistrarNormativaUseCase}, {@link RegistrarRetencionUseCase}
 * y {@link CrearAlertaUseCase} coordinando el dominio de compliance con
 * el puerto de repositorio, en apoyo al cumplimiento SEP, UAEMEX y LFPDPPP.
 *
 * @author ADES
 * @since 2026
 */
public class ComplianceApplicationService
        implements RegistrarNormativaUseCase, RegistrarRetencionUseCase, CrearAlertaUseCase {

    private final ComplianceRepositoryPort repo;

    public ComplianceApplicationService(ComplianceRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarNormativaUseCase.Command cmd) {
        return repo.insertNormativa(cmd);
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarRetencionUseCase.Command cmd) {
        return repo.insertRetencion(cmd);
    }

    @Override
    @Transactional
    public UUID crear(CrearAlertaUseCase.Command cmd) {
        return repo.insertAlerta(cmd);
    }
}

package mx.ades.modules.compliance.domain.port.out;

import mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase;
import mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase;

import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para normativas, retenciones y alertas de cumplimiento.
 *
 * @author ADES
 * @since 2026
 */
public interface ComplianceRepositoryPort {
    UUID insertNormativa(RegistrarNormativaUseCase.Command cmd);
    UUID insertRetencion(RegistrarRetencionUseCase.Command cmd);
    UUID insertAlerta(CrearAlertaUseCase.Command cmd);
}

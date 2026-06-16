package mx.ades.modules.medico.domain.port.out;

import mx.ades.modules.medico.domain.port.in.*;

import java.util.UUID;

public interface SaludAvanzadaRepositoryPort {

    UUID insertMedicamento(RegistrarMedicamentoUseCase.Command cmd);

    void suspenderMedicamento(SuspenderMedicamentoUseCase.Command cmd);

    boolean existeIncidente(UUID incidenteId);

    UUID insertActaIncidente(GenerarActaIncidenteUseCase.Command cmd);

    UUID insertPsicosocial(RegistrarPsicosocialUseCase.Command cmd);

    UUID insertTutoria(RegistrarTutoriaUseCase.Command cmd);
}

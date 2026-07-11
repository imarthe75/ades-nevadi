package mx.ades.modules.medico.application.service;

import mx.ades.modules.medico.domain.port.in.*;
import mx.ades.modules.medico.domain.port.out.SaludAvanzadaRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: gestión avanzada de salud estudiantil: medicamentos, incidentes y tutorías.
 * Implementa {@link RegistrarMedicamentoUseCase}, {@link SuspenderMedicamentoUseCase},
 * {@link GenerarActaIncidenteUseCase}, {@link RegistrarPsicosocialUseCase}
 * y {@link RegistrarTutoriaUseCase} coordinando el dominio de salud avanzada
 * con el puerto de repositorio para el seguimiento integral del bienestar estudiantil.
 *
 * @author ADES
 * @since 2026
 */
public class SaludAvanzadaApplicationService
        implements RegistrarMedicamentoUseCase, SuspenderMedicamentoUseCase,
                   GenerarActaIncidenteUseCase, RegistrarPsicosocialUseCase,
                   RegistrarTutoriaUseCase {

    private final SaludAvanzadaRepositoryPort repo;

    public SaludAvanzadaApplicationService(SaludAvanzadaRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarMedicamentoUseCase.Command cmd) {
        return repo.insertMedicamento(cmd);
    }

    @Override
    @Transactional
    public void suspender(SuspenderMedicamentoUseCase.Command cmd) {
        repo.suspenderMedicamento(cmd);
    }

    @Override
    @Transactional
    public UUID generar(GenerarActaIncidenteUseCase.Command cmd) {
        if (!repo.existeIncidente(cmd.incidenteId())) {
            throw new IllegalStateException("Incidente médico no encontrado: " + cmd.incidenteId());
        }
        return repo.insertActaIncidente(cmd);
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarPsicosocialUseCase.Command cmd) {
        return repo.insertPsicosocial(cmd);
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarTutoriaUseCase.Command cmd) {
        return repo.insertTutoria(cmd);
    }
}

package mx.ades.modules.expediente_laboral.application.service;

import mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.AgregarDocumentoLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.out.ExpedienteLaboralRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: creación y mantenimiento del expediente laboral del personal.
 * Implementa {@link CrearExpedienteLaboralUseCase}, {@link ActualizarExpedienteLaboralUseCase}
 * y {@link AgregarDocumentoLaboralUseCase} coordinando el dominio de RRHH con
 * el puerto de repositorio, gestionando los documentos digitalizados del personal
 * docente y administrativo de los tres planteles.
 *
 * @author ADES
 * @since 2026
 */
public class ExpedienteLaboralApplicationService
        implements CrearExpedienteLaboralUseCase, ActualizarExpedienteLaboralUseCase, AgregarDocumentoLaboralUseCase {

    private final ExpedienteLaboralRepositoryPort repository;

    public ExpedienteLaboralApplicationService(ExpedienteLaboralRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Map<String, Object> crear(CrearExpedienteLaboralUseCase.Command cmd) {
        UUID id = repository.insert(cmd);
        return repository.fetchById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> actualizar(ActualizarExpedienteLaboralUseCase.Command cmd) {
        repository.findById(cmd.id())
                .orElseThrow(() -> new IllegalArgumentException("Expediente laboral no encontrado: " + cmd.id()));
        return repository.patch(cmd.id(), cmd.patch(), cmd.usuarioId());
    }

    @Override
    @Transactional
    public Map<String, Object> agregar(AgregarDocumentoLaboralUseCase.Command cmd) {
        repository.findById(cmd.expedienteId())
                .orElseThrow(() -> new IllegalArgumentException("Expediente laboral no encontrado: " + cmd.expedienteId()));
        repository.agregarDocumento(cmd.expedienteId(), cmd.tipoDocumento(), cmd.url(), cmd.usuarioId());
        return repository.fetchById(cmd.expedienteId());
    }

    @Transactional
    public void eliminar(UUID id, int nivelAcceso, String usuarioId) {
        if (nivelAcceso > 2) throw new IllegalArgumentException("Solo RH o Dirección puede eliminar expedientes");
        repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expediente laboral no encontrado: " + id));
        repository.softDelete(id, usuarioId);
    }
}

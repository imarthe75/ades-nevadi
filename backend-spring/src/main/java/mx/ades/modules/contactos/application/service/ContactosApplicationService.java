package mx.ades.modules.contactos.application.service;

import mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase;
import mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase;
import mx.ades.modules.contactos.domain.port.out.ContactosRepositoryPort;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ContactosApplicationService
        implements RegistrarContactoUseCase, ActualizarContactoUseCase {

    private final ContactosRepositoryPort repo;

    public ContactosApplicationService(ContactosRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Map<String, Object> registrar(RegistrarContactoUseCase.Command cmd) {
        return repo.insertContacto(cmd);
    }

    @Override
    public Map<String, Object> actualizar(ActualizarContactoUseCase.Command cmd) {
        Optional<Map<String, Object>> existing = repo.fetchContactoForUpdate(cmd.contactoId());
        if (existing.isEmpty()) {
            throw new IllegalStateException("Contacto no encontrado");
        }
        int currentVersion = ((Number) existing.get().get("row_version")).intValue();
        if (cmd.rowVersion() != null && cmd.rowVersion() != currentVersion) {
            throw new IllegalStateException("CONFLICT: el registro fue modificado por otro usuario.");
        }
        return repo.updateContacto(cmd);
    }

    public void eliminar(UUID contactoId) {
        repo.softDeleteContacto(contactoId);
    }

    public Map<String, Object> expedienteMedico(UUID estudianteId) {
        return repo.fetchOrCreateExpedienteMedico(estudianteId);
    }

    public Map<String, Object> actualizarExpedienteMedico(UUID estudianteId, Map<String, Object> fields, String usuario) {
        return repo.upsertExpedienteMedico(estudianteId, fields, usuario);
    }

    public void upsertDocEstatus(UUID estudianteId, UUID docTipoId, UUID cicloId,
                                 String estatus, String observaciones, String username, UUID verificadoPorId) {
        repo.upsertDocEstatus(estudianteId, docTipoId, cicloId, estatus, observaciones, username, verificadoPorId);
    }
}

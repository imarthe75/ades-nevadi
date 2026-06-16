package mx.ades.modules.contactos.domain.port.out;

import mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase;
import mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ContactosRepositoryPort {

    Map<String, Object> insertContacto(RegistrarContactoUseCase.Command cmd);

    Optional<Map<String, Object>> fetchContactoForUpdate(UUID contactoId);

    Map<String, Object> updateContacto(ActualizarContactoUseCase.Command cmd);

    void softDeleteContacto(UUID contactoId);

    Map<String, Object> fetchOrCreateExpedienteMedico(UUID estudianteId);

    Map<String, Object> upsertExpedienteMedico(UUID estudianteId, Map<String, Object> fields, String usuarioMod);

    void upsertDocEstatus(UUID estudianteId, UUID docTipoId, UUID cicloId, String estatus, String observaciones, String username, UUID verificadoPorId);
}

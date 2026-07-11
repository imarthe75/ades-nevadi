package mx.ades.modules.personal_admin.application.service;

import mx.ades.common.ValidationUtils;
import mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase;
import mx.ades.modules.personal_admin.domain.port.out.PersonalAdminRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: alta y actualización de personal administrativo no docente.
 * Implementa {@link RegistrarPersonalAdminUseCase} coordinando el dominio de
 * personal administrativo con el puerto de repositorio, creando la entidad
 * persona y el registro de empleado en una sola transacción de escritura.
 *
 * @author ADES
 * @since 2026
 */
public class PersonalAdminApplicationService implements RegistrarPersonalAdminUseCase {

    private final PersonalAdminRepositoryPort repository;

    public PersonalAdminApplicationService(PersonalAdminRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID registrar(RegistrarPersonalAdminUseCase.Command cmd) {
        ValidationUtils.validarPersonaMap(cmd.persona());
        ValidationUtils.validarLaboralesMap(cmd.laborales());
        UUID personaId = repository.createPersona(cmd.persona(), cmd.usuario());
        return repository.createEmpleado(personaId, cmd.plantelId(), cmd.laborales(), cmd.usuario());
    }

    @Transactional
    public Map<String, Object> actualizar(UUID id, Map<String, Object> persona,
                                          Map<String, Object> laborales, String usuario) {
        ValidationUtils.validarPersonaMap(persona);
        ValidationUtils.validarLaboralesMap(laborales);
        UUID personaId = repository.findPersonaId(id)
                .orElseThrow(() -> new IllegalArgumentException("Personal no encontrado: " + id));
        if (persona != null) repository.updatePersona(personaId, persona, usuario);
        if (laborales != null) repository.updateEmpleado(id, laborales, usuario);
        return repository.fetchById(id);
    }

    @Transactional
    public void desactivar(UUID id) {
        int n = repository.softDelete(id);
        if (n == 0) throw new IllegalArgumentException("Personal no encontrado: " + id);
    }
}

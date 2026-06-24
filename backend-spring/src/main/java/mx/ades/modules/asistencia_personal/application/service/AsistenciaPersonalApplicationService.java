package mx.ades.modules.asistencia_personal.application.service;

import mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.out.AsistenciaPersonalRepositoryPort;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: registro y actualización de asistencia del personal docente y administrativo.
 * Implementa {@link RegistrarAsistenciaUseCase} y {@link ActualizarAsistenciaUseCase}
 * coordinando el dominio de asistencia personal con el puerto de repositorio,
 * aplicando upsert para evitar duplicados por persona y fecha.
 *
 * @author ADES
 * @since 2026
 */
public class AsistenciaPersonalApplicationService
        implements RegistrarAsistenciaUseCase, ActualizarAsistenciaUseCase {

    private final AsistenciaPersonalRepositoryPort repository;

    public AsistenciaPersonalApplicationService(AsistenciaPersonalRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> registrar(RegistrarAsistenciaUseCase.Command cmd) {
        if (repository.existeRegistro(cmd.personaId(), cmd.fecha())) {
            repository.update(cmd);
        } else {
            repository.insert(cmd);
        }
        return repository.findByPersonaFecha(cmd.personaId(), cmd.fecha());
    }

    @Override
    public Map<String, Object> actualizar(ActualizarAsistenciaUseCase.Command cmd) {
        repository.findById(cmd.id())
                .orElseThrow(() -> new IllegalArgumentException("Registro de asistencia no encontrado: " + cmd.id()));
        repository.patch(cmd);
        return repository.fetchById(cmd.id());
    }

    public void eliminar(UUID id, String usuario) {
        repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registro de asistencia no encontrado: " + id));
        repository.softDelete(id, usuario);
    }
}

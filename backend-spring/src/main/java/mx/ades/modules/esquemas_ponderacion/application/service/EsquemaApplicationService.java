package mx.ades.modules.esquemas_ponderacion.application.service;

import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: creación y actualización de esquemas de ponderación de calificaciones.
 * Implementa {@link CrearEsquemaUseCase} y {@link ActualizarEsquemaUseCase}
 * coordinando el dominio de esquemas con el puerto de repositorio, gestionando
 * atómicamente la cabecera del esquema y sus ítems de ponderación por tipo de actividad.
 *
 * @author ADES
 * @since 2026
 */
public class EsquemaApplicationService implements CrearEsquemaUseCase, ActualizarEsquemaUseCase {

    private final EsquemaRepositoryPort repository;

    public EsquemaApplicationService(EsquemaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Map<String, Object> crear(CrearEsquemaUseCase.Command cmd) {
        UUID id = repository.insertEsquema(cmd);
        repository.insertItems(id, cmd);
        return Map.of("id", id.toString(), "message", "Esquema creado");
    }

    @Override
    @Transactional
    public Map<String, Object> actualizar(ActualizarEsquemaUseCase.Command cmd) {
        int updated = repository.updateEsquema(cmd);
        if (updated == 0) throw new IllegalArgumentException("Esquema no encontrado: " + cmd.esquemaId());
        repository.softDeleteItems(cmd.esquemaId());
        repository.insertItems(cmd.esquemaId(), cmd);
        return Map.of("message", "Esquema actualizado");
    }

    @Transactional
    public Map<String, Object> desactivar(UUID esquemaId, String usuario) {
        int updated = repository.desactivar(esquemaId, usuario);
        if (updated == 0) throw new IllegalArgumentException("Esquema no encontrado: " + esquemaId);
        return Map.of("message", "Esquema desactivado");
    }
}

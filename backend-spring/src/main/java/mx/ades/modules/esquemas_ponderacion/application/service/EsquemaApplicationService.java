package mx.ades.modules.esquemas_ponderacion.application.service;

import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort;

import java.util.Map;
import java.util.UUID;

public class EsquemaApplicationService implements CrearEsquemaUseCase, ActualizarEsquemaUseCase {

    private final EsquemaRepositoryPort repository;

    public EsquemaApplicationService(EsquemaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> crear(CrearEsquemaUseCase.Command cmd) {
        UUID id = repository.insertEsquema(cmd);
        repository.insertItems(id, cmd);
        return Map.of("id", id.toString(), "message", "Esquema creado");
    }

    @Override
    public Map<String, Object> actualizar(ActualizarEsquemaUseCase.Command cmd) {
        int updated = repository.updateEsquema(cmd);
        if (updated == 0) throw new IllegalArgumentException("Esquema no encontrado: " + cmd.esquemaId());
        repository.softDeleteItems(cmd.esquemaId());
        repository.insertItems(cmd.esquemaId(), cmd);
        return Map.of("message", "Esquema actualizado");
    }

    public Map<String, Object> desactivar(UUID esquemaId, String usuario) {
        int updated = repository.desactivar(esquemaId, usuario);
        if (updated == 0) throw new IllegalArgumentException("Esquema no encontrado: " + esquemaId);
        return Map.of("message", "Esquema desactivado");
    }
}

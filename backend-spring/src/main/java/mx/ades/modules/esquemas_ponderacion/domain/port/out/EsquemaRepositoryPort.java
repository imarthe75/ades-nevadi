package mx.ades.modules.esquemas_ponderacion.domain.port.out;

import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EsquemaRepositoryPort {
    List<Map<String, Object>> list(UUID nivelEducativoId, UUID materiaId);
    Map<String, Object> efectivo(UUID materiaId);
    UUID insertEsquema(CrearEsquemaUseCase.Command cmd);
    void insertItems(UUID esquemaId, CrearEsquemaUseCase.Command cmd);
    int updateEsquema(ActualizarEsquemaUseCase.Command cmd);
    void softDeleteItems(UUID esquemaId);
    void insertItems(UUID esquemaId, ActualizarEsquemaUseCase.Command cmd);
    int desactivar(UUID esquemaId, String usuario);
}

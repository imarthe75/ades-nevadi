package mx.ades.modules.esquemas_ponderacion.domain.port.out;

import mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase;
import mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para esquemas de ponderación y sus ítems.
 * <p>Cubre {@code ades_esquemas_ponderacion} e {@code ades_items_ponderacion}.
 * El método {@code efectivo} resuelve el esquema vigente más específico para una materia.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EsquemaRepositoryPort {
    List<Map<String, Object>> list(UUID nivelEducativoId, UUID materiaId, UUID profesorScopeId, UUID plantelScopeId);
    Map<String, Object> efectivo(UUID materiaId);
    UUID insertEsquema(CrearEsquemaUseCase.Command cmd);
    void insertItems(UUID esquemaId, CrearEsquemaUseCase.Command cmd);
    int updateEsquema(ActualizarEsquemaUseCase.Command cmd);
    void softDeleteItems(UUID esquemaId);
    void insertItems(UUID esquemaId, ActualizarEsquemaUseCase.Command cmd);
    int desactivar(UUID esquemaId, String usuario);
    UUID resolverProfesorIdPorPersona(UUID personaId);
    Map<String, Object> scopeDe(UUID esquemaId);
}

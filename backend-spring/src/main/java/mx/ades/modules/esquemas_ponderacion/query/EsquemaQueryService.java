package mx.ades.modules.esquemas_ponderacion.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo esquemas_ponderacion.
 * <p>Expone listado de esquemas filtrables por nivel y materia, y resolución del esquema
 * efectivo vigente para una materia dada.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class EsquemaQueryService {

    private final EsquemaRepositoryPort repository;

    public List<Map<String, Object>> listar(UUID nivelEducativoId, UUID materiaId, UUID profesorScopeId, UUID plantelScopeId) {
        return repository.list(nivelEducativoId, materiaId, profesorScopeId, plantelScopeId);
    }

    public Map<String, Object> efectivo(UUID materiaId) {
        return repository.efectivo(materiaId);
    }

    public UUID resolverProfesorIdPorPersona(UUID personaId) {
        return repository.resolverProfesorIdPorPersona(personaId);
    }

    public Map<String, Object> scopeDe(UUID esquemaId) {
        return repository.scopeDe(esquemaId);
    }
}

package mx.ades.modules.catalogos.domain.port.out;

import mx.ades.modules.catalogos.CicloEscolar;
import mx.ades.modules.catalogos.Grado;
import mx.ades.modules.catalogos.NivelEducativo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CatalogReadPort {

    List<NivelEducativo> findAllNiveles();

    List<Grado> findAllGrados();

    List<Grado> findGrados(UUID nivelId, UUID plantelId);

    List<CicloEscolar> findAllCiclos();

    List<CicloEscolar> findCiclosVigentes();

    List<Map<String, Object>> roles();

    List<Map<String, Object>> periodos(UUID cicloId, UUID grupoId);

    List<Map<String, Object>> paises();

    List<Map<String, Object>> nacionalidades();

    List<Map<String, Object>> lenguasIndigenas(String familia);

    List<Map<String, Object>> familiasLinguisticas();

    List<Map<String, Object>> nivelesIngles();
}

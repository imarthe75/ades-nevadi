package mx.ades.modules.geo.domain.port.out;

import java.util.List;
import java.util.Map;

public interface GeoQueryPort {

    List<Map<String, Object>> estados();

    List<Map<String, Object>> municipios(int estadoId);

    List<Map<String, Object>> coloniasPorCp(String cp);

    List<Map<String, Object>> coloniasPorMunicipio(int municipioId);

    Map<String, Object> buscarPorCp(String cp);
}

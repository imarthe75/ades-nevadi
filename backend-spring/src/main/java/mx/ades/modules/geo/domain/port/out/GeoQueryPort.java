package mx.ades.modules.geo.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: consultas al catálogo geográfico SEPOMEX.
 * Los datos residen en tablas {@code public.ades_*} (IDs UUID, colonias deduplicadas
 * según migración 092). Usado por el selector-geo del frontend.
 *
 * @author ADES
 * @since 2026
 */
public interface GeoQueryPort {

    List<Map<String, Object>> estados();

    List<Map<String, Object>> municipios(UUID estadoId);

    List<Map<String, Object>> coloniasPorCp(String cp);

    List<Map<String, Object>> coloniasPorMunicipio(UUID municipioId);

    Map<String, Object> buscarPorCp(String cp);
}

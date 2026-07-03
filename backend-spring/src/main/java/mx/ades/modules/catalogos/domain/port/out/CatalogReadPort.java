package mx.ades.modules.catalogos.domain.port.out;

import mx.ades.modules.catalogos.CicloEscolar;
import mx.ades.modules.catalogos.Grado;
import mx.ades.modules.catalogos.NivelEducativo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de consulta para catálogos del sistema
 * (niveles educativos, grados, ciclos, roles, períodos, países, nacionalidades,
 * lenguas indígenas, familias lingüísticas y niveles de inglés).
 *
 * @author ADES
 * @since 2026
 */
public interface CatalogReadPort {

    List<NivelEducativo> findAllNiveles();

    List<Grado> findAllGrados();

    List<Grado> findGrados(UUID nivelId, UUID plantelId);

    /** Grados de un nivel en TODOS los planteles, sin deduplicar por (numero_grado, nivel). */
    List<Grado> findGradosSinDeduplicar(UUID nivelId);

    List<CicloEscolar> findAllCiclos();

    List<CicloEscolar> findCiclosVigentes();

    List<CicloEscolar> findCiclosVigentesByNivel(UUID nivelId);

    List<Map<String, Object>> roles();

    List<Map<String, Object>> periodos(UUID cicloId, UUID grupoId);

    List<Map<String, Object>> paises();

    List<Map<String, Object>> nacionalidades();

    List<Map<String, Object>> lenguasIndigenas(String familia);

    List<Map<String, Object>> familiasLinguisticas();

    List<Map<String, Object>> nivelesIngles();
}

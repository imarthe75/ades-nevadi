package mx.ades.modules.capacitaciones.domain.port.out;

import mx.ades.modules.capacitaciones.CapacitacionDocente;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para la entidad
 * {@code CapacitacionDocente} en la tabla {@code ades_capacitaciones_docente}.
 *
 * <p>Incluye listado filtrado y resumen agregado por docente.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CapacitacionRepositoryPort {

    CapacitacionDocente save(CapacitacionDocente cap);

    Optional<CapacitacionDocente> findActiveById(UUID id);

    List<Map<String, Object>> list(UUID docenteId, String tipo, String modalidad, Boolean validado, String q,
                                    int pagina, int porPagina);

    List<Map<String, Object>> resumen(UUID docenteId);
}

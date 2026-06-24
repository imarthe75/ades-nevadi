package mx.ades.modules.evaluaciones.domain.port.out;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para asignaciones de aula a slots horarios.
 * <p>Verifica conflictos de solapamiento y persiste en {@code ades_asignaciones_aula}.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface AsignacionAulaRepositoryPort {

    boolean existeConflicto(UUID aulaId, LocalDate fecha, SlotHorario slot);

    UUID guardar(UUID claseId, UUID aulaId, LocalDate fecha, SlotHorario slot,
                 String observaciones, String username);
}

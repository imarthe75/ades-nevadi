package mx.ades.modules.disponibilidad.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para eliminar (soft-delete) un slot de disponibilidad docente
 * en el módulo disponibilidad.
 *
 * @author ADES
 * @since 2026
 */
public interface EliminarSlotUseCase {
    void eliminar(UUID slotId);
}

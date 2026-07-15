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

    /**
     * Devuelve el profesor_id dueño del slot — usado por el adaptador REST para
     * verificar ownership ANTES de borrar (ver {@code DisponibilidadDocenteController#eliminar}):
     * un Docente (nivelAcceso 4) solo puede borrar slots de su propia disponibilidad,
     * mismo criterio ya aplicado en el PUT bulk ({@code guardar()}).
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 si el slot no existe.
     */
    java.util.UUID obtenerProfesorId(UUID slotId);
}

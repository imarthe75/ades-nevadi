package mx.ades.modules.biblioteca.domain.port.in;

import mx.ades.modules.biblioteca.domain.model.EstatusPrestamo;

import java.util.UUID;

public interface DevolverPrestamoUseCase {

    /**
     * Cierra un préstamo. Si estatusFinal es DEVUELTO, el ejemplar regresa al
     * acervo. Si es EXTRAVIADO, NO regresa (la copia se pierde).
     */
    record Command(
            UUID prestamoId,
            EstatusPrestamo estatusFinal,
            String observaciones,
            int nivelAcceso
    ) {
        public Command {
            if (prestamoId == null)
                throw new IllegalArgumentException("prestamo_id es requerido");
            if (estatusFinal == null || estatusFinal.estaAbierto())
                throw new IllegalArgumentException("estatus_final debe ser DEVUELTO o EXTRAVIADO");
        }
    }

    void devolver(Command cmd);
}

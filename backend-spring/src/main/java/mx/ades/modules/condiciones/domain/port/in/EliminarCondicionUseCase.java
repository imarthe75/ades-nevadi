package mx.ades.modules.condiciones.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para eliminar (soft-delete) una condición crónica en el módulo condiciones.
 * <p>Requiere nivel de acceso suficiente; el registro se desactiva pero no se borra físicamente.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EliminarCondicionUseCase {

    void eliminar(UUID condicionId, int nivelAcceso);
}

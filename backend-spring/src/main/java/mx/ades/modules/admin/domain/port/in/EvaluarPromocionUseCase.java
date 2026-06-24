package mx.ades.modules.admin.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para evaluar y actualizar el estatus de promoción
 * de los estudiantes en el dominio de administración escolar.
 *
 * <p>Invoca la función PostgreSQL {@code fn_evaluar_estatus_promocion} para determinar
 * si cada alumno del ciclo/plantel indicado aprueba o reprueba el período.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EvaluarPromocionUseCase {

    Object ejecutar(Command cmd);

    record Command(UUID cicloId, UUID plantelId, String usuario) {
        public Command {
            if (cicloId == null) throw new IllegalArgumentException("cicloId es requerido");
            if (usuario == null || usuario.isBlank()) throw new IllegalArgumentException("usuario es requerido");
        }
    }
}

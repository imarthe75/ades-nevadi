package mx.ades.modules.admin.domain.port.out;

import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para ejecutar la evaluación
 * masiva de promoción de alumnos en la tabla {@code ades_calificaciones_periodo}.
 *
 * @author ADES
 * @since 2026
 */
public interface PromocionRepositoryPort {
    Object ejecutarPromocion(UUID cicloId, UUID plantelId, String usuario);
}

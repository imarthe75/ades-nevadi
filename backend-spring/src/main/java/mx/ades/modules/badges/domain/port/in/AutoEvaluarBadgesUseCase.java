package mx.ades.modules.badges.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para auto-evaluar y otorgar masivamente badges
 * con criterio {@code AUTOMATICO} en el dominio de badges.
 *
 * <p>Itera todos los badges automáticos activos, calcula los alumnos elegibles según
 * la métrica configurada y los otorga en bulk para el ciclo indicado.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface AutoEvaluarBadgesUseCase {

    record Result(int badgesEvaluados, int totalOtorgados) {}

    Result autoEvaluar(UUID cicloId);
}

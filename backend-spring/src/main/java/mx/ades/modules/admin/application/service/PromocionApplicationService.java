package mx.ades.modules.admin.application.service;

import mx.ades.modules.admin.domain.port.in.EvaluarPromocionUseCase;
import mx.ades.modules.admin.domain.port.out.PromocionRepositoryPort;

/**
 * Caso de uso: evalúa y ejecuta la promoción de alumnos al siguiente grado.
 * Implementa {@link EvaluarPromocionUseCase} coordinando el dominio de administración
 * escolar con la función PostgreSQL {@code fn_evaluar_estatus_promocion()},
 * aplicable tanto a planteles SEP (Primaria/Secundaria NEM) como a la Preparatoria UAEMEX.
 *
 * @author ADES
 * @since 2026
 */
public class PromocionApplicationService implements EvaluarPromocionUseCase {

    private final PromocionRepositoryPort repo;

    public PromocionApplicationService(PromocionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Object ejecutar(Command cmd) {
        return repo.ejecutarPromocion(cmd.cicloId(), cmd.plantelId(), cmd.usuario());
    }
}

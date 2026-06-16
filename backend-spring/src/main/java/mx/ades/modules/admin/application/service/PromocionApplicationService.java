package mx.ades.modules.admin.application.service;

import mx.ades.modules.admin.domain.port.in.EvaluarPromocionUseCase;
import mx.ades.modules.admin.domain.port.out.PromocionRepositoryPort;

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

package mx.ades.modules.cierre.application.service;

import mx.ades.modules.cierre.CierreCicloService;
import mx.ades.modules.cierre.domain.port.in.CerrarCicloUseCase;
import mx.ades.modules.cierre.domain.port.out.CierreRepositoryPort;

public class CierreApplicationService implements CerrarCicloUseCase {

    private final CierreRepositoryPort repo;
    private final CierreCicloService cierreService;

    public CierreApplicationService(CierreRepositoryPort repo, CierreCicloService cierreService) {
        this.repo = repo;
        this.cierreService = cierreService;
    }

    @Override
    public String cerrar(Command cmd) {
        String estado = repo.fetchEstado(cmd.cicloId())
            .orElseThrow(() -> new IllegalStateException("Ciclo escolar no encontrado"));

        if ("CERRADO".equals(estado)) {
            throw new IllegalStateException("El ciclo escolar ya se encuentra cerrado");
        }

        repo.marcarCerrado(cmd.cicloId());

        if (cmd.cicloDestinoId() != null) {
            return cierreService.cerrarCiclo(cmd.cicloId(), cmd.cicloDestinoId(), cmd.usuario());
        }
        return null;
    }
}

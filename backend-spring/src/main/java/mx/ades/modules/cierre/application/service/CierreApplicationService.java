package mx.ades.modules.cierre.application.service;

import mx.ades.modules.cierre.CierreCicloService;
import mx.ades.modules.cierre.domain.port.in.CerrarCicloUseCase;
import mx.ades.modules.cierre.domain.port.out.CierreRepositoryPort;

/**
 * Caso de uso: cierre definitivo de un período académico.
 * Implementa {@link CerrarCicloUseCase} coordinando el dominio de cierre
 * con el puerto de repositorio y el servicio de transición de ciclo.
 * La operación es irreversible una vez confirmada; valida que el ciclo
 * no esté ya cerrado antes de proceder.
 *
 * @author ADES
 * @since 2026
 */
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

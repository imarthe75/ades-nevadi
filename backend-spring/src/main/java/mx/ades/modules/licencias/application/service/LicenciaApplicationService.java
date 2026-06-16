package mx.ades.modules.licencias.application.service;

import mx.ades.modules.licencias.LicenciaPersonal;
import mx.ades.modules.licencias.domain.model.DiasHabiles;
import mx.ades.modules.licencias.domain.model.EstadoLicencia;
import mx.ades.modules.licencias.domain.port.in.ResolverLicenciaUseCase;
import mx.ades.modules.licencias.domain.port.in.SolicitarLicenciaUseCase;
import mx.ades.modules.licencias.domain.port.out.LicenciaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

public class LicenciaApplicationService
        implements SolicitarLicenciaUseCase, ResolverLicenciaUseCase {

    private final LicenciaRepositoryPort repo;

    public LicenciaApplicationService(LicenciaRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID solicitar(SolicitarLicenciaUseCase.Command cmd) {
        int dias = DiasHabiles.calcular(cmd.fechaInicio(), cmd.fechaFin()).valor();
        LicenciaPersonal lp = new LicenciaPersonal();
        lp.setPersonalId(cmd.personalId());
        lp.setTipoLicencia(cmd.tipoLicencia().name());
        lp.setFechaInicio(cmd.fechaInicio());
        lp.setFechaFin(cmd.fechaFin());
        lp.setDiasHabiles(dias);
        lp.setMotivo(cmd.motivo());
        lp.setSustitutoId(cmd.sustitutoId());
        lp.setConGoceSueldo(cmd.conGoceSueldo());
        lp.setEstado(EstadoLicencia.PENDIENTE.name());
        lp.setIsActive(true);
        if (cmd.usuarioCreacion() != null) {
            lp.setUsuarioCreacion(cmd.usuarioCreacion());
            lp.setUsuarioModificacion(cmd.usuarioCreacion());
        }
        return repo.save(lp).getId();
    }

    @Override
    public void resolver(ResolverLicenciaUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo Director o RH puede aprobar/rechazar licencias");
        }
        LicenciaPersonal lp = repo.findActiveById(cmd.licenciaId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));

        EstadoLicencia actual = EstadoLicencia.valueOf(lp.getEstado());
        if (!actual.permiteModificacion()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La licencia no está en estado PENDIENTE");
        }

        boolean aprobando = cmd.accion() == ResolverLicenciaUseCase.Accion.APROBAR;
        lp.setEstado(aprobando ? EstadoLicencia.APROBADA.name() : EstadoLicencia.RECHAZADA.name());
        lp.setAprobadoPor(cmd.usuarioId());
        lp.setFechaAprobacion(LocalDateTime.now());
        if (cmd.observaciones() != null) lp.setObservacionesRh(cmd.observaciones());
        if (cmd.usuarioNombre() != null) lp.setUsuarioModificacion(cmd.usuarioNombre());
        repo.save(lp);
    }

    public void cancelar(UUID licenciaId, String usuarioNombre) {
        LicenciaPersonal lp = repo.findActiveById(licenciaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        EstadoLicencia actual = EstadoLicencia.valueOf(lp.getEstado());
        if (!actual.permiteModificacion()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede cancelar una licencia PENDIENTE");
        }
        lp.setEstado(EstadoLicencia.CANCELADA.name());
        lp.setIsActive(false);
        if (usuarioNombre != null) lp.setUsuarioModificacion(usuarioNombre);
        repo.save(lp);
    }

    public void actualizar(UUID licenciaId, LicenciaPersonal patch, String usuarioNombre) {
        LicenciaPersonal lp = repo.findActiveById(licenciaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Licencia no encontrada"));
        EstadoLicencia actual = EstadoLicencia.valueOf(lp.getEstado());
        if (!actual.permiteModificacion()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede editar una licencia PENDIENTE");
        }
        if (patch.getMotivo() != null)         lp.setMotivo(patch.getMotivo());
        if (patch.getObservacionesRh() != null) lp.setObservacionesRh(patch.getObservacionesRh());
        if (patch.getSustitutoId() != null)    lp.setSustitutoId(patch.getSustitutoId());
        if (patch.getConGoceSueldo() != null)  lp.setConGoceSueldo(patch.getConGoceSueldo());
        if (usuarioNombre != null)             lp.setUsuarioModificacion(usuarioNombre);
        repo.save(lp);
    }
}

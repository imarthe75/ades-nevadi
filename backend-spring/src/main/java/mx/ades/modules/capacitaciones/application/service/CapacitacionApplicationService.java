package mx.ades.modules.capacitaciones.application.service;

import mx.ades.modules.capacitaciones.CapacitacionDocente;
import mx.ades.modules.capacitaciones.domain.port.in.RegistrarCapacitacionUseCase;
import mx.ades.modules.capacitaciones.domain.port.in.ValidarCapacitacionUseCase;
import mx.ades.modules.capacitaciones.domain.port.out.CapacitacionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Caso de uso: registro y validación de capacitaciones del personal docente.
 * Implementa {@link RegistrarCapacitacionUseCase} y {@link ValidarCapacitacionUseCase}
 * coordinando el dominio de formación continua con el puerto de repositorio,
 * controlando que solo RH o Dirección pueda validar certificaciones.
 *
 * @author ADES
 * @since 2026
 */
public class CapacitacionApplicationService
        implements RegistrarCapacitacionUseCase, ValidarCapacitacionUseCase {

    private final CapacitacionRepositoryPort repo;

    public CapacitacionApplicationService(CapacitacionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarCapacitacionUseCase.Command cmd) {
        CapacitacionDocente cd = new CapacitacionDocente();
        cd.setDocenteId(cmd.docenteId());
        cd.setNombre(cmd.nombre());
        cd.setTipoCertificacion(cmd.tipo().name());
        cd.setModalidad(cmd.modalidad().name());
        cd.setFechaInicio(cmd.fechaInicio());
        cd.setFechaFin(cmd.fechaFin());
        cd.setDuracionHrs(cmd.duracionHrs());
        cd.setInstitucion(cmd.institucion());
        cd.setFolioCertificado(cmd.folioCertificado());
        cd.setCertificadoUrl(cmd.certificadoUrl());
        if (cmd.area() != null) cd.setAreaFormacion(cmd.area().name());
        cd.setIsActive(true);
        if (cmd.usuarioCreacion() != null) {
            cd.setUsuarioCreacion(cmd.usuarioCreacion());
            cd.setUsuarioModificacion(cmd.usuarioCreacion());
        }
        return repo.save(cd).getId();
    }

    @Override
    public void validar(ValidarCapacitacionUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo RH o Dirección puede validar capacitaciones");
        }
        CapacitacionDocente cd = repo.findActiveById(cmd.capacitacionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Capacitación no encontrada"));
        cd.setValidadoRh(true);
        cd.setFechaValidacion(LocalDateTime.now());
        if (cmd.usuarioNombre() != null) cd.setUsuarioModificacion(cmd.usuarioNombre());
        repo.save(cd);
    }
}

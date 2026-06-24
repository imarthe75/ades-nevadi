package mx.ades.modules.condiciones.application.service;

import mx.ades.modules.condiciones.CondicionCronica;
import mx.ades.modules.condiciones.domain.port.in.ActualizarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.EliminarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.in.RegistrarCondicionUseCase;
import mx.ades.modules.condiciones.domain.port.out.CondicionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Caso de uso: gestión de condiciones crónicas de salud de los alumnos.
 * Implementa {@link RegistrarCondicionUseCase}, {@link ActualizarCondicionUseCase}
 * y {@link EliminarCondicionUseCase} coordinando el dominio de salud estudiantil
 * con el puerto de repositorio, controlando acceso por nivel para proteger
 * datos médicos sensibles (LFPDPPP).
 *
 * @author ADES
 * @since 2026
 */
public class CondicionCronicaApplicationService
        implements RegistrarCondicionUseCase, ActualizarCondicionUseCase, EliminarCondicionUseCase {

    private final CondicionRepositoryPort repo;

    public CondicionCronicaApplicationService(CondicionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public UUID registrar(RegistrarCondicionUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere COORDINADOR+)");
        }
        CondicionCronica cc = new CondicionCronica();
        cc.setAlumnoId(cmd.alumnoId());
        cc.setTipoCondicion(cmd.tipoCondicion().name());
        cc.setDescripcion(cmd.descripcion());
        cc.setMedicacionNombre(cmd.medicacionNombre());
        cc.setDosis(cmd.dosis());
        cc.setFrecuencia(cmd.frecuencia());
        cc.setAlergias(cmd.alergias());
        cc.setMedicoResponsable(cmd.medicoResponsable());
        cc.setTelefonoMedico(cmd.telefonoMedico());
        cc.setActiva(true);
        cc.setIsActive(true);
        return repo.save(cc).getId();
    }

    @Override
    public void actualizar(ActualizarCondicionUseCase.Command cmd) {
        if (cmd.nivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere COORDINADOR+)");
        }
        CondicionCronica cc = repo.findActiveById(cmd.condicionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Condición no encontrada"));

        if (cmd.descripcion() != null)       cc.setDescripcion(cmd.descripcion());
        if (cmd.medicacionNombre() != null)  cc.setMedicacionNombre(cmd.medicacionNombre());
        if (cmd.dosis() != null)             cc.setDosis(cmd.dosis());
        if (cmd.frecuencia() != null)        cc.setFrecuencia(cmd.frecuencia());
        if (cmd.alergias() != null)          cc.setAlergias(cmd.alergias());
        if (cmd.medicoResponsable() != null) cc.setMedicoResponsable(cmd.medicoResponsable());
        if (cmd.telefonoMedico() != null)    cc.setTelefonoMedico(cmd.telefonoMedico());
        if (cmd.activa() != null)            cc.setActiva(cmd.activa());

        repo.save(cc);
    }

    @Override
    public void eliminar(UUID condicionId, int nivelAcceso) {
        if (nivelAcceso > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos (requiere DIRECTOR+)");
        }
        CondicionCronica cc = repo.findActiveById(condicionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Condición no encontrada"));
        cc.setIsActive(false);
        repo.save(cc);
    }
}

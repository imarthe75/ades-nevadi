package mx.ades.modules.portal_familias.application.service;

import mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase;
import mx.ades.modules.portal_familias.domain.port.out.PortalFamiliasRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: gestión del portal de familias y vínculos tutor-alumno.
 * Implementa {@link AgregarTutorUseCase} coordinando el dominio de familias
 * con el puerto de repositorio, permitiendo vincular tutores a alumnos,
 * crear cuentas de acceso para padres de familia y configurar restricciones
 * de visualización de información académica.
 *
 * @author ADES
 * @since 2026
 */
public class PortalFamiliasApplicationService implements AgregarTutorUseCase {

    private final PortalFamiliasRepositoryPort repo;

    public PortalFamiliasApplicationService(PortalFamiliasRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public Map<String, Object> agregar(Command cmd) {
        if (!repo.existeAlumno(cmd.alumnoId())) {
            throw new IllegalStateException("Alumno no encontrado");
        }
        return repo.insertTutor(cmd);
    }

    @Transactional
    public void desvincular(UUID tutorAlumnoId, String usuarioMod) {
        repo.desvincularTutor(tutorAlumnoId, usuarioMod);
    }

    @Transactional
    public UUID crearUsuario(UUID tutorAlumnoId, String email, String nombreCompleto, String usuarioId) {
        if (!repo.existeTutorAlumno(tutorAlumnoId)) {
            throw new IllegalStateException("Vínculo tutor-alumno no encontrado");
        }
        return repo.enqueueCrearUsuario(tutorAlumnoId, email, nombreCompleto, usuarioId);
    }

    @Transactional
    public void establecerRestriccion(UUID tutorAlumnoId, Map<String, Object> restricciones, String usuarioId) {
        repo.upsertRestriccion(tutorAlumnoId, restricciones, usuarioId);
    }
}

package mx.ades.modules.portal_familias.application.service;

import mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase;
import mx.ades.modules.portal_familias.domain.port.out.PortalFamiliasRepositoryPort;

import java.util.Map;
import java.util.UUID;

public class PortalFamiliasApplicationService implements AgregarTutorUseCase {

    private final PortalFamiliasRepositoryPort repo;

    public PortalFamiliasApplicationService(PortalFamiliasRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Map<String, Object> agregar(Command cmd) {
        if (!repo.existeAlumno(cmd.alumnoId())) {
            throw new IllegalStateException("Alumno no encontrado");
        }
        return repo.insertTutor(cmd);
    }

    public void desvincular(UUID tutorAlumnoId, String usuarioMod) {
        repo.desvincularTutor(tutorAlumnoId, usuarioMod);
    }

    public UUID crearUsuario(UUID tutorAlumnoId, String email, String nombreCompleto, String usuarioId) {
        if (!repo.existeTutorAlumno(tutorAlumnoId)) {
            throw new IllegalStateException("Vínculo tutor-alumno no encontrado");
        }
        return repo.enqueueCrearUsuario(tutorAlumnoId, email, nombreCompleto, usuarioId);
    }

    public void establecerRestriccion(UUID tutorAlumnoId, Map<String, Object> restricciones, String usuarioId) {
        repo.upsertRestriccion(tutorAlumnoId, restricciones, usuarioId);
    }
}

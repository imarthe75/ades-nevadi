package mx.ades.modules.portal_familias.domain.port.out;

import mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase;

import java.util.Map;
import java.util.UUID;

public interface PortalFamiliasRepositoryPort {

    boolean existeAlumno(UUID alumnoId);

    Map<String, Object> insertTutor(AgregarTutorUseCase.Command cmd);

    void desvincularTutor(UUID tutorAlumnoId, String usuarioMod);

    boolean existeTutorAlumno(UUID tutorAlumnoId);

    UUID enqueueCrearUsuario(UUID tutorAlumnoId, String email, String nombreCompleto, String usuarioId);

    void upsertRestriccion(UUID tutorAlumnoId, Map<String, Object> restricciones, String usuarioId);
}

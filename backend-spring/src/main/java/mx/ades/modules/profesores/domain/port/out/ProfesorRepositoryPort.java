package mx.ades.modules.profesores.domain.port.out;

import mx.ades.modules.profesores.Profesor;

import java.util.Optional;
import java.util.UUID;

public interface ProfesorRepositoryPort {

    Profesor save(Profesor profesor);

    Optional<Profesor> findById(UUID id);
}

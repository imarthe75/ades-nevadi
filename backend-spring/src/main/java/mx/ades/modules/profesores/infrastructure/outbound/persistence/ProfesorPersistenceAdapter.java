package mx.ades.modules.profesores.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.profesores.Profesor;
import mx.ades.modules.profesores.ProfesorRepository;
import mx.ades.modules.profesores.domain.port.out.ProfesorRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProfesorPersistenceAdapter implements ProfesorRepositoryPort {

    private final ProfesorRepository jpa;

    @Override
    public Profesor save(Profesor profesor) {
        return jpa.save(profesor);
    }

    @Override
    public Optional<Profesor> findById(UUID id) {
        return jpa.findById(id);
    }
}

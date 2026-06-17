package mx.ades.modules.alumnos.domain.port.out;

import mx.ades.modules.alumnos.Estudiante;

import java.util.Optional;
import java.util.UUID;

public interface AlumnoRepositoryPort {

    Estudiante save(Estudiante estudiante);

    Optional<Estudiante> findById(UUID id);

    boolean existeByCurp(String curp);

    UUID resolverPrimerPlantelActivo();

    String generarSiguienteMatricula();
}

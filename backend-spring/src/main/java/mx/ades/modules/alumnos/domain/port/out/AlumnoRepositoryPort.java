package mx.ades.modules.alumnos.domain.port.out;

import mx.ades.modules.alumnos.Estudiante;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para la entidad {@code Estudiante},
 * incluyendo búsqueda, verificación de CURP única y generación de matrícula secuencial.
 *
 * @author ADES
 * @since 2026
 */
public interface AlumnoRepositoryPort {

    Estudiante save(Estudiante estudiante);

    Optional<Estudiante> findById(UUID id);

    boolean existeByCurp(String curp);

    UUID resolverPrimerPlantelActivo();

    String generarSiguienteMatricula();
}

package mx.ades.modules.materias.domain.port.out;

import mx.ades.modules.materias.Materia;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para la entidad Materia.
 *
 * @author ADES
 * @since 2026
 */
public interface MateriaRepositoryPort {

    Materia save(Materia materia);

    Optional<Materia> findById(UUID id);
}

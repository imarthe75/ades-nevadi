package mx.ades.modules.horarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, UUID> {
    List<Horario> findByGrupoId(UUID grupoId);

    List<Horario> findByCorridaIdAndIsActiveTrue(UUID corridaId);

    long countByCorridaId(UUID corridaId);
}

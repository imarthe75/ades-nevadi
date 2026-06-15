package mx.ades.modules.conducta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SancionDisciplinariaRepository extends JpaRepository<SancionDisciplinaria, UUID> {
    Optional<SancionDisciplinaria> findByReporteConductaIdAndIsActiveTrue(UUID reporteConductaId);
}
